import { useRef, useState, type FormEvent } from "react";
import { createFileRoute, Link, useBlocker } from "@tanstack/react-router";
import { ArrowLeft, Download, FileUp, Save, Upload } from "lucide-react";
import { toast } from "sonner";

import { useItemQuery, useLessonQuery, useSaveLessonMutation } from "@/hooks/queries";
import {
  curriculumApi,
  mediaApi,
  parseApiError,
  type LessonCompletionRule,
  type LessonContentFormat,
  type LessonResponse,
  type ResourceType,
  type SaveLessonRequest,
  type SourceType,
} from "@/api";
import { MarkdownEditor } from "@/components/workspace/MarkdownEditor";
import { ResourcePanel } from "@/components/workspace/ResourcePanel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { cn } from "@/lib/utils";

const FORM_ID = "lesson-page-form";

/**
 * What the material is, and where it lives — two questions, two controls.
 *
 * They used to be one list, which is why it had entries that could not be
 * saved: "written" is a kind of thing and "external" is a place a thing lives,
 * and neither answer told the platform the other half. Audio lessons and links
 * work now because the question they needed answered is finally being asked.
 */
const RESOURCE_TYPES: ResourceType[] = [
  "DOCUMENT",
  "VIDEO",
  "AUDIO",
  "IMAGE",
  "SOURCE_CODE",
  "LINK",
  "OTHER",
];

const KIND: Record<string, string> = {
  DOCUMENT: "Document",
  VIDEO: "Video",
  AUDIO: "Audio",
  IMAGE: "Image",
  SOURCE_CODE: "Source code",
  LINK: "Link",
  OTHER: "Something else",
};

const SOURCE_TYPES: SourceType[] = ["INLINE", "FILE", "URL"];

const WHERE: Record<string, { label: string; hint: string }> = {
  INLINE: { label: "Written here", hint: "Typed below and stored as text." },
  FILE: {
    label: "A file you upload",
    hint: "Uploaded straight to storage; the bytes never pass through the API.",
  },
  URL: {
    label: "A link",
    hint: "Somewhere else entirely. The platform stores the reference, not the content.",
  },
};

const FORMATS: LessonContentFormat[] = ["MARKDOWN", "HTML", "PLAIN_TEXT"];

const FORMAT_LABEL: Record<string, string> = {
  MARKDOWN: "Markdown",
  HTML: "HTML",
  PLAIN_TEXT: "Plain text",
};

/** Narrows the file picker where the kind makes it obvious. */
function acceptFor(resourceType: ResourceType): string | undefined {
  if (resourceType === "VIDEO") return "video/*";
  if (resourceType === "AUDIO") return "audio/*";
  if (resourceType === "IMAGE") return "image/*";
  return undefined;
}

const COMPLETION_RULES: LessonCompletionRule[] = ["MANUAL", "VIEW", "DURATION", "PERCENTAGE"];

const COMPLETION: Record<string, { label: string; hint: string }> = {
  MANUAL: { label: "The student marks it done", hint: "They decide when they have finished." },
  VIEW: { label: "Opening it is enough", hint: "Counted as done as soon as it is opened." },
  DURATION: {
    label: "After its stated duration",
    hint: "Counted once they have spent the duration above on it.",
  },
  PERCENTAGE: {
    label: "After a share of it",
    hint: "Counted once they are a set way through — for video, mostly.",
  },
};

const WORDS_PER_MINUTE = 200;

/**
 * What a lesson body may be loaded from, and how much of it.
 *
 * The extensions are a hint to the file picker, not a rule — the browser lets
 * anyone pick anything, so the handler checks what it actually read. Two
 * megabytes of prose is about a novel; past that it is not a lesson.
 */
const MARKDOWN_ACCEPT = ".md,.markdown,.mdx,.txt,text/markdown,text/plain";
const MAX_MARKDOWN_BYTES = 2 * 1024 * 1024;

