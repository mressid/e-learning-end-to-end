import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "@tanstack/react-router";
import { ChevronLeft, ChevronRight, Layers, Paperclip, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import {
  useItemsQuery,
  useAddItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation,
  useReorderItemsMutation,
  useUpdateSectionMutation,
  useDeleteSectionMutation,
} from "@/hooks/queries";
import type { CourseItemResponse, CourseItemType, SectionResponse } from "@/api";
import { ITEM_TYPES, fail, itemIcon, itemLabel, tally } from "@/components/workspace/curriculum-ui";
import {
  DragHandle,
  SortableList,
  SortableRow,
  type DragHandleProps,
} from "@/components/workspace/Sortable";
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
import { cn } from "@/lib/utils";

/**
 * One section, with the page to itself.
 *
 * Choosing a section in the rail brings you here rather than scrolling a long
 * accordion to it: what you are working on is at the top of the screen, the
 * rest of the course is not competing with it, and the URL says which section
 * it is, so it survives a refresh and can be sent to somebody.
 *
 * Leaving is the point of the header — the neighbours are one click away, and
 * "All sections" goes back to the shape of the course.
 */
export function SectionFocus({
  courseId,
  sections,
  sectionId,
  highlightItemId = null,
}: {
  courseId: string;
  sections: SectionResponse[];
  sectionId: string;
  highlightItemId?: string | null;
}) {
  const index = sections.findIndex((section) => section.id === sectionId);
  const section = index >= 0 ? sections[index] : undefined;
  const previous = index > 0 ? sections[index - 1] : undefined;
  const next = index >= 0 && index < sections.length - 1 ? sections[index + 1] : undefined;

  const items = useItemsQuery(courseId, sectionId, Boolean(section));
  const updateSection = useUpdateSectionMutation(courseId);
  const deleteSection = useDeleteSectionMutation(courseId);
  const addItem = useAddItemMutation(courseId);
  const reorderItems = useReorderItemsMutation(courseId);

  const [editing, setEditing] = useState(false);
  const [addingItem, setAddingItem] = useState(false);
  const [itemType, setItemType] = useState<CourseItemType>("LESSON");

  // A section deleted in another tab, or a stale link. Say so rather than
  // rendering an empty page that looks broken.
  if (!section) {
    return (
      <div className="card-surface p-8 text-center">
        <h2 className="text-lg font-bold tracking-tight">That section is not here</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          It may have been deleted, or the link may be from another course.
        </p>
        <Button variant="outline" size="sm" className="mt-3" asChild>
          <Link to="/courses/$courseId" params={{ courseId }} search={{ tab: "curriculum" }}>
            All sections
          </Link>
        </Button>
      </div>
    );
  }

  const rows = items.data ?? [];
  const ids = rows.map((item) => item.id ?? "").filter(Boolean);
  const counts = tally(items.data);

  const saveSection = (e: FormEvent) => {
    e.preventDefault();
    const data = new FormData(e.target as HTMLFormElement);
    updateSection.mutate(
      {
        sectionId,
        title: String(data.get("title") ?? "").trim(),
        description: String(data.get("description") ?? "").trim(),
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
    const form = e.target as HTMLFormElement;
    const data = new FormData(form);
    addItem.mutate(
      { sectionId, title: String(data.get("title") ?? "").trim(), type: itemType },
      {
        onSuccess: () => {
          toast.success(`${itemLabel(itemType)} added.`);
          setAddingItem(false);
          setItemType("LESSON");
          form.reset();
        },
        onError: (err) => fail(err, "Could not add that item."),
      },
    );
  };

  return (
    <div className="space-y-4">
      <nav className="flex flex-wrap items-center justify-between gap-2">
        <Button variant="ghost" size="sm" asChild>
          <Link to="/courses/$courseId" params={{ courseId }} search={{ tab: "curriculum" }}>
            <Layers className="h-3.5 w-3.5" />
            All sections
          </Link>
        </Button>

        <div className="flex items-center gap-1.5">
          <span className="text-xs text-muted-foreground">
            Section {index + 1} of {sections.length}
          </span>
          <Button variant="outline" size="sm" disabled={!previous} asChild={Boolean(previous)}>
            {previous ? (
              <Link
                to="/courses/$courseId"
                params={{ courseId }}
                search={{ tab: "curriculum", section: previous.id ?? "" }}
                title={previous.title}
              >
                <ChevronLeft className="h-3.5 w-3.5 rtl:rotate-180" />
                Previous
              </Link>
            ) : (
              <span>
                <ChevronLeft className="h-3.5 w-3.5 rtl:rotate-180" />
                Previous
              </span>
            )}
          </Button>
          <Button variant="outline" size="sm" disabled={!next} asChild={Boolean(next)}>
            {next ? (
              <Link
                to="/courses/$courseId"
                params={{ courseId }}
                search={{ tab: "curriculum", section: next.id ?? "" }}
                title={next.title}
              >
                Next
                <ChevronRight className="h-3.5 w-3.5 rtl:rotate-180" />
              </Link>
            ) : (
              <span>
                Next
                <ChevronRight className="h-3.5 w-3.5 rtl:rotate-180" />
              </span>
            )}
          </Button>
        </div>
      </nav>

      <section className="card-surface p-4 sm:p-5">
        {editing ? (
          <form onSubmit={saveSection} className="space-y-3" key={sectionId}>
            <div className="space-y-1.5">
              <Label htmlFor="focus-title">Section title</Label>
              <Input
                id="focus-title"
                name="title"
                defaultValue={section.title}
                required
                autoFocus
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="focus-description">Description</Label>
              <Textarea
                id="focus-description"
                name="description"
                rows={3}
                defaultValue={section.description ?? ""}
              />
            </div>
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={updateSection.isPending}>
                {updateSection.isPending ? "Saving…" : "Save"}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(false)}>
                Cancel
              </Button>
            </div>
          </form>
        ) : (
          <div className="flex items-start gap-3">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-primary/15 text-sm font-bold text-primary">
              {index + 1}
            </span>
            <div className="min-w-0 flex-1">
              <h1 className="text-xl font-extrabold tracking-tight">{section.title}</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                {section.description ||
                  "No description. A sentence here tells students what the section covers."}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-0.5">
              <Button variant="ghost" size="sm" onClick={() => setEditing(true)} title="Edit">
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
                    onError: (err) => fail(err, "Could not delete that section."),
                  });
                }}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        )}

        <dl className="mt-4 grid grid-cols-2 gap-2 border-t pt-4 sm:grid-cols-4">
          <Stat label="Items" value={counts.total} />
          <Stat label="Lessons" value={counts.lessons} />
          <Stat label="Quizzes" value={counts.quizzes} />
          <Stat label="Assignments" value={counts.assignments} />
        </dl>
      </section>

      <section className="card-surface p-4 sm:p-5">
        <div className="flex items-center justify-between gap-2">
          <div>
            <h2 className="text-sm font-semibold">Items</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {counts.total === 0
                ? "Nothing in this section yet."
                : `Drag to reorder${counts.optional > 0 ? `, ${counts.optional} optional` : ""}.`}
            </p>
          </div>
          {!addingItem && (
            <Button size="sm" variant="outline" onClick={() => setAddingItem(true)}>
              <Plus className="h-3.5 w-3.5" />
              Add item
            </Button>
          )}
        </div>

        <div className="mt-3 space-y-3">
          {items.isLoading ? (
            <Skeleton className="h-16 w-full rounded-lg" />
          ) : rows.length === 0 ? (
            <p className="rounded-lg border border-dashed p-6 text-center text-xs text-muted-foreground">
              A lesson to teach something, a quiz to check it, an assignment to practise it.
            </p>
          ) : (
            <SortableList
              ids={ids}
              className="space-y-1.5"
              onReorder={(orderedIds) =>
                reorderItems.mutate(
                  { sectionId, orderedIds },
                  { onError: (err) => fail(err, "Could not reorder those.") },
                )
              }
            >
              {rows.map((item, position) => (
                <SortableRow key={item.id} id={item.id ?? ""}>
                  {({ handle, isDragging }) => (
                    <ItemRow
                      courseId={courseId}
                      item={item}
                      position={position}
                      handle={handle}
                      isDragging={isDragging}
                      highlighted={highlightItemId === item.id}
                    />
                  )}
                </SortableRow>
              ))}
            </SortableList>
          )}

          {addingItem && (
            <form onSubmit={submitItem} className="space-y-3 rounded-lg border bg-muted/30 p-3">
              <div className="space-y-1.5">
                <Label htmlFor="new-item-title">Item title</Label>
                <Input id="new-item-title" name="title" required autoFocus />
              </div>
              <div className="space-y-1.5">
                <Label>Type</Label>
                <Select value={itemType} onValueChange={(v) => setItemType(v as CourseItemType)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ITEM_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>
                        {itemLabel(type)}
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
          )}
        </div>
      </section>

      <section className="card-surface p-4 sm:p-5">
        <ResourcePanel
          scope="section"
          ownerId={sectionId}
          emptyHint="Nothing attached to this section. Material that belongs to the whole chapter — slides, a reading list, a repository — goes here rather than on one lesson."
        />
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-muted/40 px-3 py-2">
      <dt className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </dt>
      <dd className="text-lg font-bold leading-tight tabular-nums">{value}</dd>
    </div>
  );
}

