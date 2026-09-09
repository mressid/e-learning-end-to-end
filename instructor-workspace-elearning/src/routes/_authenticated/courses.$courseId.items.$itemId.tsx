import { useEffect, useState, type FormEvent } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft, Save } from "lucide-react";
import { toast } from "sonner";

import { useItemQuery, useLessonQuery, useSaveLessonMutation } from "@/hooks/queries";
import { mediaApi, parseApiError, type LessonContentType, type SaveLessonRequest } from "@/api";
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

const FORM_ID = "lesson-page-form";

const CONTENT_TYPES: LessonContentType[] = ["ARTICLE", "VIDEO", "DOCUMENT", "AUDIO", "EXTERNAL"];

/** The content types whose body is an uploaded file rather than typed text. */
const FILE_BACKED: LessonContentType[] = ["VIDEO", "DOCUMENT", "AUDIO"];

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
 */
export const Route = createFileRoute("/_authenticated/courses/$courseId/items/$itemId")({
  head: () => ({ meta: [{ title: "Lesson — Lernova for Instructors" }] }),
  component: LessonPage,
});

function LessonPage() {
  const { courseId, itemId } = Route.useParams();
  const navigate = useNavigate();

  const item = useItemQuery(courseId, itemId);
  const lesson = useLessonQuery(courseId, itemId);
  const save = useSaveLessonMutation(courseId);

  const [contentType, setContentType] = useState<LessonContentType>("ARTICLE");
  // The rich editor is not an <input>, so FormData cannot see it — the body is
  // the one field this form holds in state.
  const [body, setBody] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [stage, setStage] = useState("");
  const [error, setError] = useState("");

  // Seeded once the saved lesson arrives. Keyed on the item so navigating
  // between lessons in the sidebar does not carry the previous body across.
  useEffect(() => {
    const saved = lesson.data?.contentType as LessonContentType | undefined;
    if (saved) setContentType(saved);
    setBody(lesson.data?.content ?? "");
    setFile(null);
    setStage("");
    setError("");
  }, [itemId, lesson.data?.contentType, lesson.data?.content]);

  const existing = lesson.data;
  const needsFile = FILE_BACKED.includes(contentType);
  const hasFileAlready = Boolean(existing?.hasFile) && existing?.contentType === contentType;
  const busy = save.isPending || Boolean(stage);

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

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const form = new FormData(e.target as HTMLFormElement);
    const description = String(form.get("description") ?? "").trim();
    const content =
      contentType === "ARTICLE" ? body.trim() : String(form.get("content") ?? "").trim();
    const durationRaw = String(form.get("durationSeconds") ?? "").trim();

    if (needsFile && !file && !hasFileAlready) {
      setError(`A ${contentType.toLowerCase()} lesson needs a file.`);
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
        ...(description ? { description } : {}),
        ...(contentType === "ARTICLE" || contentType === "EXTERNAL" ? { content } : {}),
        ...(mediaId ? { mediaId } : {}),
        ...(durationRaw ? { durationSeconds: Number(durationRaw) } : {}),
      };

      await save.mutateAsync(payload);
      setStage("");
      setFile(null);
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
          <div className="flex items-center gap-2">
            {lesson.isError && (
              <span className="text-xs text-muted-foreground">Not written yet</span>
            )}
            <Button type="submit" form={FORM_ID} size="sm" disabled={busy}>
              <Save className="h-4 w-4" />
              {busy ? stage || "Saving…" : "Save lesson"}
            </Button>
          </div>
        </div>
      </header>

      <form id={FORM_ID} onSubmit={submit} className="flex-1 space-y-5 p-4 sm:p-6" key={itemId}>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>How this lesson is delivered</Label>
            <Select
              value={contentType}
              onValueChange={(v) => {
                setContentType(v as LessonContentType);
                setFile(null);
              }}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {CONTENT_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t.charAt(0) + t.slice(1).toLowerCase()}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              Changing this changes what the lesson holds, so it clears a file you had picked.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="l-duration">Duration in seconds</Label>
            <Input
              id="l-duration"
              name="durationSeconds"
              type="number"
              min={0}
              defaultValue={existing?.durationSeconds ?? ""}
            />
            <p className="text-xs text-muted-foreground">
              Optional. Shown to learners as how long this takes.
            </p>
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="l-description">Description</Label>
          <Textarea
            id="l-description"
            name="description"
            rows={2}
            defaultValue={existing?.description ?? ""}
          />
          <p className="text-xs text-muted-foreground">
            A line about the lesson, read before it is opened.
          </p>
        </div>

        {contentType === "ARTICLE" && (
          <div className="space-y-1.5">
            <Label>Body</Label>
            <MarkdownEditor
              value={body}
              onChange={setBody}
              seedKey={itemId}
              className="[&_.lernova-mdx-content]:min-h-[60vh]"
            />
            <p className="text-xs text-muted-foreground">
              Type as you would in a document. What is stored is markdown, so it stays readable
              outside this editor — the toolbar toggle switches between the rich view and the
              source.
            </p>
          </div>
        )}

        {contentType === "EXTERNAL" && (
          <div className="space-y-1.5">
            <Label htmlFor="l-content">External link or embed</Label>
            <Textarea
              id="l-content"
              name="content"
              rows={4}
              defaultValue={existing?.content ?? ""}
              placeholder="https://…"
            />
            <p className="text-xs text-muted-foreground">
              Somewhere else entirely — the platform stores the reference, not the content.
            </p>
          </div>
        )}

        {needsFile && (
          <div className="space-y-2">
            <Label htmlFor="l-file">
              {contentType.charAt(0) + contentType.slice(1).toLowerCase()} file
            </Label>
            <div className="rounded-xl border border-dashed p-8 text-center">
              <Input
                id="l-file"
                type="file"
                accept={
                  contentType === "VIDEO"
                    ? "video/*"
                    : contentType === "AUDIO"
                      ? "audio/*"
                      : undefined
                }
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="mx-auto max-w-sm"
              />
              {file ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  {file.name} · {(file.size / (1024 * 1024)).toFixed(1)} MB
                </p>
              ) : hasFileAlready ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  A file is already attached. Choose another only to replace it.
                </p>
              ) : (
                <p className="mt-3 text-xs text-muted-foreground">
                  Uploaded straight to storage — the bytes never pass through the API.
                </p>
              )}
              {contentType === "VIDEO" && (
                <p className="mt-1.5 text-xs text-muted-foreground">
                  Uploading queues an encode. The original plays until it finishes.
                </p>
              )}
            </div>
          </div>
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

      {/* Navigating away is the sidebar's job; this only exists so the page has
          somewhere obvious to go when it is opened directly. */}
      <div className="px-4 pb-6 sm:px-6">
        <Button
          variant="ghost"
          size="sm"
          onClick={() =>
            void navigate({
              to: "/courses/$courseId",
              params: { courseId },
              search: { tab: "curriculum", item: itemId },
            })
          }
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to the curriculum
        </Button>
      </div>
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