/**
 * Writing one lesson, with the whole page to do it in.
 *
 * This was a sheet over the curriculum, which meant the editor competed for
 * width with a list nobody was reading at the time. Writing a lesson is not a
 * quick edit you do beside something else, so it gets its own URL — which also
 * makes it linkable, refreshable, and reachable from the sidebar tree.
 *
 * The item lives under its course in the path rather than at `/items/{id}`, so
 * the sidebar stays in course context and "back" has somewhere to go.
 *
 * The underscore in the filename is what keeps it a page. Without it the
 * router makes this a child of `courses.$courseId`, which renders no
 * `<Outlet />` — so opening a lesson drew the course page instead, landing on
 * the overview. The URL is unchanged; only the nesting is.
 */
export const Route = createFileRoute("/_authenticated/courses/$courseId_/items/$itemId")({
  head: () => ({ meta: [{ title: "Lesson — Lernova for Instructors" }] }),
  component: LessonPage,
});

function LessonPage() {
  const { courseId, itemId } = Route.useParams();

  const item = useItemQuery(courseId, itemId);
  const lesson = useLessonQuery(courseId, itemId);

  /*
   * Both queries settle before the editor exists.
   *
   * The editor reads its text once, when it mounts. Rendering it while the
   * lesson was still arriving mounted it on an empty body, and the text that
   * turned up a moment later went into React state that the editor was no
   * longer listening to — so a lesson with content came up blank, and moving
   * between two lessons showed the previous one's writing.
   */
  if (item.isLoading || lesson.isLoading) {
    return (
      <div className="space-y-4 p-4 sm:p-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-[60vh] w-full rounded-xl" />
      </div>
    );
  }

  if (item.isError || !item.data) {
    return (
      <div className="p-4 sm:p-6">
        <div className="card-surface p-8 text-center">
          <h2 className="text-lg font-bold tracking-tight">Item not found</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            {parseApiError(item.error).message ||
              "It may have been deleted, or it may not be yours."}
          </p>
          <Button variant="outline" size="sm" className="mt-3" asChild>
            <Link to="/courses/$courseId" params={{ courseId }} search={{ tab: "curriculum" }}>
              Back to the curriculum
            </Link>
          </Button>
        </div>
      </div>
    );
  }

  // Only lessons have a body. A quiz or an assignment has its own editor,
  // which does not exist yet — say so rather than showing a lesson form that
  // would save the wrong thing.
  if (item.data.type !== "LESSON") {
    return (
      <div className="space-y-6 p-4 sm:p-6">
        <BackLink courseId={courseId} />
        <div className="card-surface p-8 text-center">
          <h2 className="text-lg font-bold tracking-tight">{item.data.title}</h2>
          <Badge variant="outline" className="mt-2 text-[10px] uppercase tracking-wider">
            {item.data.type}
          </Badge>
          <p className="mx-auto mt-3 max-w-md text-sm text-muted-foreground">
            {item.data.type === "QUIZ"
              ? "Quiz authoring is not built yet — questions and options have endpoints, but no screen."
              : "Assignment authoring is not built yet — the endpoints exist, but no screen does."}
          </p>
        </div>
      </div>
    );
  }

  return (
    <LessonEditor
      // A different lesson is a different editor, not the same one told to
      // forget: every field below is seeded from the lesson it was mounted
      // with, and a key is the honest way to say so.
      key={itemId}
      courseId={courseId}
      itemId={itemId}
      title={item.data.title ?? "Lesson"}
      lesson={lesson.data ?? null}
    />
  );
}

/**
 * The lesson itself, mounted once the lesson it edits is known.
 *
 * `lesson` is null for an item nobody has written yet, which is the ordinary
 * state of a course being built rather than a failure. It stays live after
 * that: it is what the page compares against to know whether anything here is
 * unsaved.
 */
