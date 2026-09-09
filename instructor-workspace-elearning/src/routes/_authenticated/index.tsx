import { useState, type FormEvent } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { BookOpen, Plus, FileText } from "lucide-react";
import { toast } from "sonner";

import { useMyCoursesQuery, useCreateCourseMutation } from "@/hooks/queries";
import { parseApiError, type CourseResponse } from "@/api";
import { PageHeader } from "@/components/workspace/PageHeader";
import { Pager } from "@/components/workspace/Pager";
import { FloatingDetailSheet } from "@/components/workspace/FloatingDetailSheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/format";

const PAGE_SIZE = 20;
const CREATE_FORM_ID = "create-course-form";

/**
 * Everything this instructor teaches.
 *
 * Reads `/courses/mine`, not the public listing: that one is published-only, so
 * an author's unfinished work — the reason they opened the app — would be
 * missing from the page meant to show it.
 */
export const Route = createFileRoute("/_authenticated/")({
  head: () => ({
    meta: [
      { title: "My courses — Lernova for Instructors" },
      { name: "description", content: "The courses you own or co-instruct." },
    ],
  }),
  component: MyCoursesPage,
});

function MyCoursesPage() {
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const { data, isLoading, isError, error, refetch } = useMyCoursesQuery({
    page,
    size: PAGE_SIZE,
  });

  const rows = data?.content ?? [];

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader
        title="My courses"
        description="Everything you own or co-instruct, drafts included."
      >
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus className="h-4 w-4" />
          New course
        </Button>
      </PageHeader>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-32 w-full rounded-xl" />
          ))}
        </div>
      ) : isError ? (
        <div className="card-surface p-8 text-center">
          <p className="text-sm text-muted-foreground">
            {parseApiError(error).message || "Could not load your courses."}
          </p>
          <Button variant="outline" size="sm" className="mt-3" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <EmptyState onCreate={() => setCreating(true)} />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {rows.map((course) => (
              <CourseCard key={course.id} course={course} />
            ))}
          </div>
          <Pager
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            totalElements={data?.totalElements ?? 0}
            onChange={setPage}
            noun="course"
          />
        </>
      )}

      <CreateCourseSheet open={creating} onClose={() => setCreating(false)} />
    </div>
  );
}

function CourseCard({ course }: { course: CourseResponse }) {
  const status = course.status ?? "DRAFT";

  return (
    <Link
      to="/courses/$courseId"
      params={{ courseId: course.id ?? "" }}
      className="card-surface group flex flex-col gap-2 p-4 transition-colors hover:border-primary/40"
    >
      <div className="flex items-start justify-between gap-2">
        <span
          aria-hidden
          className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary"
        >
          <BookOpen className="h-4.5 w-4.5" />
        </span>
        <Badge
          variant={
            status === "PUBLISHED" ? "default" : status === "DRAFT" ? "secondary" : "outline"
          }
          className="h-5 px-1.5 text-[10px] font-semibold uppercase tracking-wider"
        >
          {status}
        </Badge>
      </div>

      <h3 className="line-clamp-2 font-semibold leading-snug group-hover:text-primary">
        {course.title}
      </h3>
      {course.shortDescription && (
        <p className="line-clamp-2 text-xs text-muted-foreground">{course.shortDescription}</p>
      )}

      <p className="mt-auto pt-2 text-[11px] text-muted-foreground">
        {course.level ?? "ALL_LEVELS"} · created {formatDate(course.createdAt)}
      </p>
    </Link>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="card-surface grid min-h-[40vh] place-items-center p-8 text-center">
      <div className="max-w-sm">
        <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
          <FileText className="h-5 w-5" />
        </div>
        <h2 className="mt-4 text-lg font-bold tracking-tight">Nothing here yet</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Courses you own or are added to as a co-instructor appear here, published or not.
        </p>
        <Button size="sm" className="mt-4" onClick={onCreate}>
          <Plus className="h-4 w-4" />
          Create your first course
        </Button>
      </div>
    </div>
  );
}

function CreateCourseSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const create = useCreateCourseMutation();
  const [error, setError] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const data = new FormData(e.target as HTMLFormElement);
    const title = String(data.get("title") ?? "").trim();
    const shortDescription = String(data.get("shortDescription") ?? "").trim();

    create.mutate(
      { title, ...(shortDescription ? { shortDescription } : {}) },
      {
        onSuccess: (course) => {
          toast.success(`"${course.title}" created as a draft.`);
          onClose();
        },
        onError: (err) => setError(parseApiError(err).message || "Could not create that course."),
      },
    );
  };

  return (
    <FloatingDetailSheet
      open={open}
      onOpenChange={(next) => !next && onClose()}
      title="New course"
      description="It starts as a draft, owned by you. Nothing is visible to learners until you publish."
      footerActions={
        <>
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form={CREATE_FORM_ID} disabled={create.isPending}>
            {create.isPending ? "Creating…" : "Create draft"}
          </Button>
        </>
      }
    >
      <form id={CREATE_FORM_ID} onSubmit={submit} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="c-title">Title</Label>
          <Input id="c-title" name="title" required minLength={3} autoFocus />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="c-short">Short description</Label>
          <Textarea id="c-short" name="shortDescription" rows={3} />
          <p className="text-xs text-muted-foreground">
            Optional. The line learners read before opening the course.
          </p>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
      </form>
    </FloatingDetailSheet>
  );
}
