import { useMemo } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useQueries } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Eye, Pencil } from "lucide-react";

import { useCourseQuery, useSectionsQuery } from "@/hooks/queries";
import { curriculumApi, queryKeys, type CourseItemResponse } from "@/api";
import { itemIcon, itemLabelShort } from "@/components/workspace/curriculum-ui";
import { LessonPreview } from "@/components/preview/LessonPreview";
import { QuizPreview } from "@/components/preview/QuizPreview";
import { AssignmentPreview } from "@/components/preview/AssignmentPreview";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** One item, with the section it sits in, in the order a student would meet them. */
interface ReadingStep {
  item: CourseItemResponse;
  sectionTitle: string;
}

/**
 * The course as a student meets it.
 *
 * Everything an author writes is read by somebody else, and until now there was
 * nowhere to see that. A lesson looked like a form full of blocks and a quiz
 * looked like a list of editor rows, neither of which is what the course
 * actually is. This is the same content with the authoring taken away.
 *
 * Two things are honestly missing rather than faked. Progress needs an active
 * enrolment and an author has none, so nothing here is locked or ticked off —
 * the course reads fully open. And a quiz is shown as the paper rather than as
 * an attempt, because starting one also needs an enrolment. Inventing either
 * would mean a second implementation that no student ever runs, which is how
 * previews start lying.
 *
 * The underscore in the filename keeps this a page rather than a child of the
 * course route, which renders no `<Outlet />`.
 */
export const Route = createFileRoute("/_authenticated/courses/$courseId_/preview")({
  validateSearch: (raw: Record<string, unknown>): { item?: string } => {
    const item = raw["item"];
    return typeof item === "string" && item ? { item } : {};
  },
  head: () => ({ meta: [{ title: "Preview — Lernova for Instructors" }] }),
  component: PreviewPage,
});

