import { useState } from "react";
import {
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CircleDot,
  ListChecks,
  PenLine,
  Plus,
  Trash2,
  Type,
} from "lucide-react";
import { toast } from "sonner";

import {
  useAddQuestionMutation,
  useDeleteQuestionMutation,
  useQuizQuestionsQuery,
  useReorderQuestionsMutation,
  useUpdateQuestionMutation,
} from "@/hooks/queries";
import { parseApiError, type AuthorQuestionResponse, type QuestionType } from "@/api";
import {
  DragHandle,
  SortableList,
  SortableRow,
  type DragHandleProps,
} from "@/components/workspace/Sortable";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
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

const TYPE_LABEL: Record<QuestionType, string> = {
  SINGLE_CHOICE: "One answer",
  MULTIPLE_CHOICE: "Several answers",
  TRUE_FALSE: "True or false",
  SHORT_TEXT: "Short answer",
  LONG_TEXT: "Long answer",
};

const TYPE_ICON: Record<QuestionType, typeof CircleDot> = {
  SINGLE_CHOICE: CircleDot,
  MULTIPLE_CHOICE: ListChecks,
  TRUE_FALSE: CheckCircle2,
  SHORT_TEXT: Type,
  LONG_TEXT: PenLine,
};

/**
 * One option while it is being edited.
 *
 * The generated request type leaves `isCorrect` optional, which is right on the
 * wire and wrong in a form: a checkbox is on or off, never absent. A required
 * boolean here is still assignable to the request when it is sent.
 */
type OptionDraft = { text: string; isCorrect: boolean };

/** Which types the machine can mark. Free text waits for a human, and carries no options. */
function isChoice(type: QuestionType): boolean {
  return type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE" || type === "TRUE_FALSE";
}

/**
 * What a new question of each type starts as.
 *
 * A choice question is refused without two options and exactly the right number
 * of correct ones, so adding one cannot mean adding an empty shell — it would
 * be a 422 before the author had typed anything. These are valid the moment
 * they land, and the author edits them in place.
 */
function starter(type: QuestionType): { text: string; options: OptionDraft[] } {
  if (type === "TRUE_FALSE") {
    return {
      text: "New question",
      options: [
        { text: "True", isCorrect: true },
        { text: "False", isCorrect: false },
      ],
    };
  }
  if (isChoice(type)) {
    return {
      text: "New question",
      options: [
        { text: "First option", isCorrect: true },
        { text: "Second option", isCorrect: false },
      ],
    };
  }
  return { text: "New question", options: [] };
}

function fail(err: unknown, fallback: string) {
  toast.error(parseApiError(err).message || fallback);
}

/**
 * The ordered questions a quiz is made of.
 *
 * Add at the bottom, edit in place, drag to reorder, delete. Once a student has
 * sat the quiz the paper freezes, and every control that would change its shape
 * goes with it — the server refuses those edits either way, and a control that
 * is plainly unavailable beside an explanation is a better way to learn the
 * rule than a save that comes back rejected.
 *
 * Reordering is deliberately still allowed. It changes the order questions are
 * read in, not what the paper asks or what it is marked out of.
 */
export function QuizQuestions({
  courseId,
  itemId,
  hasAttempts,
}: {
  courseId: string;
  itemId: string;
  /** Once true the answer key is frozen, so the controls that touch it are disabled. */
  hasAttempts: boolean;
}) {
  const { data, isLoading, isError, error, refetch } = useQuizQuestionsQuery(courseId, itemId);
  const reorder = useReorderQuestionsMutation(courseId, itemId);

  // Held here rather than in each card so one control can fold the lot. An
  // expanded question is a text area and its whole answer key; folded, the list
  // is the shape of the paper.
  const [folded, setFolded] = useState<ReadonlySet<string>>(new Set());

  const rows = data ?? [];
  const ids = rows.map((row) => row.id ?? "").filter(Boolean);
  const allFolded = ids.length > 0 && ids.every((id) => folded.has(id));
  const total = rows.reduce((sum, row) => sum + (row.points ?? 0), 0);

  const toggle = (id: string) =>
    setFolded((current) => {
      const next = new Set(current);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">
          Questions
          {rows.length > 0 && (
            <span className="ml-2 text-xs font-normal text-muted-foreground">
              {rows.length} for {total} {total === 1 ? "point" : "points"}
            </span>
          )}
        </h3>
        {ids.length > 1 && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-xs"
            onClick={() => setFolded(allFolded ? new Set() : new Set(ids))}
          >
            {allFolded ? "Expand all" : "Collapse all"}
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-16 w-full rounded-lg" />
          <Skeleton className="h-16 w-full rounded-lg" />
        </div>
      ) : isError ? (
        <div className="rounded-lg border border-dashed p-4 text-center">
          <p className="text-sm text-muted-foreground">
            {parseApiError(error).message || "Could not load this quiz's questions."}
          </p>
          <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <p className="rounded-lg border border-dashed p-6 text-center text-xs text-muted-foreground">
          {hasAttempts
            ? "This quiz has no questions, and a student has already sat it, so none can be added now."
            : "No questions yet. Pick a kind below and the first one appears here, ready to be written over."}
        </p>
      ) : (
        <SortableList
          ids={ids}
          className="space-y-2"
          onReorder={(orderedIds) =>
            reorder.mutate(orderedIds, {
              onError: (err) => fail(err, "Could not reorder those questions."),
            })
          }
        >
          {rows.map((row, position) => {
            const questionId = row.id ?? "";
            return (
              <SortableRow key={questionId} id={questionId}>
                {({ handle, isDragging }) => (
                  <QuestionCard
                    courseId={courseId}
                    itemId={itemId}
                    hasAttempts={hasAttempts}
                    question={row}
                    position={position}
                    handle={handle}
                    isDragging={isDragging}
                    collapsed={folded.has(questionId)}
                    onToggleCollapsed={() => toggle(questionId)}
                  />
                )}
              </SortableRow>
            );
          })}
        </SortableList>
      )}

      {!hasAttempts && <AddQuestionBar courseId={courseId} itemId={itemId} />}
    </div>
  );
}