function ItemRow({
  courseId,
  item,
  position,
  handle,
  isDragging,
  highlighted,
}: {
  courseId: string;
  item: CourseItemResponse;
  position: number;
  handle: DragHandleProps;
  isDragging: boolean;
  highlighted: boolean;
}) {
  const itemId = item.id ?? "";
  const Icon = itemIcon(item.type);
  const [editing, setEditing] = useState(false);
  const [showResources, setShowResources] = useState(false);

  const updateItem = useUpdateItemMutation(courseId);
  const deleteItem = useDeleteItemMutation(courseId);
  const isRequired = item.isRequired !== false;

  const save = (e: FormEvent) => {
    e.preventDefault();
    const data = new FormData(e.target as HTMLFormElement);
    updateItem.mutate(
      { itemId, title: String(data.get("title") ?? "").trim() },
      {
        onSuccess: () => {
          toast.success("Item renamed.");
          setEditing(false);
        },
        onError: (err) => fail(err, "Could not rename that."),
      },
    );
  };

  // Brought into view when the rail selects it: in a long section the row being
  // highlighted is often below the fold.
  const rowRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (highlighted) rowRef.current?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [highlighted]);

  return (
    <div
      ref={rowRef}
      className={cn(
        "rounded-lg border bg-card transition-colors",
        highlighted && "border-primary ring-1 ring-primary/40",
        isDragging && "border-primary/60",
      )}
    >
      <div className="flex items-center gap-2 p-2.5">
        <DragHandle handle={handle} label={`Reorder ${item.title ?? "item"}`} />
        <span className="w-4 shrink-0 text-center text-[11px] font-medium tabular-nums text-muted-foreground">
          {position + 1}
        </span>
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
              {itemLabel(item.type)}
            </Badge>

            {/*
              Whether a student may skip it, as a control rather than a label —
              it is the one property of an item that changes often, and burying
              it behind a rename form made it look like it could not be changed.
            */}
            <button
              type="button"
              disabled={updateItem.isPending}
              title={
                isRequired
                  ? "Required — counts towards completing the course. Click to make it optional."
                  : "Optional — students may skip it. Click to make it required."
              }
              onClick={() =>
                updateItem.mutate(
                  { itemId, isRequired: !isRequired },
                  {
                    onSuccess: () => toast.success(isRequired ? "Now optional." : "Now required."),
                    onError: (err) => fail(err, "Could not change that."),
                  },
                )
              }
              className={cn(
                "h-4 shrink-0 rounded border px-1 text-[9px] font-semibold uppercase transition-colors disabled:opacity-50",
                isRequired
                  ? "border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/70"
                  : "border-dashed text-muted-foreground hover:text-foreground",
              )}
            >
              {isRequired ? "Required" : "Optional"}
            </button>

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
                <Paperclip className="h-3.5 w-3.5" />
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
    </div>
  );
}
