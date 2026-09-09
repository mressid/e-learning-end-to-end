import { useState, type FormEvent } from "react";
import { Link } from "@tanstack/react-router";
import { ChevronRight, Layers, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import {
  useSectionsQuery,
  useItemsQuery,
  useAddSectionMutation,
  useDeleteSectionMutation,
  useReorderSectionsMutation,
} from "@/hooks/queries";
import { parseApiError, type SectionResponse } from "@/api";
import { fail, tally } from "@/components/workspace/curriculum-ui";
import {
  DragHandle,
  SortableList,
  SortableRow,
  type DragHandleProps,
} from "@/components/workspace/Sortable";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/**
 * The course at one level: its sections, in order, and nothing else.
 *
 * This used to be the whole curriculum — every section expanded, every item,
 * every attachment, one page. It answered "what is in this course?" and
 * "what is in this section?" at the same time and neither of them well: the
 * thing being edited was always somewhere in the middle of everything else.
 *
 * So the two questions are two screens now. This one is the shape of the
 * course. Opening a section goes to `SectionFocus`, where that section is the
 * only thing on the page.
 */
export function CurriculumBoard({ courseId }: { courseId: string }) {
  const sections = useSectionsQuery(courseId);
  const addSection = useAddSectionMutation(courseId);
  const reorder = useReorderSectionsMutation(courseId);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");

  const rows = sections.data ?? [];
  const ids = rows.map((section) => section.id ?? "").filter(Boolean);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const form = e.target as HTMLFormElement;
    const data = new FormData(form);
    const title = String(data.get("title") ?? "").trim();
    const description = String(data.get("description") ?? "").trim();
    addSection.mutate(
      { title, ...(description ? { description } : {}) },
      {
        onSuccess: () => {
          toast.success("Section added.");
          setAdding(false);
          form.reset();
        },
        onError: (err) => setError(parseApiError(err).message || "Could not add that section."),
      },
    );
  };

  if (sections.isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-20 w-full rounded-xl" />
        <Skeleton className="h-20 w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            <Layers className="h-4 w-4" />
            Curriculum
          </h2>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {rows.length === 0
              ? "No sections yet."
              : `${rows.length} ${rows.length === 1 ? "section" : "sections"}, in the order students meet them. Drag to change it.`}
          </p>
        </div>
        {!adding && (
          <Button size="sm" onClick={() => setAdding(true)}>
            <Plus className="h-4 w-4" />
            Add section
          </Button>
        )}
      </div>

      {adding && (
        <form onSubmit={submit} className="card-surface space-y-3 p-4">
          <div className="space-y-1.5">
            <Label htmlFor="s-title">Section title</Label>
            <Input id="s-title" name="title" required minLength={1} autoFocus />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="s-description">Description</Label>
            <Textarea id="s-description" name="description" rows={2} />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={addSection.isPending}>
              {addSection.isPending ? "Adding…" : "Add section"}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => {
                setAdding(false);
                setError("");
              }}
            >
              Cancel
            </Button>
          </div>
        </form>
      )}

      {rows.length === 0 && !adding ? (
        <div className="card-surface grid min-h-[30vh] place-items-center p-8 text-center">
          <div className="max-w-sm">
            <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
              <Layers className="h-5 w-5" />
            </div>
            <h3 className="mt-4 font-bold tracking-tight">No sections yet</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              A section is a chapter — a group of lessons, quizzes and assignments. A course needs
              at least one item before it can be published.
            </p>
            <Button size="sm" className="mt-4" onClick={() => setAdding(true)}>
              <Plus className="h-4 w-4" />
              Add the first section
            </Button>
          </div>
        </div>
      ) : (
        <SortableList
          ids={ids}
          className="space-y-2"
          onReorder={(orderedIds) =>
            reorder.mutate(orderedIds, { onError: (err) => fail(err, "Could not reorder those.") })
          }
        >
          {rows.map((section, index) => (
            <SortableRow key={section.id} id={section.id ?? ""}>
              {({ handle, isDragging }) => (
                <SectionCard
                  courseId={courseId}
                  section={section}
                  index={index}
                  handle={handle}
                  isDragging={isDragging}
                />
              )}
            </SortableRow>
          ))}
        </SortableList>
      )}
    </div>
  );
}

function SectionCard({
  courseId,
  section,
  index,
  handle,
  isDragging,
}: {
  courseId: string;
  section: SectionResponse;
  index: number;
  handle: DragHandleProps;
  isDragging: boolean;
}) {
  const sectionId = section.id ?? "";
  // Cached under the course, and the sidebar tree asks for the same lists — a
  // section already open in the rail costs nothing to count here.
  const items = useItemsQuery(courseId, sectionId);
  const counts = tally(items.data);
  const deleteSection = useDeleteSectionMutation(courseId);

  const parts = [
    counts.lessons && `${counts.lessons} ${counts.lessons === 1 ? "lesson" : "lessons"}`,
    counts.quizzes && `${counts.quizzes} ${counts.quizzes === 1 ? "quiz" : "quizzes"}`,
    counts.assignments &&
      `${counts.assignments} ${counts.assignments === 1 ? "assignment" : "assignments"}`,
  ].filter(Boolean) as string[];

  return (
    <div
      className={cn(
        "card-surface flex items-center gap-2 p-3 transition-colors",
        isDragging ? "border-primary/60" : "hover:border-primary/40",
      )}
    >
      <DragHandle handle={handle} label={`Reorder ${section.title ?? "section"}`} />

      <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-secondary text-xs font-bold">
        {index + 1}
      </span>

      <Link
        to="/courses/$courseId"
        params={{ courseId }}
        search={{ tab: "curriculum", section: sectionId }}
        className="min-w-0 flex-1 rounded-lg px-1 py-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span className="block truncate font-semibold leading-snug">{section.title}</span>
        {section.description ? (
          <span className="mt-0.5 block truncate text-xs text-muted-foreground">
            {section.description}
          </span>
        ) : null}
        <span className="mt-1 block text-[11px] text-muted-foreground">
          {items.isLoading ? "…" : parts.length > 0 ? parts.join(" · ") : "Empty"}
        </span>
      </Link>

      <Button variant="ghost" size="sm" asChild title="Open this section">
        <Link
          to="/courses/$courseId"
          params={{ courseId }}
          search={{ tab: "curriculum", section: sectionId }}
        >
          Open
          <ChevronRight className="h-3.5 w-3.5 rtl:rotate-180" />
        </Link>
      </Button>

      <Button
        variant="ghost"
        size="sm"
        disabled={deleteSection.isPending}
        title="Delete this section and its items"
        onClick={() => {
          if (!window.confirm(`Delete "${section.title}" and everything in it?`)) return;
          deleteSection.mutate(sectionId, {
            onSuccess: () => toast.success("Section deleted."),
            // 422 here means a student has already worked on something inside
            // it, and the server refuses rather than destroying their progress.
            // The message says so.
            onError: (err) => fail(err, "Could not delete that section."),
          });
        }}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </Button>
    </div>
  );
}
