import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  ChevronDown,
  ChevronRight,
  ChevronUp,
  FileText,
  HelpCircle,
  FileCheck,
  Layers,
  Pencil,
  Plus,
  Trash2,
} from "lucide-react";
import { Link } from "@tanstack/react-router";
import { toast } from "sonner";

import {
  useSectionsQuery,
  useItemsQuery,
  useAddSectionMutation,
  useUpdateSectionMutation,
  useDeleteSectionMutation,
  useReorderSectionsMutation,
  useAddItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation,
  useReorderItemsMutation,
} from "@/hooks/queries";
import {
  parseApiError,
  type CourseItemResponse,
  type CourseItemType,
  type SectionResponse,
} from "@/api";
import { cn } from "@/lib/utils";
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

const ITEM_TYPES: CourseItemType[] = ["LESSON", "QUIZ", "ASSIGNMENT"];

function itemIcon(type: string | undefined) {
  if (type === "QUIZ") return HelpCircle;
  if (type === "ASSIGNMENT") return FileCheck;
  return FileText;
}

const fail = (err: unknown, fallback: string) =>
  toast.error(parseApiError(err).message || fallback);

/**
 * The whole shape of a course: sections, the items in them, and what hangs off
 * each.
 *
 * Ordering is done with arrows rather than drag-and-drop. The API reorders by
 * being sent the complete sequence, which a pair of buttons satisfies exactly;
 * dragging would add a dependency and a lot of pointer handling to produce the
 * same array.
 */
