import { useMemo, useState, type FormEvent } from "react";
import { createFileRoute, Link, useBlocker } from "@tanstack/react-router";
import { ArrowLeft, Save } from "lucide-react";
import { toast } from "sonner";

import { useItemQuery, useLessonQuery } from "@/hooks/queries";
import {
  curriculumApi,
  parseApiError,
  type LessonCompletionRule,
  type LessonResponse,
} from "@/api";
import { LessonBlocks } from "@/components/workspace/LessonBlocks";
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

const FORM_ID = "lesson-details-form";

const COMPLETION_RULES: LessonCompletionRule[] = ["MANUAL", "VIEW", "DURATION"];

const COMPLETION: Record<string, { label: string; hint: string }> = {
  MANUAL: { label: "The student marks it done", hint: "They decide when they have finished." },
  VIEW: { label: "Opening it is enough", hint: "Counted as done the moment it is opened." },
  DURATION: {
    label: "Reaching the end",
    hint: "Claiming to have finished before the duration above is recorded as still in progress.",
  },
};

/**
 * Writing one lesson, with the whole page to do it in.
 *
 * This page used to be a form for one material: a video, or a page of prose,
 * or a link, and never two of those at once. Saying "watch this, then read the
 * notes" therefore took two course items, which split one lesson's progress
 * across two rows of the curriculum for a reason no student would recognise.
 *
 * A lesson is now its own description plus an ordered list of blocks, and the
 * page is shaped to match: what belongs to the lesson at the top, what it is
 * made of below. The two save separately and deliberately — changing how long
 * a lesson takes should not mean re-sending a video, and editing a block
 * should not wait on a form.
 *
 * The item lives under its course in the path rather than at `/items/{id}`, so
 * the sidebar stays in course context and "back" has somewhere to go. The
 * underscore in the filename is what keeps it a page: without it the router
 * makes this a child of `courses.$courseId`, which renders no `<Outlet />`, so
 * opening a lesson drew the course page instead.
 */
export const Route = createFileRoute("/_authenticated/courses/$courseId_/items/$itemId")({
  head: () => ({ meta: [{ title: "Lesson — Lernova for Instructors" }] }),
  component: LessonPage,
});

function LessonPage() {
  const { courseId, itemId } = Route.useParams();

  const item = useItemQuery(courseId, itemId);
  const lesson = useLessonQuery(courseId, itemId);

  // Both queries settle before the editor exists, because every field below is
  // seeded from the lesson once and never told about a later arrival.
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

  // Only lessons have blocks. A quiz or an assignment has its own editor,
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
  const [description, setDescription] = useState(lesson?.description ?? "");
  const [minutes, setMinutes] = useState(
    lesson?.durationSeconds ? String(Math.floor(lesson.durationSeconds / 60)) : "",
  );
  const [seconds, setSeconds] = useState(
    lesson?.durationSeconds ? String(lesson.durationSeconds % 60) : "",
  );
  const [completionRule, setCompletionRule] = useState<LessonCompletionRule>(
    (lesson?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  // What the server last confirmed, so the dirty check compares against what
  // was stored rather than against what this component first rendered.
  const [saved, setSaved] = useState({
    description: lesson?.description ?? "",
    durationSeconds: lesson?.durationSeconds ?? null,
    completionRule: (lesson?.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
  });

  const durationSeconds = useMemo(() => {
    const total = (Number(minutes) || 0) * 60 + (Number(seconds) || 0);
    return total > 0 ? total : null;
  }, [minutes, seconds]);

  const isDirty =
    description !== saved.description ||
    durationSeconds !== saved.durationSeconds ||
    completionRule !== saved.completionRule;

  // Only these fields are guarded. A block saves itself the moment its own
  // button is pressed, so there is never an unsaved block to warn about — the
  // warning would be about a form the person may not have touched.
  const blocker = useBlocker({
    shouldBlockFn: () => isDirty,
    withResolver: true,
  });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setSaving(true);
    try {
      // Null rather than omitted, so clearing a description says so. The
      // endpoint reads a missing field as "leave it alone", which is what
      // makes it safe to describe a lesson without re-sending its content.
      const result = await curriculumApi.updateLessonDetails(itemId, {
        description: description.trim(),
        durationSeconds,
        completionRule,
      });
      setDescription(result.description ?? "");
      setCompletionRule((result.completionRule as LessonCompletionRule | undefined) ?? "MANUAL");
      setSaved({
        description: result.description ?? "",
        durationSeconds: result.durationSeconds ?? null,
        completionRule: (result.completionRule as LessonCompletionRule | undefined) ?? "MANUAL",
      });
      toast.success("Lesson saved.");
    } catch (err) {
      setError(parseApiError(err).message || "Could not save that lesson.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex min-h-full flex-col">
      {/* Sticky, because the page is taller than the viewport and Save should
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
              {isDirty ? "Unsaved changes" : "Everything saved"}
            </span>
            <Button type="submit" form={FORM_ID} size="sm" disabled={saving || !isDirty}>
              <Save className="h-4 w-4" />
              {saving ? "Saving…" : "Save details"}
            </Button>
          </div>
        </div>
      </header>

      <div className="flex-1 space-y-5 p-4 sm:p-6">
        <form id={FORM_ID} onSubmit={submit} className="card-surface space-y-4 p-4 sm:p-5">
          <div>
            <h2 className="text-sm font-semibold">About this lesson</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">
              What the lesson is and when it counts as finished. The lesson itself is below.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="l-description">Description</Label>
            <Textarea
              id="l-description"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              A line about the lesson, read before it is opened.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="l-minutes">How long it takes</Label>
              <div className="flex items-center gap-2">
                <Input
                  id="l-minutes"
                  type="number"
                  min={0}
                  className="max-w-20"
                  value={minutes}
                  onChange={(e) => setMinutes(e.target.value)}
                />
                <span className="text-sm text-muted-foreground">min</span>
                <Input
                  type="number"
                  min={0}
                  max={59}
                  className="max-w-20"
                  value={seconds}
                  onChange={(e) => setSeconds(e.target.value)}
                />
                <span className="text-sm text-muted-foreground">sec</span>
              </div>
              {/* Said plainly because a lesson can now hold several things and
                  the honest answer to "how long is it" would otherwise be
                  ambiguous. A video's runtime is filled in for you when you
                  choose the file. */}
              <p className="text-xs text-muted-foreground">
                For a lesson built around a video, its runtime.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label>When it counts as done</Label>
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
              <p className="text-xs text-muted-foreground">{COMPLETION[completionRule]?.hint}</p>
              {completionRule === "DURATION" && !durationSeconds && (
                <p className="text-xs text-amber-600 dark:text-amber-500">
                  This rule needs a duration above to measure against.
                </p>
              )}
            </div>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </form>

        {/* Outside the form on purpose: a block saves itself, and nesting the
            two would make one Save button look like it governed both. */}
        <LessonBlocks itemId={itemId} />
      </div>

      <AlertDialog open={blocker.status === "blocked"}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Leave without saving?</AlertDialogTitle>
            <AlertDialogDescription>
              The description, duration or completion rule has changes that have not been saved.
              Anything you saved on a block is already stored.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => blocker.reset?.()}>Stay</AlertDialogCancel>
            <AlertDialogAction onClick={() => blocker.proceed?.()}>Leave</AlertDialogAction>
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
