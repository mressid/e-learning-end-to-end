import { useState, type FormEvent } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, Send, Undo2 } from "lucide-react";
import { toast } from "sonner";

import {
  useCourseQuery,
  useSectionsQuery,
  useUpdateCourseMutation,
  useSetCoursePublishedMutation,
} from "@/hooks/queries";
import { parseApiError } from "@/api";
import { PageHeader } from "@/components/workspace/PageHeader";
import { CurriculumEditor } from "@/components/workspace/CurriculumEditor";
import { ResourcePanel } from "@/components/workspace/ResourcePanel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";

const EDIT_FORM_ID = "edit-course-form";

type Tab = "overview" | "curriculum" | "resources";
const TABS: Tab[] = ["overview", "curriculum", "resources"];

/**
 * One course, as its author works on it.
 *
 * Which part is showing lives in the URL and is chosen from the sidebar, which
 * becomes this course while you are inside it. The page renders what the rail
 * points at and owns no navigation of its own — two sets of tabs for one thing
 * is how they end up disagreeing.
 *
 * `item` is which item the rail has selected, so clicking a lesson in the tree
 * opens its section and highlights it. Both survive a refresh and can be
 * pasted to somebody.
 */
export const Route = createFileRoute("/_authenticated/courses/$courseId")({
  validateSearch: (raw: Record<string, unknown>): { tab?: Tab; item?: string } => {
    const tab = raw["tab"];
    const item = raw["item"];
    return {
      ...(TABS.includes(tab as Tab) ? { tab: tab as Tab } : {}),
      ...(typeof item === "string" && item ? { item } : {}),
    };
  },
  head: () => ({ meta: [{ title: "Course — Lernova for Instructors" }] }),
  component: CoursePage,
});

function CoursePage() {
  const { courseId } = Route.useParams();
  const { tab, item } = Route.useSearch();

  const course = useCourseQuery(courseId);
  const sections = useSectionsQuery(courseId);
  const update = useUpdateCourseMutation();
  const setPublished = useSetCoursePublishedMutation();
  const [error, setError] = useState("");

  if (course.isLoading) {
    return (
      <div className="space-y-4 p-4 sm:p-6">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (course.isError || !course.data) {
    return (
      <div className="p-4 sm:p-6">
        <div className="card-surface p-8 text-center">
          <h2 className="text-lg font-bold tracking-tight">Course not found</h2>
          {/*
            The backend answers 404 both for a course that does not exist and one
            you may not see, deliberately — so this cannot say which.
          */}
          <p className="mt-2 text-sm text-muted-foreground">
            {parseApiError(course.error).message ||
              "It may have been removed, or it may not be yours."}
          </p>
          <Button variant="outline" size="sm" className="mt-3" asChild>
            <Link to="/">Back to my courses</Link>
          </Button>
        </div>
      </div>
    );
  }

  const data = course.data;
  const status = data.status ?? "DRAFT";
  const isPublished = status === "PUBLISHED";
  const sectionCount = sections.data?.length ?? 0;
  const active: Tab = tab ?? "overview";

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const form = new FormData(e.target as HTMLFormElement);
    update.mutate(
      {
        courseId,
        title: String(form.get("title") ?? "").trim(),
        shortDescription: String(form.get("shortDescription") ?? "").trim(),
        description: String(form.get("description") ?? "").trim(),
      },
      {
        onSuccess: () => toast.success("Saved."),
        onError: (err) => setError(parseApiError(err).message || "Could not save that."),
      },
    );
  };

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <Link
        to="/"
        className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        My courses
      </Link>

      <PageHeader
        title={data.title ?? "Course"}
        description={data.shortDescription || "No short description yet."}
      >
        <div className="flex items-center gap-2">
          <Badge
            variant={isPublished ? "default" : status === "DRAFT" ? "secondary" : "outline"}
            className="h-6 px-2 text-[10px] font-semibold uppercase tracking-wider"
          >
            {status}
          </Badge>
          <Button
            size="sm"
            variant={isPublished ? "outline" : "default"}
            disabled={setPublished.isPending || (!isPublished && sectionCount === 0)}
            title={
              !isPublished && sectionCount === 0
                ? "Add at least one section with something in it first."
                : undefined
            }
            onClick={() =>
              setPublished.mutate(
                { courseId, published: !isPublished },
                {
                  onSuccess: () => toast.success(isPublished ? "Returned to draft." : "Published."),
                  onError: (err) => toast.error(parseApiError(err).message || "That did not work."),
                },
              )
            }
          >
            {isPublished ? <Undo2 className="h-4 w-4" /> : <Send className="h-4 w-4" />}
            {isPublished ? "Return to draft" : "Publish"}
          </Button>
        </div>
      </PageHeader>

      {active === "overview" && (
        <section className="card-surface p-4 sm:p-5">
          <h2 className="text-sm font-semibold">Details</h2>
          <form id={EDIT_FORM_ID} onSubmit={submit} className="mt-3 space-y-4" key={data.id}>
            <div className="space-y-1.5">
              <Label htmlFor="title">Title</Label>
              <Input id="title" name="title" defaultValue={data.title ?? ""} minLength={3} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="shortDescription">Short description</Label>
              <Textarea
                id="shortDescription"
                name="shortDescription"
                rows={2}
                defaultValue={data.shortDescription ?? ""}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="description">Full description</Label>
              <Textarea
                id="description"
                name="description"
                rows={6}
                defaultValue={data.description ?? ""}
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" size="sm" disabled={update.isPending}>
              {update.isPending ? "Saving…" : "Save changes"}
            </Button>
          </form>
        </section>
      )}

      {active === "curriculum" && (
        <CurriculumEditor courseId={courseId} highlightItemId={item ?? null} />
      )}

      {active === "resources" && (
        <section className="card-surface p-4 sm:p-5">
          <ResourcePanel
            scope="course"
            ownerId={courseId}
            canDetach
            emptyHint="Nothing attached to the course itself yet. A syllabus, a reading list, or a link to somewhere else — material that belongs to the whole course rather than one lesson."
          />
        </section>
      )}
    </div>
  );
}
