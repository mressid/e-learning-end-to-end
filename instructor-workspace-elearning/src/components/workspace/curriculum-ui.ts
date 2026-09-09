import { FileCheck, FileText, HelpCircle } from "lucide-react";
import { toast } from "sonner";

import { parseApiError, type CourseItemResponse, type CourseItemType } from "@/api";

/** The kinds of item a section can hold, in the order they are offered. */
export const ITEM_TYPES: CourseItemType[] = ["LESSON", "QUIZ", "ASSIGNMENT"];

/**
 * These three live here rather than in each view because the curriculum is
 * drawn in four places — the sidebar tree, the board, the focused section and
 * the lesson page — and an item that is a different icon or a different word
 * in each of them reads as four different things.
 */
export function itemIcon(type: string | undefined) {
  if (type === "QUIZ") return HelpCircle;
  if (type === "ASSIGNMENT") return FileCheck;
  return FileText;
}

/** What an item is called in prose. */
export function itemLabel(type: string | undefined): string {
  if (type === "QUIZ") return "Quiz";
  if (type === "ASSIGNMENT") return "Assignment";
  return "Lesson";
}

/** The short form, for places with a column's worth of room. */
export function itemLabelShort(type: string | undefined): string {
  if (type === "QUIZ") return "Quiz";
  if (type === "ASSIGNMENT") return "Task";
  return "Lesson";
}

/** What a section holds, counted by kind. */
export function tally(items: CourseItemResponse[] | undefined) {
  const rows = items ?? [];
  return {
    total: rows.length,
    lessons: rows.filter((item) => item.type === "LESSON").length,
    quizzes: rows.filter((item) => item.type === "QUIZ").length,
    assignments: rows.filter((item) => item.type === "ASSIGNMENT").length,
    // Absent means required: the server defaults it, and only an explicit
    // false makes an item skippable.
    optional: rows.filter((item) => item.isRequired === false).length,
  };
}

export const fail = (err: unknown, fallback: string) =>
  toast.error(parseApiError(err).message || fallback);
