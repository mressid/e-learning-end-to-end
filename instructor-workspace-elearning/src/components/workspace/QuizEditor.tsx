import { useState } from "react";
import { toast } from "sonner";

import { useQuizQuery, useSaveQuizMutation } from "@/hooks/queries";
import { parseApiError, type QuizResponse, type SaveQuizRequest } from "@/api";
import { QuizQuestions } from "@/components/workspace/QuizQuestions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";

/** Minutes on screen, seconds on the wire. Nobody sets a time limit in seconds. */
function toMinutes(seconds: number | null | undefined): string {
  return seconds ? String(Math.round(seconds / 60)) : "";
}

/** Blank means "no limit", which the API spells as an absent field rather than a zero. */
function fromInput(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function fail(err: unknown, fallback: string) {
  toast.error(parseApiError(err).message || fallback);
}

/**
 * The whole editor for a QUIZ course item: its settings, then its questions.
 *
 * A QUIZ item exists in the sequence before it holds a quiz, so the settings
 * form doubles as the thing that creates one. Questions cannot be added until
 * it does — they hang off the quiz, not off the item — which is why the list
 * below only appears once the quiz has been saved.
 */
export function QuizEditor({
  courseId,
  itemId,
  itemTitle,
}: {
  courseId: string;
  itemId: string;
  itemTitle: string;
}) {
  const { data, isLoading, isError, error, refetch } = useQuizQuery(courseId, itemId);

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-40 w-full rounded-lg" />
        <Skeleton className="h-16 w-full rounded-lg" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-lg border border-dashed p-6 text-center">
        <p className="text-sm text-muted-foreground">
          {parseApiError(error).message || "Could not load this quiz."}
        </p>
        <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
          Try again
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <QuizSettings
        courseId={courseId}
        itemId={itemId}
        itemTitle={itemTitle}
        quiz={data ?? null}
        // A saved quiz and a blank one are different forms, not the same form
        // told to forget: every field below is seeded from what was loaded.
        key={data ? "saved" : "new"}
      />
      {data?.hasAttempts && (
        <p className="rounded-lg border border-amber-500/40 bg-amber-500/5 p-3 text-xs text-muted-foreground">
          Students have already sat this quiz, so the paper is fixed: no question can be added or
          removed, and none of the answers can be changed. A score is a percentage of the paper it
          was earned on, and reopening any of that would mark two students by different rules.
          Wording, points and the settings above are still yours to correct.
        </p>
      )}
      {data ? (
        <QuizQuestions
          courseId={courseId}
          itemId={itemId}
          hasAttempts={data.hasAttempts ?? false}
        />
      ) : (
        <p className="rounded-lg border border-dashed p-6 text-center text-xs text-muted-foreground">
          Questions can be added once the quiz itself exists. Save the settings above first.
        </p>
      )}
    </div>
  );
}

