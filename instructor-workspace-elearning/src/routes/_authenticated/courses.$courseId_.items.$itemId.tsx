import { useEffect, useRef, useState, type FormEvent } from "react";
import { createFileRoute, Link, useBlocker } from "@tanstack/react-router";
import { ArrowLeft, Download, FileUp, Save } from "lucide-react";
import { toast } from "sonner";

import { useItemQuery, useLessonQuery, useSaveLessonMutation } from "@/hooks/queries";
import {
  curriculumApi,
  mediaApi,
  parseApiError,
  type LessonCompletionRule,
  type LessonContentType,
  type SaveLessonRequest,
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
 * The three kinds of lesson the platform can actually store.
 *
 * `AUDIO` and `EXTERNAL` are in the API's enum but have no content table
 * behind them, so the server refuses them with CONTENT_TYPE_NOT_SUPPORTED.
 * Offering them here would be offering a choice that cannot be saved, so they
 * are named below the control instead of listed inside it.
 */
const CONTENT_TYPES: LessonContentType[] = ["ARTICLE", "VIDEO", "DOCUMENT"];

/** The kinds whose body is an uploaded file rather than typed text. */
const FILE_BACKED: LessonContentType[] = ["VIDEO", "DOCUMENT"];

const DELIVERY: Record<string, { label: string; hint: string }> = {
  ARTICLE: { label: "Written", hint: "Written here, in the editor below." },
  VIDEO: {
    label: "Video",
    hint: "A video you upload. It is encoded for streaming after it lands.",
  },
  DOCUMENT: { label: "Document", hint: "A file to read — a PDF, slides, a worksheet." },
};

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
  const save = useSaveLessonMutation(courseId);

  const [contentType, setContentType] = useState<LessonContentType>("ARTICLE");
  const [description, setDescription] = useState("");
  // The rich editor is not an <input>, so the body is held here rather than
  // read off the form. Everything else is here too, because knowing whether
  // there is anything unsaved means knowing every current value.
  const [body, setBody] = useState("");
  const [minutes, setMinutes] = useState("");
  const [seconds, setSeconds] = useState("");
  const [completionRule, setCompletionRule] = useState<LessonCompletionRule>("MANUAL");
  const [file, setFile] = useState<File | null>(null);
  const [stage, setStage] = useState("");
  const [error, setError] = useState("");

  const saved = {
    contentType: (lesson.data?.contentType as LessonContentType | undefined) ?? "ARTICLE",
    description: lesson.data?.description ?? "",
    content: lesson.data?.content ?? "",
    durationSeconds: lesson.data?.durationSeconds ?? null,
    completionRule: (lesson.data?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
  };

  /**
   * Seeded once per lesson, not on every change of the query's data.
   *
   * Re-seeding whenever the cached lesson changed meant a background refetch —
   * which React Query does on window focus — could overwrite half-written text
   * with the last saved version. It is seeded when the item changes, and after
   * that the page is the authority until it saves.
   */
  const seededFor = useRef<string | null>(null);
  useEffect(() => {
    if (lesson.isLoading) return;
    if (seededFor.current === itemId) return;
    seededFor.current = itemId;

    const data = lesson.data;
    setContentType((data?.contentType as LessonContentType | undefined) ?? "ARTICLE");
    setDescription(data?.description ?? "");
    setBody(data?.content ?? "");
    const duration = data?.durationSeconds ?? null;
    setMinutes(duration ? String(Math.floor(duration / 60)) : "");
    setSeconds(duration ? String(duration % 60) : "");
    setCompletionRule((data?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL");
    setFile(null);
    setStage("");
    setError("");
  }, [itemId, lesson.isLoading, lesson.data]);

  const existing = lesson.data;
  const needsFile = FILE_BACKED.includes(contentType);
  const hasFileAlready = Boolean(existing?.hasFile) && existing?.contentType === contentType;
  const busy = save.isPending || Boolean(stage);

  const durationSeconds = (() => {
    const total = Math.round(Number(minutes || 0) * 60 + Number(seconds || 0));
    return Number.isFinite(total) && total > 0 ? total : null;
  })();

  const isDirty =
    contentType !== saved.contentType ||
    description !== saved.description ||
    body !== saved.content ||
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

  if (item.isLoading) {
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

  const data = item.data;

  // Only lessons have a body. A quiz or an assignment has its own editor,
  // which does not exist yet — say so rather than showing a lesson form that
  // would save the wrong thing.
  if (data.type !== "LESSON") {
    return (
      <div className="space-y-6 p-4 sm:p-6">
        <BackLink courseId={courseId} />
        <div className="card-surface p-8 text-center">
          <h2 className="text-lg font-bold tracking-tight">{data.title}</h2>
          <Badge variant="outline" className="mt-2 text-[10px] uppercase tracking-wider">
            {data.type}
          </Badge>
          <p className="mx-auto mt-3 max-w-md text-sm text-muted-foreground">
            {data.type === "QUIZ"
              ? "Quiz authoring is not built yet — questions and options have endpoints, but no screen."
              : "Assignment authoring is not built yet — the endpoints exist, but no screen does."}
          </p>
        </div>
      </div>
    );
  }

  const words = body.trim() ? body.trim().split(/\s+/).length : 0;
  const readingMinutes = Math.max(1, Math.round(words / WORDS_PER_MINUTE));

  const openCurrentFile = async () => {
    try {
      const url = await curriculumApi.contentUrl(itemId);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(parseApiError(err).message || "Could not open that file.");
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (contentType === "ARTICLE" && !body.trim()) {
      setError("A written lesson needs a body. The server refuses an empty one.");
      return;
    }
    if (needsFile && !file && !hasFileAlready) {
      setError(`A ${DELIVERY[contentType]?.label.toLowerCase()} lesson needs a file.`);
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
        contentType,
        completionRule,
        description: description.trim() || null,
        durationSeconds,
        // The whole lesson is replaced on every save, so a field left out is a
        // field cleared. The file is the exception: omitting it keeps the one
        // already attached, which is the only way to edit a video lesson at all
        // — its media id is never given back to us to re-send.
        ...(contentType === "ARTICLE" ? { content: body.trim() } : {}),
        ...(mediaId ? { mediaId } : {}),
      };

      const result = await save.mutateAsync(payload);
      setStage("");
      setFile(null);
      // Take the server's version of what was stored, so anything it trimmed or
      // defaulted does not leave the page looking unsaved.
      setDescription(result.description ?? "");
      setBody(result.content ?? "");
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
            <h1 className="mt-1 truncate text-lg font-bold tracking-tight">{data.title}</h1>
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
              {isDirty
                ? "Unsaved changes"
                : lesson.isError
                  ? "Not written yet"
                  : "Everything saved"}
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
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label>Delivered as</Label>
              <Select
                value={contentType}
                onValueChange={(value) => {
                  setContentType(value as LessonContentType);
                  setFile(null);
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CONTENT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {DELIVERY[type]?.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{DELIVERY[contentType]?.hint}</p>
            </div>

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

        {contentType === "ARTICLE" && (
          <section className="space-y-2">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <Label>Body</Label>
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
            <MarkdownEditor
              value={body}
              onChange={setBody}
              seedKey={itemId}
              className="[&_.lernova-mdx-content]:min-h-[65vh]"
            />
            <p className="text-xs text-muted-foreground">
              Type as you would in a document. What is stored is markdown, so it stays readable
              outside this editor — the toolbar toggle switches between the rich view and the
              source.
            </p>
          </section>
        )}

        {needsFile && (
          <section className="space-y-2">
            <Label htmlFor="l-file">{DELIVERY[contentType]?.label} file</Label>

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
                accept={contentType === "VIDEO" ? "video/*" : undefined}
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
              {contentType === "VIDEO" && (
                <p className="mt-1.5 text-xs text-muted-foreground">
                  A new file queues an encode. The original plays until it finishes.
                </p>
              )}
            </div>
          </section>
        )}

        <p className="text-xs text-muted-foreground">
          Audio and external-link lessons are not stored yet — the API has the names but no table
          behind them, so they are left out of the choices above rather than failing on save.
        </p>

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
