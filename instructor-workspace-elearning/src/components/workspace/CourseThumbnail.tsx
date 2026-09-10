import { useEffect, useRef, useState } from "react";
import { ImageIcon, Upload } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
import { useSetCourseThumbnailMutation } from "@/hooks/queries";
import { parseApiError } from "@/api";
import { MEDIA_FRAME, MEDIA_STILL } from "@/lib/media-frame";
import { cn } from "@/lib/utils";

/**
 * The picture that stands for the course everywhere it is listed.
 *
 * Held in a fixed 16:9 frame rather than sized by whatever was uploaded, so
 * what an instructor sees here is what a student sees in the catalogue. The
 * crop is the point: choosing an image and only later discovering the grid cut
 * the title off it is worse than being shown the cut up front.
 */

/**
 * Small enough that the catalogue stays quick, large enough to survive a
 * retina display at card width. Not a server rule — the endpoint takes what it
 * is given — which is exactly why it is worth saying something here rather
 * than letting somebody push a 40MB camera original into a 320px tile.
 */
const MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024;

export function CourseThumbnail({
  courseId,
  thumbnailUrl,
}: {
  courseId: string;
  thumbnailUrl?: string | null | undefined;
}) {
  const setThumbnail = useSetCourseThumbnailMutation();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [percent, setPercent] = useState<number | null>(null);
  // What the new image looks like, shown from local disk while it uploads so
  // the frame does not sit on the old picture through the whole transfer.
  const [preview, setPreview] = useState<string | null>(null);

  useEffect(() => () => setPreview(null), []);

  const choose = async (file: File | null) => {
    if (!file) return;

    if (!file.type.startsWith("image/")) {
      toast.error("A thumbnail has to be an image.");
      return;
    }
    if (file.size > MAX_THUMBNAIL_BYTES) {
      toast.error(
        `That image is ${(file.size / (1024 * 1024)).toFixed(1)} MB. Thumbnails should be under 5 MB.`,
      );
      return;
    }

    const localUrl = URL.createObjectURL(file);
    setPreview(localUrl);
    setPercent(0);

    try {
      await setThumbnail.mutateAsync({ courseId, file, onProgress: setPercent });
      toast.success("Thumbnail updated.");
    } catch (err) {
      toast.error(parseApiError(err).message || "That image could not be set as the thumbnail.");
      setPreview(null);
    } finally {
      URL.revokeObjectURL(localUrl);
      setPercent(null);
      // Cleared so choosing the same file again still fires a change event.
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  const shown = preview ?? thumbnailUrl;
  const busy = percent !== null;

  return (
    <div className="space-y-2">
      <Label htmlFor="course-thumbnail">Thumbnail</Label>

      <div className={cn(MEDIA_FRAME, "grid place-items-center bg-muted/40")}>
        {shown ? (
          <img src={shown} alt="" className={MEDIA_STILL} />
        ) : (
          <div className="p-6 text-center">
            <ImageIcon className="mx-auto h-6 w-6 text-muted-foreground" />
            <p className="mt-2 text-xs text-muted-foreground">
              No thumbnail yet. Courses without one are easy to scroll past.
            </p>
          </div>
        )}
      </div>

      {busy && <Progress value={percent ?? 0} className="h-1" />}

      <div className="flex flex-wrap items-center gap-2">
        <input
          ref={inputRef}
          id="course-thumbnail"
          type="file"
          accept="image/*"
          className="sr-only"
          onChange={(e) => void choose(e.target.files?.[0] ?? null)}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={busy}
          onClick={() => inputRef.current?.click()}
        >
          <Upload className="h-3.5 w-3.5" />
          {busy ? "Uploading…" : shown ? "Replace it" : "Choose an image"}
        </Button>
        <p className="text-xs text-muted-foreground">
          Shown at 16:9. Anything else is cropped to fit.
        </p>
      </div>
    </div>
  );
}
