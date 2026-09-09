import type { ReactNode } from "react";
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { restrictToParentElement, restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";

import { cn } from "@/lib/utils";

type SortableBinding = ReturnType<typeof useSortable>;

/**
 * What a row hands to its handle.
 *
 * Only the handle is draggable, never the whole row: rows here carry buttons,
 * links and text inputs, and a row-wide drag would swallow every one of them.
 */
export interface DragHandleProps {
  ref: SortableBinding["setActivatorNodeRef"];
  attributes: SortableBinding["attributes"];
  listeners: SortableBinding["listeners"];
}

/**
 * A vertical list whose rows can be dragged into a different order.
 *
 * `onReorder` is given the complete sequence, which is also what the reorder
 * endpoints want — they reject partial orders — so nothing has to translate
 * between a move and a request.
 *
 * Movement is pinned to the vertical axis and to the container: these lists
 * live beside other lists, and a row that can be dragged anywhere on the page
 * suggests it can be dropped there.
 */
export function SortableList({
  ids,
  onReorder,
  className,
  children,
}: {
  ids: string[];
  onReorder: (orderedIds: string[]) => void;
  className?: string;
  children: ReactNode;
}) {
  const sensors = useSensors(
    // A few pixels of travel before a drag begins, so that pressing a button
    // inside a row is still a press and not a one-pixel drag.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    // Space picks a row up, the arrows move it, space drops it. dnd-kit
    // announces each step to a screen reader on its own.
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const from = ids.indexOf(String(active.id));
    const to = ids.indexOf(String(over.id));
    if (from < 0 || to < 0) return;
    onReorder(arrayMove(ids, from, to));
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      modifiers={[restrictToVerticalAxis, restrictToParentElement]}
      onDragEnd={handleDragEnd}
    >
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>
        <div className={className}>{children}</div>
      </SortableContext>
    </DndContext>
  );
}

/**
 * One row of a `SortableList`.
 *
 * Renders the wrapper itself — it owns the transform that animates the row out
 * of the way of the one being dragged — and hands the handle bindings down.
 */
export function SortableRow({
  id,
  className,
  children,
}: {
  id: string;
  className?: string;
  children: (drag: { handle: DragHandleProps; isDragging: boolean }) => ReactNode;
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id });

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn(className, isDragging && "relative z-20 shadow-lg")}
    >
      {children({
        handle: { ref: setActivatorNodeRef, attributes, listeners },
        isDragging,
      })}
    </div>
  );
}

/** The grip. Give it the `handle` a `SortableRow` passed you. */
export function DragHandle({
  handle,
  label,
  className,
}: {
  handle: DragHandleProps;
  label: string;
  className?: string;
}) {
  return (
    <button
      type="button"
      ref={handle.ref}
      {...handle.attributes}
      {...handle.listeners}
      aria-label={label}
      title={label}
      className={cn(
        // `touch-none` is not decoration: without it the browser claims the
        // gesture for scrolling and the row never moves on a touchscreen.
        "grid h-7 w-6 shrink-0 cursor-grab touch-none place-items-center rounded-md text-muted-foreground/50",
        "transition-colors hover:bg-secondary hover:text-foreground active:cursor-grabbing",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        className,
      )}
    >
      <GripVertical className="h-4 w-4" />
    </button>
  );
}