export function CurriculumEditor({
  courseId,
  /** The item the sidebar has selected, if any. Its section opens and it is marked. */
  highlightItemId = null,
}: {
  courseId: string;
  highlightItemId?: string | null;
}) {
  const sections = useSectionsQuery(courseId);
  const addSection = useAddSectionMutation(courseId);
  const reorderSections = useReorderSectionsMutation(courseId);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");

  const rows = sections.data ?? [];
  const orderedIds = rows.map((s) => s.id!).filter(Boolean);

  const move = (index: number, delta: number) => {
    const next = [...orderedIds];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target]!, next[index]!];
    reorderSections.mutate(next, {
      onError: (err) => fail(err, "Could not reorder those."),
    });
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const form = new FormData(e.target as HTMLFormElement);
    const title = String(form.get("title") ?? "").trim();
    const description = String(form.get("description") ?? "").trim();
    addSection.mutate(
      { title, ...(description ? { description } : {}) },
      {
        onSuccess: () => {
          toast.success("Section added.");
          setAdding(false);
          (e.target as HTMLFormElement).reset();
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
        <h2 className="flex items-center gap-2 text-sm font-semibold">
          <Layers className="h-4 w-4" />
          Curriculum
          <span className="text-xs font-normal text-muted-foreground">
            {rows.length} {rows.length === 1 ? "section" : "sections"}
          </span>
        </h2>
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
        <div className="space-y-3">
          {rows.map((section, index) => (
            <SectionCard
              key={section.id}
              courseId={courseId}
              section={section}
              index={index}
              isFirst={index === 0}
              isLast={index === rows.length - 1}
              onMove={(delta) => move(index, delta)}
              reordering={reorderSections.isPending}
              highlightItemId={highlightItemId}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SectionCard({
  courseId,
  section,
  index,
  isFirst,
  isLast,
  onMove,
  reordering,
  highlightItemId,
}: {
  courseId: string;
  section: SectionResponse;
  index: number;
  isFirst: boolean;
  isLast: boolean;
  onMove: (delta: number) => void;
  reordering: boolean;
  highlightItemId: string | null;
}) {
  const sectionId = section.id!;
  const [expanded, setExpanded] = useState(true);
  const [editing, setEditing] = useState(false);
  const [addingItem, setAddingItem] = useState(false);
  const [itemType, setItemType] = useState<CourseItemType>("LESSON");

  const items = useItemsQuery(sectionId, expanded);
  const updateSection = useUpdateSectionMutation(courseId);
  const deleteSection = useDeleteSectionMutation(courseId);
  const addItem = useAddItemMutation(courseId);
  const reorderItems = useReorderItemsMutation(courseId);

  const rows = items.data ?? [];
  const orderedIds = rows.map((i) => i.id!).filter(Boolean);

  const moveItem = (at: number, delta: number) => {
    const next = [...orderedIds];
    const target = at + delta;
    if (target < 0 || target >= next.length) return;
    [next[at], next[target]] = [next[target]!, next[at]!];
    reorderItems.mutate(
      { sectionId, orderedIds: next },
      { onError: (err) => fail(err, "Could not reorder those.") },
    );
  };

  const saveSection = (e: FormEvent) => {
    e.preventDefault();
    const form = new FormData(e.target as HTMLFormElement);
    updateSection.mutate(
      {
        sectionId,
        title: String(form.get("title") ?? "").trim(),
        description: String(form.get("description") ?? "").trim(),
      },
      {
        onSuccess: () => {
          toast.success("Section updated.");
          setEditing(false);
        },
        onError: (err) => fail(err, "Could not save that."),
      },
    );
  };

  const submitItem = (e: FormEvent) => {
    e.preventDefault();
    const form = new FormData(e.target as HTMLFormElement);
    addItem.mutate(
      {
        sectionId,
        title: String(form.get("title") ?? "").trim(),
        type: itemType,
      },
      {
        onSuccess: () => {
          toast.success("Item added.");
          setAddingItem(false);
          setItemType("LESSON");
        },
        onError: (err) => fail(err, "Could not add that item."),
      },
    );
  };

  return (
    <section className="card-surface overflow-hidden">
      <div className="flex items-start gap-2 p-4">
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
          aria-label={expanded ? "Collapse section" : "Expand section"}
        >
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </button>

        <span className="mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-md bg-secondary text-[11px] font-bold">
          {index + 1}
        </span>

        <div className="min-w-0 flex-1">
          {editing ? (
            <form onSubmit={saveSection} className="space-y-2">
              <Input name="title" defaultValue={section.title} required autoFocus />
              <Textarea name="description" rows={2} defaultValue={section.description ?? ""} />
              <div className="flex gap-2">
                <Button type="submit" size="sm" disabled={updateSection.isPending}>
                  Save
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(false)}>
                  Cancel
                </Button>
              </div>
            </form>
          ) : (
            <>
              <h3 className="font-semibold leading-snug">{section.title}</h3>
              {section.description && (
                <p className="mt-0.5 text-xs text-muted-foreground">{section.description}</p>
              )}
            </>
          )}
        </div>

        {!editing && (
          <div className="flex shrink-0 items-center gap-0.5">
            <Button
              variant="ghost"
              size="sm"
              disabled={isFirst || reordering}
              onClick={() => onMove(-1)}
              title="Move up"
            >
              <ChevronUp className="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={isLast || reordering}
              onClick={() => onMove(1)}
              title="Move down"
            >
              <ChevronDown className="h-3.5 w-3.5" />
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setEditing(true)} title="Rename">
              <Pencil className="h-3.5 w-3.5" />
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
                  // 422 here means a student has already worked on something
                  // inside it, and the server refuses rather than destroying
                  // their progress. The message says so.
                  onError: (err) => fail(err, "Could not delete that section."),
                });
              }}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        )}
      </div>

      {expanded && (
        <div className="space-y-4 border-t bg-muted/20 p-4">
          {items.isLoading ? (
            <Skeleton className="h-16 w-full rounded-lg" />
          ) : rows.length === 0 ? (
            <p className="text-xs text-muted-foreground">Nothing in this section yet.</p>
          ) : (
            <ol className="space-y-1.5">
              {rows.map((item, at) => (
                <ItemRow
                  key={item.id}
                  courseId={courseId}
                  item={item}
                  isFirst={at === 0}
                  isLast={at === rows.length - 1}
                  onMove={(delta) => moveItem(at, delta)}
                  reordering={reorderItems.isPending}
                  highlighted={highlightItemId === item.id}
                />
              ))}
            </ol>
          )}

          {addingItem ? (
            <form onSubmit={submitItem} className="space-y-2 rounded-lg border bg-card p-3">
              <div className="space-y-1.5">
                <Label htmlFor={`i-title-${sectionId}`}>Item title</Label>
                <Input id={`i-title-${sectionId}`} name="title" required autoFocus />
              </div>
              <div className="space-y-1.5">
                <Label>Type</Label>
                <Select value={itemType} onValueChange={(v) => setItemType(v as CourseItemType)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ITEM_TYPES.map((t) => (
                      <SelectItem key={t} value={t}>
                        {t.charAt(0) + t.slice(1).toLowerCase()}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {/* Said now rather than discovered later: the type cannot be
                    changed once the item exists. */}
                <p className="text-xs text-muted-foreground">
                  Fixed once created — a lesson and a quiz store different things.
                </p>
              </div>
              <div className="flex gap-2">
                <Button type="submit" size="sm" disabled={addItem.isPending}>
                  {addItem.isPending ? "Adding…" : "Add"}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => setAddingItem(false)}
                >
                  Cancel
                </Button>
              </div>
            </form>
          ) : (
            <Button variant="outline" size="sm" onClick={() => setAddingItem(true)}>
              <Plus className="h-3.5 w-3.5" />
              Add item
            </Button>
          )}

          <div className="border-t pt-4">
            <ResourcePanel
              scope="section"
              ownerId={sectionId}
              emptyHint="Nothing attached to this section. Material that belongs to the whole chapter goes here."
            />
          </div>
        </div>
      )}
    </section>
  );
}

function ItemRow({
  courseId,
  item,
  isFirst,
  isLast,
  onMove,
  reordering,
  highlighted,
}: {
  courseId: string;
  item: CourseItemResponse;
  isFirst: boolean;
  isLast: boolean;
  onMove: (delta: number) => void;
  reordering: boolean;
  highlighted: boolean;
}) {
  const itemId = item.id!;
  const Icon = itemIcon(item.type);
  const [editing, setEditing] = useState(false);
  const [showResources, setShowResources] = useState(false);

  const updateItem = useUpdateItemMutation(courseId);
  const deleteItem = useDeleteItemMutation(courseId);

  const save = (e: FormEvent) => {
    e.preventDefault();
    const form = new FormData(e.target as HTMLFormElement);
    updateItem.mutate(
      { itemId, title: String(form.get("title") ?? "").trim() },
      {
        onSuccess: () => {
          toast.success("Item renamed.");
          setEditing(false);
        },
        onError: (err) => fail(err, "Could not rename that."),
      },
    );
  };

  // Brought into view when the sidebar selects it: in a long course the row
  // being highlighted is often below the fold.
  const rowRef = useRef<HTMLLIElement | null>(null);
  useEffect(() => {
    if (highlighted) rowRef.current?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [highlighted]);

  return (
    <li
      ref={rowRef}
      className={cn(
        "rounded-lg border bg-card transition-colors",
        highlighted && "border-primary ring-1 ring-primary/40",
      )}
    >
      <div className="flex items-center gap-2 p-2.5">
        <Icon className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />

        {editing ? (
          <form onSubmit={save} className="flex flex-1 items-center gap-2">
            <Input name="title" defaultValue={item.title} required autoFocus className="h-8" />
            <Button type="submit" size="sm" disabled={updateItem.isPending}>
              Save
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </form>
        ) : (
          <>
            <span className="min-w-0 flex-1 truncate text-sm">{item.title}</span>
            <Badge variant="outline" className="h-4 shrink-0 px-1 text-[9px] uppercase">
              {item.type}
            </Badge>
            {!item.isRequired && (
              <Badge variant="secondary" className="h-4 shrink-0 px-1 text-[9px]">
                optional
              </Badge>
            )}

            <div className="flex shrink-0 items-center gap-0.5">
              {/* A page of its own, not a sheet: writing a lesson is the task,
                  not a quick edit beside the list. */}
              {item.type === "LESSON" && (
                <Button variant="ghost" size="sm" asChild title="Write this lesson">
                  <Link to="/courses/$courseId/items/$itemId" params={{ courseId, itemId }}>
                    Content
                  </Link>
                </Button>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowResources((prev) => !prev)}
                title="Documents and links for this item"
              >
                Files
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={isFirst || reordering}
                onClick={() => onMove(-1)}
                title="Move up"
              >
                <ChevronUp className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={isLast || reordering}
                onClick={() => onMove(1)}
                title="Move down"
              >
                <ChevronDown className="h-3.5 w-3.5" />
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setEditing(true)} title="Rename">
                <Pencil className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={deleteItem.isPending}
                title="Delete this item"
                onClick={() => {
                  if (!window.confirm(`Delete "${item.title}"?`)) return;
                  deleteItem.mutate(itemId, {
                    onSuccess: () => toast.success("Item deleted."),
                    onError: (err) => fail(err, "Could not delete that item."),
                  });
                }}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </>
        )}
      </div>

      {showResources && (
        <div className="border-t p-3">
          <ResourcePanel
            scope="item"
            ownerId={itemId}
            emptyHint="Nothing attached to this item yet."
          />
        </div>
      )}
    </li>
  );
}