function QuizSettings({
  courseId,
  itemId,
  itemTitle,
  quiz,
}: {
  courseId: string;
  itemId: string;
  itemTitle: string;
  quiz: QuizResponse | null;
}) {
  const save = useSaveQuizMutation(courseId, itemId);

  // A new quiz borrows the item's own title, which the author has already
  // typed once in the curriculum and should not have to type again.
  const [title, setTitle] = useState(quiz?.title ?? itemTitle);
  const [instructions, setInstructions] = useState(quiz?.instructions ?? "");
  const [passingScore, setPassingScore] = useState(
    quiz?.passingScore != null ? String(quiz.passingScore) : "",
  );
  const [maxAttempts, setMaxAttempts] = useState(
    quiz?.maxAttempts != null ? String(quiz.maxAttempts) : "",
  );
  const [timeLimit, setTimeLimit] = useState(toMinutes(quiz?.timeLimitSeconds));
  const [randomize, setRandomize] = useState(quiz?.randomizeQuestions ?? false);

  const scoreValue = fromInput(passingScore);
  const scoreValid =
    !passingScore.trim() || (scoreValue != null && scoreValue >= 0 && scoreValue <= 100);
  const attemptsValue = fromInput(maxAttempts);
  const attemptsValid = !maxAttempts.trim() || (attemptsValue != null && attemptsValue >= 1);
  const minutesValue = fromInput(timeLimit);
  const minutesValid = !timeLimit.trim() || (minutesValue != null && minutesValue >= 1);

  const canSave =
    title.trim().length > 0 && scoreValid && attemptsValid && minutesValid && !save.isPending;

  const submit = () => {
    if (!canSave) return;
    // A PUT replaces the whole quiz, so every field goes every time. Sending
    // only what changed would clear the rest.
    const body: SaveQuizRequest = {
      title: title.trim(),
      instructions: instructions.trim() || null,
      passingScore: scoreValue,
      maxAttempts: attemptsValue,
      timeLimitSeconds: minutesValue != null ? minutesValue * 60 : null,
      randomizeQuestions: randomize,
    };
    save.mutate(body, {
      onSuccess: () => toast.success(quiz ? "Settings saved." : "Quiz created."),
      onError: (err) => fail(err, "Could not save these settings."),
    });
  };

  return (
    <div className="card-surface space-y-3 p-4">
      <div className="space-y-1.5">
        <Label htmlFor="quiz-title" className="text-xs text-muted-foreground">
          Title
        </Label>
        <Input
          id="quiz-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="What this quiz is called"
          className="h-8 text-sm"
        />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="quiz-instructions" className="text-xs text-muted-foreground">
          Instructions
        </Label>
        <Textarea
          id="quiz-instructions"
          value={instructions}
          onChange={(e) => setInstructions(e.target.value)}
          placeholder="Anything the student should read before starting"
          rows={2}
          className="text-sm"
        />
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="quiz-passing" className="text-xs text-muted-foreground">
            Pass mark
          </Label>
          <Input
            id="quiz-passing"
            type="number"
            min={0}
            max={100}
            value={passingScore}
            onChange={(e) => setPassingScore(e.target.value)}
            placeholder="Any score"
            className="h-8 text-sm"
            aria-invalid={!scoreValid}
          />
          <p className="text-[11px] text-muted-foreground">
            Percent. Blank means any score passes.
          </p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="quiz-attempts" className="text-xs text-muted-foreground">
            Attempts
          </Label>
          <Input
            id="quiz-attempts"
            type="number"
            min={1}
            value={maxAttempts}
            onChange={(e) => setMaxAttempts(e.target.value)}
            placeholder="Unlimited"
            className="h-8 text-sm"
            aria-invalid={!attemptsValid}
          />
          <p className="text-[11px] text-muted-foreground">Blank means as many as they like.</p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="quiz-time" className="text-xs text-muted-foreground">
            Time limit
          </Label>
          <Input
            id="quiz-time"
            type="number"
            min={1}
            value={timeLimit}
            onChange={(e) => setTimeLimit(e.target.value)}
            placeholder="None"
            className="h-8 text-sm"
            aria-invalid={!minutesValid}
          />
          <p className="text-[11px] text-muted-foreground">
            Minutes, counted from when they start.
          </p>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 rounded-md border p-2.5">
        <div className="min-w-0">
          <Label htmlFor="quiz-randomize" className="text-sm">
            Shuffle the questions
          </Label>
          <p className="text-[11px] text-muted-foreground">
            Each student sees them in a different order. The options within a question are not
            shuffled.
          </p>
        </div>
        <Switch id="quiz-randomize" checked={randomize} onCheckedChange={setRandomize} />
      </div>

      <div className="flex justify-end">
        <Button type="button" size="sm" disabled={!canSave} onClick={submit}>
          {quiz ? "Save settings" : "Create quiz"}
        </Button>
      </div>
    </div>
  );
}
