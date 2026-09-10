import { useState, type FormEvent, type ReactNode } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, Eye, Send, Undo2 } from "lucide-react";
import { toast } from "sonner";

import {
  useCourseQuery,
  useSectionsQuery,
  useUpdateCourseMutation,
  useSetCoursePublishedMutation,
} from "@/hooks/queries";
import { parseApiError, type CourseResponse, type UpdateCourseRequest } from "@/api";
import { formatDate } from "@/lib/format";
import { PageHeader } from "@/components/workspace/PageHeader";
import { CurriculumBoard } from "@/components/workspace/CurriculumBoard";
import { SectionFocus } from "@/components/workspace/SectionFocus";
import { ResourcePanel } from "@/components/workspace/ResourcePanel";
import { CourseThumbnail } from "@/components/workspace/CourseThumbnail";
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

type Tab = "overview" | "curriculum" | "resources";
const TABS: Tab[] = ["overview", "curriculum", "resources"];

type Level = NonNullable<NonNullable<UpdateCourseRequest["level"]>>;
const LEVELS: Level[] = ["BEGINNER", "INTERMEDIATE", "ADVANCED", "ALL_LEVELS"];
/** The value the Select uses for "no level chosen"; sent to the API as null. */
const NO_LEVEL = "UNSET";

const levelLabel = (level: string) =>
  level === "ALL_LEVELS" ? "All levels" : level.charAt(0) + level.slice(1).toLowerCase();

/**
 * One course, as its author works on it.
 *
 * Which part is showing lives in the URL and is chosen from the sidebar, which
 * becomes this course while you are inside it. The page renders what the rail
 * points at and owns no navigation of its own — two sets of tabs for one thing
 * is how they end up disagreeing.
 *
 * `section` is the section being worked on, and it is the whole of the
 * curriculum screen when it is set: one section, its items, its material.
 * `item` is which item within it the rail has selected, so clicking a quiz in
 * the tree opens its section and marks it. All three survive a refresh and can
 * be pasted to somebody.
 */
export const Route = createFileRoute("/_authenticated/courses/$courseId")({
  validateSearch: (
    raw: Record<string, unknown>,
  ): { tab?: Tab; section?: string; item?: string } => {
    const tab = raw["tab"];
    const section = raw["section"];
    const item = raw["item"];
    return {
      ...(TABS.includes(tab as Tab) ? { tab: tab as Tab } : {}),
      ...(typeof section === "string" && section ? { section } : {}),
      ...(typeof item === "string" && item ? { item } : {}),
    };
  },
  head: () => ({ meta: [{ title: "Course — Lernova for Instructors" }] }),
  component: CoursePage,
});

function CoursePage() {
  const { courseId } = Route.useParams();
  const { tab, section, item } = Route.useSearch();

  const course = useCourseQuery(courseId);
  const sections = useSectionsQuery(courseId);
  const setPublished = useSetCoursePublishedMutation();

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
            variant="outline"
            size="sm"
            disabled={sectionCount === 0}
            asChild={sectionCount > 0}
          >
            {sectionCount > 0 ? (
              <Link to="/courses/$courseId/preview" params={{ courseId }} search={{}}>
                <Eye className="h-4 w-4" />
                Preview
              </Link>
            ) : (
              <>
                <Eye className="h-4 w-4" />
                Preview
              </>
            )}
          </Button>
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
        <CourseOverview
          key={data.id}
          courseId={courseId}
          course={data}
          sectionCount={sectionCount}
        />
      )}

      {active === "curriculum" &&
        (sections.isLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-20 w-full rounded-xl" />
            <Skeleton className="h-20 w-full rounded-xl" />
          </div>
        ) : section ? (
          <SectionFocus
            courseId={courseId}
            sections={sections.data ?? []}
            sectionId={section}
            highlightItemId={item ?? null}
          />
        ) : (
          <CurriculumBoard courseId={courseId} />
        ))}

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

/**
 * What the course is, and the terms it is offered on.
 *
 * Mounted with the course id as its key so the uncontrolled fields below are
 * seeded once from the loaded course rather than fighting with it on every
 * refetch.
 */
