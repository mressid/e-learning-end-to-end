import { useCallback, useEffect, useRef, useState } from "react";
import { AlertCircle, Loader2, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { curriculumApi } from "@/api/endpoints/curriculum.api";
import { parseApiError } from "@/api/client";
import { cn } from "@/lib/utils";
import { MEDIA_FRAME } from "@/lib/media-frame";

/**
 * Watching the video attached to a lesson, without leaving the editor.
 *
 * Two sources, in order of preference. The HLS rendition is what a student
 * gets, so it is what an instructor should be checking; the original upload is
 * the fallback, and it is the *only* thing available in the minutes or hours
 * between saving a video and the encode finishing. The backend is explicit
 * that this is the intended behaviour rather than a workaround: the manifest
 * answers 422 while a job is running and says the original still downloads.
 *
 * Nothing here is fetched until the preview is actually opened. This page was
 * three seconds slow for exactly this kind of reason once already, and a video
 * player that loads itself on every lesson — including the written ones that
 * have no video at all — would put it back.
 */

/** How the encode looked the last time we asked. */
type Source =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "hls"; manifestUrl: string }
  | { kind: "original"; url: string; encoding: boolean }
  | { kind: "error"; message: string };

/**
 * Backing off rather than asking every few seconds. An encode is minutes of
 * ffmpeg, not milliseconds, so a tight poll would be a few hundred pointless
 * requests for one answer that was always going to take a while.
 */
const POLL_DELAYS_MS = [5_000, 10_000, 20_000, 30_000, 60_000];