function LessonEditor({
  courseId,
  itemId,
  title,
  lesson,
}: {
  courseId: string;
  itemId: string;
  title: string;
  lesson: LessonResponse | null;
}) {
  const save = useSaveLessonMutation(courseId);

  const [resourceType, setResourceType] = useState<ResourceType>(
    (lesson?.resourceType as ResourceType | undefined) ?? "DOCUMENT",
  );
  const [sourceType, setSourceType] = useState<SourceType>(
    (lesson?.sourceType as SourceType | undefined) ?? "INLINE",
  );
  const [contentFormat, setContentFormat] = useState<LessonContentFormat>(
    (lesson?.contentFormat as LessonContentFormat | undefined) ?? "MARKDOWN",
  );
  const [url, setUrl] = useState(lesson?.url ?? "");
  const [description, setDescription] = useState(lesson?.description ?? "");
  // The rich editor is not an <input>, so the body is held here rather than
  // read off the form. Everything else is here too, because knowing whether
  // there is anything unsaved means knowing every current value.
  const [body, setBody] = useState(lesson?.content ?? "");
  const [minutes, setMinutes] = useState(
    lesson?.durationSeconds ? String(Math.floor(lesson.durationSeconds / 60)) : "",
  );
  const [seconds, setSeconds] = useState(
    lesson?.durationSeconds ? String(lesson.durationSeconds % 60) : "",
  );
  const [completionRule, setCompletionRule] = useState<LessonCompletionRule>(
    (lesson?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
  );
  const [file, setFile] = useState<File | null>(null);
  const [stage, setStage] = useState("");
  const [error, setError] = useState("");

  // Bumped to re-seed the editor from `body` — it reads its text at mount and
  // never again, so replacing what is in it means giving it a new identity.
  const [bodySeed, setBodySeed] = useState(0);
  const markdownInput = useRef<HTMLInputElement | null>(null);

  const saved = {
    resourceType: (lesson?.resourceType as ResourceType | undefined) ?? "DOCUMENT",
    sourceType: (lesson?.sourceType as SourceType | undefined) ?? "INLINE",
    contentFormat: (lesson?.contentFormat as LessonContentFormat | undefined) ?? "MARKDOWN",
    url: lesson?.url ?? "",
    description: lesson?.description ?? "",
    content: lesson?.content ?? "",
    durationSeconds: lesson?.durationSeconds ?? null,
    completionRule: (lesson?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
  };

  const hasFileAlready = Boolean(lesson?.hasFile) && lesson?.sourceType === sourceType;
  const busy = save.isPending || Boolean(stage);

  const durationSeconds = (() => {
    const total = Math.round(Number(minutes || 0) * 60 + Number(seconds || 0));
    return Number.isFinite(total) && total > 0 ? total : null;
  })();

  const isDirty =
    resourceType !== saved.resourceType ||
    sourceType !== saved.sourceType ||
    (sourceType === "INLINE" &&
      (body !== saved.content || contentFormat !== saved.contentFormat)) ||
    (sourceType === "URL" && url !== saved.url) ||
    description !== saved.description ||
    durationSeconds !== saved.durationSeconds ||
    completionRule !== saved.completionRule ||
    file !== null;

  /**
   * Leaving with unsaved work asks first.
   *
   * The sidebar is one click from every other lesson in the course, so the way
   * to lose an afternoon's writing is an ordinary navigation rather than
   * anything careless. `enableBeforeUnload` covers closing the tab, which the
   * router cannot intercept.
   */
  const blocker = useBlocker({
    shouldBlockFn: () => isDirty,
    enableBeforeUnload: () => isDirty,
    withResolver: true,
  });

  /**
   * Loading a body from a markdown file on disk.
   *
   * For prose written somewhere else, which is most of it — an existing README,
   * notes kept in a repository, a draft from another editor. What is stored is
   * markdown either way, so the file goes in as it is and the editor renders
   * it; nothing is converted and nothing is lost.
   */
  const loadMarkdownFile = async (chosen: File | null) => {
    if (!chosen) return;

    if (chosen.size > MAX_MARKDOWN_BYTES) {
      toast.error("That file is bigger than 2 MB. It is probably not a lesson.");
      return;
    }
    if (body.trim() && !window.confirm(`Replace what is written here with ${chosen.name}?`)) {
      return;
    }

    try {
      const text = await chosen.text();
      // The picker's accept list is a suggestion the browser does not enforce,
      // so this is where a PDF gets turned away rather than pasted in as
      // mojibake.
      if (text.includes("\u0000")) {
        toast.error("That does not look like a text file.");
        return;
      }
      setBody(text);
      setBodySeed((seed) => seed + 1);
      setError("");
      toast.success(`Loaded ${chosen.name}. Nothing is saved until you save the lesson.`);
    } catch {
      toast.error("That file could not be read.");
    }
  };

  const words = body.trim() ? body.trim().split(/\s+/).length : 0;
  const readingMinutes = Math.max(1, Math.round(words / WORDS_PER_MINUTE));

  const openCurrentFile = async () => {
    try {
      const signed = await curriculumApi.contentUrl(itemId);
      window.open(signed, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(parseApiError(err).message || "Could not open that file.");
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (sourceType === "INLINE" && !body.trim()) {
      setError("A written lesson needs a body. The server refuses an empty one.");
      return;
    }
    if (sourceType === "URL" && !/^https?:\/\//i.test(url.trim())) {
      setError("A link lesson needs an http or https address.");
      return;
    }
    if (sourceType === "FILE" && !file && !hasFileAlready) {
      setError("Choose a file to upload.");
      return;
    }

    try {
      let mediaId: string | undefined;
      if (file) {
        setStage("Uploading…");
        const media = await mediaApi.upload(file, { onProgress: setStage });
        mediaId = media.id ?? undefined;
      }

      const payload: SaveLessonRequest & { itemId: string } = {
        itemId,
        // The material is named after the item it teaches. It is a library
        // resource like any other and could be named separately, but a second
        // name for the same thing is a second thing to keep in step.
        title,
        resourceType,
        sourceType,
        completionRule,
        description: description.trim() || null,
        durationSeconds,
        // The whole lesson is replaced on every save, so a field left out is a
        // field cleared. The file is the exception: omitting it keeps the one
        // already attached, which is the only way to edit a video lesson at all
        // — its media id is never given back to us to re-send.
        ...(sourceType === "INLINE" ? { content: body.trim(), contentFormat } : {}),
        ...(sourceType === "URL" ? { url: url.trim() } : {}),
        ...(mediaId ? { mediaId } : {}),
      };

      const result = await save.mutateAsync(payload);
      setStage("");
      setFile(null);
      // Take the server's version of what was stored, so anything it trimmed or
      // defaulted does not leave the page looking unsaved.
      setDescription(result.description ?? "");
      setBody(result.content ?? "");
      setUrl(result.url ?? "");
      setCompletionRule((result.completionRule as LessonCompletionRule | undefined) ?? "MANUAL");
      toast.success("Lesson saved.");
    } catch (err) {
      setStage("");
      setError(parseApiError(err).message || "Could not save that lesson.");
    }
  };

  return (
    <div className="flex min-h-full flex-col">
      {/* Sticky, because the editor is taller than the viewport and Save should
          not be something you scroll back up to find. */}
      <header className="sticky top-0 z-20 border-b bg-background/85 px-4 py-3 backdrop-blur sm:px-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <BackLink courseId={courseId} />
            <h1 className="mt-1 truncate text-lg font-bold tracking-tight">{title}</h1>
          </div>
          <div className="flex items-center gap-3">
            <span
              className={cn(
                "text-xs",
                isDirty
                  ? "font-medium text-amber-600 dark:text-amber-500"
                  : "text-muted-foreground",
              )}
            >
              {isDirty ? "Unsaved changes" : lesson ? "Everything saved" : "Not written yet"}
            </span>
            <Button type="submit" form={FORM_ID} size="sm" disabled={busy || !isDirty}>
              <Save className="h-4 w-4" />
              {busy ? stage || "Saving…" : "Save lesson"}
            </Button>
          </div>
        </div>
      </header>

      <form id={FORM_ID} onSubmit={submit} className="flex-1 space-y-5 p-4 sm:p-6">
        <section className="card-surface space-y-4 p-4 sm:p-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>What it is</Label>
              <Select
                value={resourceType}
                onValueChange={(value) => setResourceType(value as ResourceType)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RESOURCE_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {KIND[type]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                How it is listed and what a student expects to open.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label>Where it lives</Label>
              <Select
                value={sourceType}
                onValueChange={(value) => {
                  setSourceType(value as SourceType);
                  setFile(null);
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SOURCE_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {WHERE[type]?.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{WHERE[sourceType]?.hint}</p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="l-minutes">How long it takes</Label>
              <div className="flex items-center gap-1.5">
                <Input
                  id="l-minutes"
                  type="number"
                  min={0}
                  value={minutes}
                  onChange={(e) => setMinutes(e.target.value)}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-xs text-muted-foreground">min</span>
                <Input
                  id="l-seconds"
                  type="number"
                  min={0}
                  max={59}
                  value={seconds}
                  onChange={(e) => setSeconds(e.target.value)}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-xs text-muted-foreground">sec</span>
              </div>
              <p className="text-xs text-muted-foreground">
                Optional. Shown to students before they start.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label>Counts as done when</Label>
              <Select
                value={completionRule}
                onValueChange={(value) => setCompletionRule(value as LessonCompletionRule)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {COMPLETION_RULES.map((rule) => (
                    <SelectItem key={rule} value={rule}>
                      {COMPLETION[rule]?.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {/* Honest about where this stands: the field is stored and read
                  back, but nothing in the platform acts on it yet. */}
              <p className="text-xs text-muted-foreground">
                {COMPLETION[completionRule]?.hint} Recorded on the lesson, though nothing enforces
                it yet.
              </p>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="l-description">Description</Label>
            <Textarea
              id="l-description"
              name="description"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              A line about the lesson, read before it is opened.
            </p>
          </div>
        </section>

        {sourceType === "INLINE" && (
          <section className="space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Label>Body</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-6 px-2 text-xs"
                  onClick={() => markdownInput.current?.click()}
                >
                  <Upload className="h-3 w-3" />
                  Load from a file
                </Button>
                <input
                  ref={markdownInput}
                  type="file"
                  accept={MARKDOWN_ACCEPT}
                  className="hidden"
                  onChange={(e) => {
                    const chosen = e.target.files?.[0] ?? null;
                    // Cleared so that picking the same file twice in a row
                    // still counts as a change.
                    e.target.value = "";
                    void loadMarkdownFile(chosen);
                  }}
                />
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span>
                  {words.toLocaleString()} {words === 1 ? "word" : "words"}
                </span>
                {words > 0 && (
                  <>
                    <span aria-hidden>·</span>
                    <span>about {readingMinutes} min to read</span>
                    {durationSeconds !== readingMinutes * 60 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-6 px-2 text-xs"
                        onClick={() => {
                          setMinutes(String(readingMinutes));
                          setSeconds("");
                        }}
                      >
                        Use as the duration
                      </Button>
                    )}
                  </>
                )}
              </div>
            </div>
            {contentFormat === "MARKDOWN" ? (
              <MarkdownEditor
                value={body}
                onChange={setBody}
                seedKey={`${itemId}:${bodySeed}`}
                className="[&_.lernova-mdx-content]:min-h-[65vh]"
              />
            ) : (
              // The rich editor speaks markdown and only markdown. Handing it
              // HTML would quietly rewrite somebody's markup, so the other two
              // formats get a plain box and are stored exactly as typed.
              <Textarea
                value={body}
                onChange={(e) => setBody(e.target.value)}
                className="min-h-[65vh] font-mono text-xs"
                spellCheck={contentFormat === "PLAIN_TEXT"}
              />
            )}
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2">
                <Label htmlFor="l-format" className="text-xs text-muted-foreground">
                  Stored as
                </Label>
                <Select
                  value={contentFormat}
                  onValueChange={(value) => setContentFormat(value as LessonContentFormat)}
                >
                  <SelectTrigger id="l-format" className="h-7 w-36 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {FORMATS.map((format) => (
                      <SelectItem key={format} value={format}>
                        {FORMAT_LABEL[format]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {/* The format is recorded with the text now. It used to be
                  markdown by the editor's habit and nothing else, which left a
                  renderer free to guess wrong. */}
              <p className="flex-1 text-xs text-muted-foreground">
                Written down with the text, so whatever displays this later does not have to guess.
              </p>
            </div>
          </section>
        )}

        {sourceType === "URL" && (
          <section className="space-y-1.5">
            <Label htmlFor="l-url">Where it is</Label>
            <Input
              id="l-url"
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://…"
            />
            <p className="text-xs text-muted-foreground">
              http and https only. Anything a browser would execute is refused.
            </p>
          </section>
        )}

        {sourceType === "FILE" && (
          <section className="space-y-2">
            <Label htmlFor="l-file">{KIND[resourceType]} file</Label>

            {hasFileAlready && (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-muted/30 p-3">
                <div className="min-w-0 text-xs">
                  <p className="font-medium">A file is already attached.</p>
                  <p className="mt-0.5 text-muted-foreground">
                    Everything else here can be changed without touching it. Choose another file
                    only to replace it.
                  </p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => void openCurrentFile()}
                >
                  <Download className="h-3.5 w-3.5" />
                  Open the current file
                </Button>
              </div>
            )}

            <div className="rounded-xl border border-dashed p-8 text-center">
              <FileUp className="mx-auto h-6 w-6 text-muted-foreground" />
              <Input
                id="l-file"
                type="file"
                accept={acceptFor(resourceType)}
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="mx-auto mt-3 max-w-sm"
              />
              {file ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  {file.name} · {(file.size / (1024 * 1024)).toFixed(1)} MB
                </p>
              ) : (
                <p className="mt-3 text-xs text-muted-foreground">
                  Uploaded straight to storage — the bytes never pass through the API.
                </p>
              )}
              {resourceType === "VIDEO" && (
                <p className="mt-1.5 text-xs text-muted-foreground">
                  A new file queues an encode, once per file rather than once per lesson. The
                  original plays until it finishes.
                </p>
              )}
            </div>
          </section>
        )}

        {stage && !error && (
          <p className="rounded-lg border bg-secondary/40 p-2.5 text-xs text-muted-foreground">
            {stage}
          </p>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}
      </form>

      {/* Outside the form: a nested <form> is invalid, and attaching a document
          is its own action rather than part of saving the lesson. */}
      <div className="border-t p-4 sm:p-6">
        <ResourcePanel
          scope="item"
          ownerId={itemId}
          emptyHint="Nothing attached to this lesson yet. Slides, a worksheet, a link to read first."
        />
      </div>

      <AlertDialog open={blocker.status === "blocked"}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Leave without saving?</AlertDialogTitle>
            <AlertDialogDescription>
              This lesson has changes that have not been saved. Leaving now loses them.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => blocker.reset?.()}>Stay here</AlertDialogCancel>
            <AlertDialogAction onClick={() => blocker.proceed?.()}>Leave anyway</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function BackLink({ courseId }: { courseId: string }) {
  return (
    <Link
      to="/courses/$courseId"
      params={{ courseId }}
      search={{ tab: "curriculum" }}
      className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground"
    >
      <ArrowLeft className="h-3.5 w-3.5" />
      Curriculum
    </Link>
  );
}