function CourseOverview({
  courseId,
  course,
  sectionCount,
}: {
  courseId: string;
  course: CourseResponse;
  sectionCount: number;
}) {
  const update = useUpdateCourseMutation();
  const [level, setLevel] = useState<string>(course.level || NO_LEVEL);
  const [error, setError] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const data = new FormData(e.target as HTMLFormElement);
    const language = String(data.get("language") ?? "").trim();
    const days = String(data.get("accessDurationDays") ?? "").trim();

    update.mutate(
      {
        courseId,
        title: String(data.get("title") ?? "").trim(),
        shortDescription: String(data.get("shortDescription") ?? "").trim(),
        description: String(data.get("description") ?? "").trim(),
        level: level === NO_LEVEL ? null : (level as Level),
        language: language || null,
        // Empty means access that never lapses, which the API spells as null.
        accessDurationDays: days === "" ? null : Number(days),
      },
      {
        onSuccess: () => toast.success("Saved."),
        onError: (err) => setError(parseApiError(err).message || "Could not save that."),
      },
    );
  };

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <form onSubmit={submit} className="space-y-4 lg:col-span-2">
        <section className="card-surface space-y-4 p-4 sm:p-5">
          <div>
            <h2 className="text-sm font-semibold">Details</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">
              What a student reads before deciding to enrol.
            </p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="title">Title</Label>
            <Input id="title" name="title" defaultValue={course.title ?? ""} minLength={3} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="shortDescription">Short description</Label>
            <Textarea
              id="shortDescription"
              name="shortDescription"
              rows={2}
              defaultValue={course.shortDescription ?? ""}
            />
            <p className="text-xs text-muted-foreground">
              One or two sentences. This is the line under the title in a list of courses.
            </p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="description">Full description</Label>
            <Textarea
              id="description"
              name="description"
              rows={8}
              defaultValue={course.description ?? ""}
            />
          </div>
        </section>

        <section className="card-surface space-y-4 p-4 sm:p-5">
          <div>
            <h2 className="text-sm font-semibold">Audience and access</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Who the course is for, and for how long they keep it.
            </p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Level</Label>
              <Select value={level} onValueChange={setLevel}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_LEVEL}>Not set</SelectItem>
                  {LEVELS.map((value) => (
                    <SelectItem key={value} value={value}>
                      {levelLabel(value)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="language">Language</Label>
              <Input
                id="language"
                name="language"
                defaultValue={course.language ?? ""}
                placeholder="en"
                maxLength={16}
              />
              <p className="text-xs text-muted-foreground">
                The language it is taught in, as a code — <code>en</code>, <code>fr</code>,{" "}
                <code>ar</code>.
              </p>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="accessDurationDays">Access duration</Label>
            <div className="flex items-center gap-2">
              <Input
                id="accessDurationDays"
                name="accessDurationDays"
                type="number"
                min={1}
                className="max-w-32"
                defaultValue={course.accessDurationDays ?? ""}
                placeholder="∞"
              />
              <span className="text-sm text-muted-foreground">days from enrolling</span>
            </div>
            {/* Worth saying, because it is the kind of change people expect to
                be retroactive and are alarmed when it is not. */}
            <p className="text-xs text-muted-foreground">
              Leave it empty for access that never lapses. Changing it applies to people who enrol
              afterwards; nobody already enrolled loses time they were given.
            </p>
          </div>
        </section>

        {error && <p className="text-sm text-destructive">{error}</p>}
        <Button type="submit" size="sm" disabled={update.isPending}>
          {update.isPending ? "Saving…" : "Save changes"}
        </Button>
      </form>

      <div className="space-y-4">
        {/* Outside the form on purpose, the way attaching a resource to a
            lesson is: choosing an image uploads and applies it there and then,
            so putting it beside fields that only take effect on Save would be
            claiming it works the same way. */}
        <section className="card-surface p-4 sm:p-5">
          <CourseThumbnail courseId={courseId} thumbnailUrl={course.thumbnailUrl} />
        </section>

        <section className="card-surface h-fit space-y-3 p-4 sm:p-5">
          <h2 className="text-sm font-semibold">At a glance</h2>
          <dl className="space-y-2.5 text-xs">
            <Fact label="Status" value={course.status ?? "DRAFT"} />
            <Fact label="Sections" value={String(sectionCount)} />
            <Fact label="Level" value={course.level ? levelLabel(course.level) : "Not set"} />
            <Fact label="Language" value={course.language || "Not set"} />
            <Fact
              label="Access"
              value={
                course.accessDurationDays ? `${course.accessDurationDays} days` : "Never lapses"
              }
            />
            <Fact label="Created" value={formatDate(course.createdAt)} />
            <Fact
              label="Published"
              value={course.publishedAt ? formatDate(course.publishedAt) : "Not yet"}
            />
            <Fact label="Slug" value={course.slug || "—"} mono />
          </dl>

          {course.categories?.length || course.tags?.length ? (
            <div className="space-y-2 border-t pt-3">
              {/* Read-only here on purpose: the catalogue's vocabulary is the
                administrators' to set, and an instructor picking from it is a
                screen this workspace does not have yet. */}
              <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                Catalogue
              </p>
              <div className="flex flex-wrap gap-1">
                {course.categories?.map((term) => (
                  <Badge key={term.id} variant="secondary" className="text-[10px]">
                    {term.name}
                  </Badge>
                ))}
                {course.tags?.map((term) => (
                  <Badge key={term.id} variant="outline" className="text-[10px]">
                    {term.name}
                  </Badge>
                ))}
              </div>
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}

function Fact({ label, value, mono }: { label: string; value: ReactNode; mono?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd
        className={`min-w-0 truncate text-right font-medium ${mono ? "font-mono text-[11px]" : ""}`}
      >
        {value}
      </dd>
    </div>
  );
}