export function VideoPreview({
  itemId,
  localFile,
  className,
}: {
  itemId: string;
  /** A file chosen in the picker but not yet saved. Takes precedence over anything on the server. */
  localFile?: File | null;
  className?: string;
}) {
  const [source, setSource] = useState<Source>({ kind: "idle" });
  const videoRef = useRef<HTMLVideoElement | null>(null);

  // Held so cleanup can reach them: the blob URL leaks the manifest until it is
  // revoked, and an undestroyed Hls instance keeps fetching segments for a
  // video nobody is looking at any more.
  const blobUrlRef = useRef<string | null>(null);
  const hlsRef = useRef<{ destroy: () => void } | null>(null);
  const pollRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const attemptRef = useRef(0);
  // Set on unmount so an in-flight request cannot call setState afterwards.
  const goneRef = useRef(false);

  const releasePlayer = useCallback(() => {
    hlsRef.current?.destroy();
    hlsRef.current = null;
    if (blobUrlRef.current) {
      URL.revokeObjectURL(blobUrlRef.current);
      blobUrlRef.current = null;
    }
  }, []);

  /** The original upload, which plays whether or not anything has encoded it. */
  const playOriginal = useCallback(
    async (encoding: boolean) => {
      try {
        const url = await curriculumApi.contentUrl(itemId);
        if (goneRef.current) return;
        setSource({ kind: "original", url, encoding });
      } catch (err) {
        if (goneRef.current) return;
        setSource({
          kind: "error",
          message: parseApiError(err).message || "That video could not be loaded.",
        });
      }
    },
    [itemId],
  );

  const load = useCallback(async () => {
    releasePlayer();
    setSource({ kind: "loading" });

    let manifest: string;
    try {
      manifest = await curriculumApi.streamManifest(itemId);
    } catch (err) {
      if (goneRef.current) return;
      const error = parseApiError(err);
      // Still encoding is a state to show, not a failure to report. Anything
      // else — no video, no permission — is a real error and says so.
      if (error.code === "STREAM_NOT_READY") {
        void playOriginal(true);
        const delay = POLL_DELAYS_MS[Math.min(attemptRef.current, POLL_DELAYS_MS.length - 1)];
        attemptRef.current += 1;
        pollRef.current = setTimeout(() => void load(), delay);
        return;
      }
      if (error.code === "LESSON_HAS_NO_VIDEO") {
        setSource({ kind: "error", message: "This lesson has no video attached yet." });
        return;
      }
      void playOriginal(false);
      return;
    }

    if (goneRef.current) return;
    attemptRef.current = 0;

    // A blob URL because the manifest arrives as text, not as a link. The
    // endpoint needs our bearer token and a <video> element has nowhere to put
    // one; the segment references inside are already absolute and signed.
    const blobUrl = URL.createObjectURL(
      new Blob([manifest], { type: "application/vnd.apple.mpegurl" }),
    );
    blobUrlRef.current = blobUrl;
    setSource({ kind: "hls", manifestUrl: blobUrl });
  }, [itemId, releasePlayer, playOriginal]);

  // Attaches hls.js once the <video> for it is on screen. Imported here rather
  // than at the top of the file so the parser cost lands on instructors who
  // open a preview, not on everyone who opens a lesson.
  useEffect(() => {
    if (source.kind !== "hls") return;
    const video = videoRef.current;
    if (!video) return;

    let cancelled = false;
    void (async () => {
      const { default: Hls } = await import("hls.js");
      if (cancelled || goneRef.current) return;

      if (!Hls.isSupported()) {
        // iOS Safari has no Media Source Extensions and plays HLS natively
        // instead. It will not accept a manifest whose type it cannot sniff
        // from a blob, so the original upload is the honest answer there.
        void playOriginal(false);
        return;
      }

      const hls = new Hls({ enableWorker: true });
      hlsRef.current = hls;
      hls.loadSource(source.manifestUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal || cancelled || goneRef.current) return;
        // A fatal error here means the rendition is unplayable even though the
        // manifest itself arrived — most likely a segment the browser was
        // refused. The original file is still a true preview of the lesson, so
        // fall back to it rather than showing the instructor a dead player.
        hls.destroy();
        hlsRef.current = null;
        void playOriginal(false);
      });
    })();

    return () => {
      cancelled = true;
    };
  }, [source, playOriginal]);

  useEffect(() => {
    goneRef.current = false;
    return () => {
      goneRef.current = true;
      if (pollRef.current) clearTimeout(pollRef.current);
      releasePlayer();
    };
  }, [releasePlayer]);

  // A file sitting in the picker plays from the disk it is already on, with
  // none of the machinery below: there is nothing to stream, nothing to
  // encode, and nothing to ask the server about. Checking you picked the
  // right video *before* spending ten minutes uploading it is the whole
  // point, so this needs no button and does not wait to be asked.
  if (localFile) return <LocalFilePreview file={localFile} className={className} />;

  if (source.kind === "idle") {
    return (
      <div className={cn("rounded-xl border bg-muted/30 p-4 text-center", className)}>
        <p className="text-xs text-muted-foreground">
          Check the video the way a student will see it.
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-2"
          onClick={() => void load()}
        >
          Preview the video
        </Button>
      </div>
    );
  }

  if (source.kind === "loading") {
    return (
      <div
        className={cn(
          "flex items-center justify-center gap-2 rounded-xl border bg-muted/30 p-8",
          className,
        )}
      >
        <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
        <span className="text-xs text-muted-foreground">Loading the video…</span>
      </div>
    );
  }

  if (source.kind === "error") {
    return (
      <div className={cn("rounded-xl border border-dashed p-4", className)}>
        <div className="flex items-start gap-2">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
          <div className="min-w-0">
            <p className="text-xs">{source.message}</p>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="mt-1 h-7 px-2"
              onClick={() => void load()}
            >
              <RefreshCw className="h-3 w-3" />
              Try again
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={cn("space-y-2", className)}>
      <video
        ref={videoRef}
        controls
        preload="metadata"
        className={cn(MEDIA_FRAME, "block")}
        {...(source.kind === "original" ? { src: source.url } : {})}
      />
      {source.kind === "original" && source.encoding && (
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Loader2 className="h-3 w-3 animate-spin" />
          Still encoding. This is the file you uploaded; students get the streaming version once it
          finishes.
        </p>
      )}
      {source.kind === "hls" && (
        <p className="text-xs text-muted-foreground">
          Playing the streaming version, the same one students get.
        </p>
      )}
    </div>
  );
}

/**
 * The video still on the instructor's own disk.
 *
 * The object URL is created in an effect and revoked when the file changes or
 * the component goes away — a URL made during render would leak one per
 * render, and revoking it too early leaves the player pointing at nothing.
 */
function LocalFilePreview({ file, className }: { file: File; className?: string | undefined }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    const objectUrl = URL.createObjectURL(file);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);

  if (!url) return null;

  return (
    <div className={cn("space-y-2", className)}>
      <video src={url} controls preload="metadata" className={cn(MEDIA_FRAME, "block")} />
      <p className="text-xs text-muted-foreground">
        The file you just chose, played from your own machine. Save the lesson to upload it.
      </p>
    </div>
  );
}