function QuestionCard({
  courseId,
  itemId,
  hasAttempts,
  question,
  position,
  handle,
  isDragging,
  collapsed,
  onToggleCollapsed,
}: {
  courseId: string;
  itemId: string;
  hasAttempts: boolean;
  question: AuthorQuestionResponse;
  position: number;
  handle: DragHandleProps;
  isDragging: boolean;
  collapsed: boolean;
  onToggleCollapsed: () => void;
}) {
  const update = useUpdateQuestionMutation(courseId, itemId);
  const remove = useDeleteQuestionMutation(courseId, itemId);

  const type = (question.type as QuestionType | undefined) ?? "SINGLE_CHOICE";
  const savedText = question.text ?? "";
  const savedPoints = String(question.points ?? 1);
  const savedOptions: OptionDraft[] = (question.options ?? []).map((option) => ({
    text: option.text ?? "",
    isCorrect: option.isCorrect ?? false,
  }));

  const [text, setText] = useState(savedText);
  const [points, setPoints] = useState(savedPoints);
  const [options, setOptions] = useState<OptionDraft[]>(savedOptions);
  const [confirmOpen, setConfirmOpen] = useState(false);

  if (!question.id) return null;
  const questionId = question.id;
  const fieldsId = `question-fields-${questionId}`;
  const Icon = TYPE_ICON[type];

  const optionsChanged =
    options.length !== savedOptions.length ||
    options.some(
      (option, index) =>
        option.text !== savedOptions[index]?.text ||
        option.isCorrect !== savedOptions[index]?.isCorrect,
    );
  const isDirty = text !== savedText || points !== savedPoints || optionsChanged;

  // The same rules the server enforces, checked here so an unanswerable
  // question is a disabled button rather than a round trip that comes back 422.
  const correctCount = options.filter((option) => option.isCorrect).length;
  const optionsValid =
    !isChoice(type) ||
    (options.length >= 2 &&
      options.every((option) => option.text.trim().length > 0) &&
      correctCount >= 1 &&
      (type === "MULTIPLE_CHOICE" || correctCount === 1));
  const pointsValid = points.trim() !== "" && Number(points) >= 0 && !Number.isNaN(Number(points));
  const canSave =
    isDirty && optionsValid && pointsValid && text.trim().length > 0 && !update.isPending;

  const save = () => {
    if (!canSave) return;
    update.mutate(
      {
        questionId,
        text: text.trim(),
        points: Number(points),
        // Sent only when they actually changed. The server refuses an answer
        // key edit once the quiz has been attempted, and a fixed typo should
        // not be refused along with options nobody touched.
        ...(optionsChanged ? { options } : {}),
      },
      {
        onSuccess: () => toast.success("Saved."),
        onError: (err) => fail(err, "Could not save that question."),
      },
    );
  };

  const setOption = (index: number, patch: Partial<OptionDraft>) =>
    setOptions((current) =>
      current.map((option, i) => (i === index ? { ...option, ...patch } : option)),
    );

  /** Single-answer types hold exactly one correct option, so picking is moving the mark. */
  const markCorrect = (index: number, checked: boolean) =>
    setOptions((current) =>
      current.map((option, i) => ({
        ...option,
        isCorrect:
          type === "MULTIPLE_CHOICE" ? (i === index ? checked : option.isCorrect) : i === index,
      })),
    );

  return (
    <div
      className={cn(
        "rounded-lg border bg-card p-3",
        isDragging && "relative border-primary/60 shadow-lg",
      )}
    >
      <div className="flex items-start gap-2.5">
        <DragHandle handle={handle} label={`Reorder question ${position + 1}`} />
        <span
          aria-hidden
          className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-md bg-secondary text-muted-foreground"
        >
          <Icon className="h-3.5 w-3.5" />
        </span>

        <div className="min-w-0 flex-1 space-y-2.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="w-4 shrink-0 text-center text-[11px] font-medium tabular-nums text-muted-foreground">
              {position + 1}
            </span>
            <Badge
              variant="outline"
              className="h-4 shrink-0 px-1 text-[9px] uppercase tracking-wider"
            >
              {TYPE_LABEL[type]}
            </Badge>
            {collapsed ? (
              <p className="min-w-0 flex-1 truncate text-sm">{savedText || "Untitled question"}</p>
            ) : (
              <span className="min-w-0 flex-1" />
            )}
            <div className="ml-auto flex shrink-0 items-center gap-1">
              {!collapsed && canSave && (
                <Button type="button" size="sm" className="h-7 px-2 text-xs" onClick={save}>
                  Save
                </Button>
              )}
              {!hasAttempts && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7 text-muted-foreground hover:text-destructive"
                  aria-label="Delete this question"
                  onClick={() => setConfirmOpen(true)}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              )}
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7 text-muted-foreground"
                aria-expanded={!collapsed}
                aria-controls={fieldsId}
                aria-label={collapsed ? "Expand this question" : "Collapse this question"}
                onClick={onToggleCollapsed}
              >
                {collapsed ? (
                  <ChevronRight className="h-4 w-4" />
                ) : (
                  <ChevronDown className="h-4 w-4" />
                )}
              </Button>
            </div>
          </div>

          <div id={fieldsId} className={cn("space-y-2.5", collapsed && "hidden")}>
            <Textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="What are you asking?"
              rows={2}
              className="text-sm"
            />

            <div className="flex items-center gap-2">
              <Label htmlFor={`points-${questionId}`} className="text-xs text-muted-foreground">
                Points
              </Label>
              <Input
                id={`points-${questionId}`}
                type="number"
                min={0}
                step="0.5"
                value={points}
                onChange={(e) => setPoints(e.target.value)}
                className="h-7 w-20 text-sm"
              />
              {!isChoice(type) && (
                <p className="text-xs text-muted-foreground">
                  Marked by hand. A quiz holding one of these is not scored until someone does.
                </p>
              )}
            </div>

            {isChoice(type) && (
              <div className="space-y-1.5">
                {options.map((option, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <Checkbox
                      checked={option.isCorrect}
                      onCheckedChange={(checked) => markCorrect(index, checked === true)}
                      aria-label={`Option ${index + 1} is correct`}
                      disabled={hasAttempts}
                      className={cn(type !== "MULTIPLE_CHOICE" && "rounded-full")}
                    />
                    <Input
                      value={option.text}
                      onChange={(e) => setOption(index, { text: e.target.value })}
                      placeholder={`Option ${index + 1}`}
                      // True or false is the two answers it is named after.
                      // Renaming them would make it a single-choice question
                      // wearing the wrong label.
                      disabled={type === "TRUE_FALSE" || hasAttempts}
                      className="h-7 flex-1 text-sm"
                    />
                    {type !== "TRUE_FALSE" && !hasAttempts && options.length > 2 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7 text-muted-foreground hover:text-destructive"
                        aria-label={`Remove option ${index + 1}`}
                        onClick={() =>
                          setOptions((current) => current.filter((_, i) => i !== index))
                        }
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    )}
                  </div>
                ))}
                {type !== "TRUE_FALSE" && !hasAttempts && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs"
                    onClick={() =>
                      setOptions((current) => [...current, { text: "", isCorrect: false }])
                    }
                  >
                    <Plus className="mr-1 h-3 w-3" />
                    Add option
                  </Button>
                )}
                {!optionsValid && (
                  <p className="text-xs text-destructive">
                    {correctCount === 0
                      ? "Mark the correct answer, or nobody can get this right."
                      : type !== "MULTIPLE_CHOICE" && correctCount > 1
                        ? "Only one answer can be correct on this kind of question."
                        : "Every option needs some text, and there must be at least two."}
                  </p>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this question?</AlertDialogTitle>
            <AlertDialogDescription>
              It goes for good, and the questions after it move up.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep it</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                remove.mutate(questionId, {
                  onSuccess: () => toast.success("Question deleted."),
                  onError: (err) => fail(err, "Could not delete that question."),
                });
                setConfirmOpen(false);
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

const ADD_TYPES: QuestionType[] = [
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "SHORT_TEXT",
  "LONG_TEXT",
];

function AddQuestionBar({ courseId, itemId }: { courseId: string; itemId: string }) {
  const add = useAddQuestionMutation(courseId, itemId);

  return (
    <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-dashed p-2">
      <span className="px-1 text-xs text-muted-foreground">Add</span>
      {ADD_TYPES.map((type) => {
        const Icon = TYPE_ICON[type];
        return (
          <Button
            key={type}
            type="button"
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-xs"
            disabled={add.isPending}
            onClick={() =>
              add.mutate(
                { type, points: 1, ...starter(type) },
                { onError: (err) => fail(err, "Could not add that question.") },
              )
            }
          >
            <Icon className="mr-1 h-3 w-3" />
            {TYPE_LABEL[type]}
          </Button>
        );
      })}
    </div>
  );
}