function PreviewPage() {
  const { courseId } = Route.useParams();
  const { item: selectedId } = Route.useSearch();

  const course = useCourseQuery(courseId);
  const sections = useSectionsQuery(courseId);
  const sectionRows = useMemo(() => sections.data ?? [], [sections.data]);

  // Every section's items at once, because the reading order is the whole
  // course and prev/next cannot be answered one section at a time.
  const itemQueries = useQueries({
    queries: sectionRows.map((section) => ({
      queryKey: queryKeys.curriculum.items(courseId, section.id ?? ""),
      queryFn: () => curriculumApi.items(section.id ?? ""),
      enabled: Boolean(section.id),
    })),
  });

  const loadingItems = itemQueries.some((query) => query.isLoading);

  const steps = useMemo<ReadingStep[]>(
    () =>
      sectionRows.flatMap((section, index) =>
        (itemQueries[index]?.data ?? []).map((item) => ({
          item,
          sectionTitle: section.title ?? "Untitled section",
        })),
      ),
    // The queries array is rebuilt every render, so it is the data inside it
    // that this depends on, not its identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [sectionRows, itemQueries.map((query) => query.data).join("|")],
  );

  const position = steps.findIndex((step) => step.item.id === selectedId);
  // Landing on the preview with nothing named opens at the beginning, which is
  // where a student starts.
  const current = position >= 0 ? steps[position] : steps[0];
  const index = position >= 0 ? position : 0;

  if (course.isLoading || sections.isLoading || loadingItems) {
    return (
      <div className="space-y-4 p-4 sm:p-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-[60vh] w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-6xl space-y-4 p-4 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Eye className="h-4 w-4 shrink-0 text-muted-foreground" />
            <h1 className="truncate text-lg font-bold tracking-tight">
              {course.data?.title ?? "Course"}
            </h1>
          </div>
          <p className="mt-0.5 text-xs text-muted-foreground">
            What a student sees. Nothing here is locked or ticked off, and a quiz is shown as the
            paper rather than as an attempt, because both of those need an enrolment.
          </p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link
            to="/courses/$courseId"
            params={{ courseId }}
            search={{ tab: "curriculum", ...(current ? { item: current.item.id ?? "" } : {}) }}
          >
            <Pencil className="mr-1.5 h-3.5 w-3.5" />
            Back to editing
          </Link>
        </Button>
      </div>

      {steps.length === 0 ? (
        <div className="card-surface p-10 text-center">
          <h2 className="text-base font-semibold">There is nothing to see yet</h2>
          <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
            This course has no items in it, so a student who enrolled would open it and find an
            empty shelf.
          </p>
        </div>
      ) : (
        <div className="grid gap-5 lg:grid-cols-[240px_minmax(0,1fr)]">
          <Contents courseId={courseId} steps={steps} currentId={current?.item.id ?? ""} />
          <main className="min-w-0 space-y-5">
            {current && <StepView courseId={courseId} step={current} />}
            <Navigation courseId={courseId} steps={steps} index={index} />
          </main>
        </div>
      )}
    </div>
  );
}

/** The course's shape, grouped the way it is written rather than as one long list. */
function Contents({
  courseId,
  steps,
  currentId,
}: {
  courseId: string;
  steps: ReadingStep[];
  currentId: string;
}) {
  const grouped = steps.reduce<{ title: string; items: CourseItemResponse[] }[]>((acc, step) => {
    const last = acc[acc.length - 1];
    if (last && last.title === step.sectionTitle) last.items.push(step.item);
    else acc.push({ title: step.sectionTitle, items: [step.item] });
    return acc;
  }, []);

  return (
    <nav aria-label="Course contents" className="lg:sticky lg:top-6 lg:self-start">
      <ol className="space-y-4">
        {grouped.map((group, groupIndex) => (
          <li key={`${group.title}-${groupIndex}`}>
            <p className="px-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
              {group.title}
            </p>
            <ul className="mt-1 space-y-0.5">
              {group.items.map((item) => {
                const Icon = itemIcon(item.type);
                const isCurrent = item.id === currentId;
                return (
                  <li key={item.id}>
                    <Link
                      to="/courses/$courseId/preview"
                      params={{ courseId }}
                      search={{ item: item.id ?? "" }}
                      className={cn(
                        "flex items-center gap-2 rounded-md px-2 py-1.5 text-xs transition-colors",
                        isCurrent
                          ? "bg-primary font-medium text-primary-foreground"
                          : "text-muted-foreground hover:bg-secondary hover:text-foreground",
                      )}
                      aria-current={isCurrent ? "page" : undefined}
                    >
                      <Icon className="h-3 w-3 shrink-0" />
                      <span className="min-w-0 flex-1 truncate">{item.title}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </li>
        ))}
      </ol>
    </nav>
  );
}

function StepView({ courseId, step }: { courseId: string; step: ReadingStep }) {
  const itemId = step.item.id ?? "";

  return (
    <article className="space-y-4">
      <header className="space-y-1.5">
        <p className="text-xs text-muted-foreground">{step.sectionTitle}</p>
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-xl font-bold tracking-tight">{step.item.title}</h2>
          <Badge variant="outline" className="text-[10px] uppercase tracking-wider">
            {itemLabelShort(step.item.type)}
          </Badge>
          {step.item.isRequired === false && (
            <Badge variant="secondary" className="text-[10px] uppercase tracking-wider">
              Optional
            </Badge>
          )}
        </div>
      </header>

      {step.item.type === "QUIZ" ? (
        <QuizPreview key={itemId} courseId={courseId} itemId={itemId} />
      ) : step.item.type === "ASSIGNMENT" ? (
        <AssignmentPreview key={itemId} courseId={courseId} itemId={itemId} />
      ) : (
        <LessonPreview key={itemId} courseId={courseId} itemId={itemId} />
      )}
    </article>
  );
}

function Navigation({
  courseId,
  steps,
  index,
}: {
  courseId: string;
  steps: ReadingStep[];
  index: number;
}) {
  const previous = index > 0 ? steps[index - 1] : undefined;
  const next = index < steps.length - 1 ? steps[index + 1] : undefined;

  return (
    <div className="flex items-center justify-between gap-3 border-t pt-4">
      {previous ? (
        <Button variant="outline" size="sm" className="max-w-[45%]" asChild>
          <Link
            to="/courses/$courseId/preview"
            params={{ courseId }}
            search={{ item: previous.item.id ?? "" }}
          >
            <ChevronLeft className="mr-1 h-3.5 w-3.5 shrink-0 rtl:rotate-180" />
            <span className="truncate">{previous.item.title}</span>
          </Link>
        </Button>
      ) : (
        <span />
      )}
      <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
        {index + 1} of {steps.length}
      </span>
      {next ? (
        <Button variant="outline" size="sm" className="max-w-[45%]" asChild>
          <Link
            to="/courses/$courseId/preview"
            params={{ courseId }}
            search={{ item: next.item.id ?? "" }}
          >
            <span className="truncate">{next.item.title}</span>
            <ChevronRight className="ml-1 h-3.5 w-3.5 shrink-0 rtl:rotate-180" />
          </Link>
        </Button>
      ) : (
        <span />
      )}
    </div>
  );
}
