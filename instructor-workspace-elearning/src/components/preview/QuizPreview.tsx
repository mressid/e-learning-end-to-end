import { useState } from "react";
import { Check, Clock, Eye, EyeOff, Repeat, Target } from "lucide-react";

import { useQuizQuery, useQuizQuestionsQuery } from "@/hooks/queries";
import type { AuthorQuestionResponse, QuestionType } from "@/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const TYPE_HINT: Record<QuestionType, string> = {
  SINGLE_CHOICE: "Choose one",
  MULTIPLE_CHOICE: "Choose all that apply",
  TRUE_FALSE: "Choose one",
  SHORT_TEXT: "Write a short answer",
  LONG_TEXT: "Write an answer",
};

function isChoice(type: QuestionType): boolean {
  return type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE" || type === "TRUE_FALSE";
}

/**
 * A quiz as the paper a student is handed.
 *
 * Not an attempt. Starting one needs an active enrolment and an author has
 * none, so this renders the questions rather than pretending to sit them —
 * which also keeps a second, unmarked copy of the answering flow from existing
 * beside the real one.
 *
 * The only endpoint that lists questions is the author's, and it carries the
 * answer key, so the key is withheld here rather than by the server. Nothing
 * leaks that the same person could not already read: the toggle exists because
 * someone checking their own quiz wants both views.
 */
export function QuizPreview({ courseId, itemId }: { courseId: string; itemId: string }) {
  const quiz = useQuizQuery(courseId, itemId);
  const questions = useQuizQuestionsQuery(courseId, itemId);
  const [revealed, setRevealed] = useState(false);

  if (quiz.isLoading || questions.isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-20 w-full rounded-xl" />
        <Skeleton className="h-32 w-full rounded-xl" />
      </div>
    );
  }

  if (!quiz.data) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        This quiz has not been written yet. A student would find nothing to sit.
      </p>
    );
  }

  const rows = questions.data ?? [];
  const total = rows.reduce((sum, row) => sum + (row.points ?? 0), 0);
  const minutes = quiz.data.timeLimitSeconds ? Math.round(quiz.data.timeLimitSeconds / 60) : null;

  return (
    <article className="space-y-5">
      <header className="card-surface space-y-3 p-4">
        {quiz.data.instructions && (
          <p className="text-sm leading-relaxed text-muted-foreground">{quiz.data.instructions}</p>
        )}
        <div className="flex flex-wrap gap-x-5 gap-y-1.5 text-xs text-muted-foreground">
          <Fact
            icon={Target}
            text={`${rows.length} ${rows.length === 1 ? "question" : "questions"}, ${total} ${total === 1 ? "point" : "points"}`}
          />
          {quiz.data.passingScore != null && (
            <Fact icon={Check} text={`${quiz.data.passingScore}% to pass`} />
          )}
          {minutes && <Fact icon={Clock} text={`${minutes} minutes`} />}
          <Fact
            icon={Repeat}
            text={
              quiz.data.maxAttempts != null
                ? `${quiz.data.maxAttempts} ${quiz.data.maxAttempts === 1 ? "attempt" : "attempts"}`
                : "Unlimited attempts"
            }
          />
        </div>
        {quiz.data.randomizeQuestions && (
          <p className="text-xs text-muted-foreground">
            Each student sees these in a different order. The order below is the one you wrote them
            in.
          </p>
        )}
      </header>

      {rows.length === 0 ? (
        <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
          No questions yet, so there is nothing to sit.
        </p>
      ) : (
        <>
          <div className="flex justify-end">
            <Button variant="outline" size="sm" onClick={() => setRevealed((on) => !on)}>
              {revealed ? (
                <EyeOff className="mr-1.5 h-3.5 w-3.5" />
              ) : (
                <Eye className="mr-1.5 h-3.5 w-3.5" />
              )}
              {revealed ? "Hide the answers" : "Show the answers"}
            </Button>
          </div>
          <ol className="space-y-3">
            {rows.map((question, index) => (
              <QuestionView
                key={question.id}
                question={question}
                number={index + 1}
                revealed={revealed}
              />
            ))}
          </ol>
        </>
      )}
    </article>
  );
}

function Fact({ icon: Icon, text }: { icon: typeof Clock; text: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Icon className="h-3.5 w-3.5" />
      {text}
    </span>
  );
}

function QuestionView({
  question,
  number,
  revealed,
}: {
  question: AuthorQuestionResponse;
  number: number;
  revealed: boolean;
}) {
  const type = (question.type as QuestionType | undefined) ?? "SINGLE_CHOICE";
  const points = question.points ?? 1;

  return (
    <li className="card-surface space-y-3 p-4">
      <div className="flex items-start gap-3">
        <span className="text-sm font-semibold tabular-nums text-muted-foreground">{number}.</span>
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-sm leading-relaxed">{question.text}</p>
          <p className="text-xs text-muted-foreground">
            {TYPE_HINT[type]} · {points} {points === 1 ? "point" : "points"}
          </p>
        </div>
      </div>

      {isChoice(type) ? (
        <ul className="space-y-1.5 ps-7">
          {(question.options ?? []).map((option) => {
            const correct = revealed && option.isCorrect;
            return (
              <li
                key={option.id}
                className={cn(
                  "flex items-center gap-2.5 rounded-md border p-2 text-sm",
                  correct && "border-emerald-500/50 bg-emerald-500/5",
                )}
              >
                <span
                  aria-hidden
                  className={cn(
                    "h-3.5 w-3.5 shrink-0 border",
                    type === "MULTIPLE_CHOICE" ? "rounded-sm" : "rounded-full",
                    correct ? "border-emerald-500 bg-emerald-500" : "border-muted-foreground/40",
                  )}
                />
                <span className="min-w-0 flex-1">{option.text}</span>
                {correct && (
                  <Badge
                    variant="outline"
                    className="h-4 shrink-0 border-emerald-500/50 px-1 text-[9px] uppercase tracking-wider text-emerald-600 dark:text-emerald-400"
                  >
                    Correct
                  </Badge>
                )}
              </li>
            );
          })}
        </ul>
      ) : (
        <div className="ms-7 rounded-md border border-dashed p-3">
          <p className="text-xs text-muted-foreground">
            {type === "LONG_TEXT" ? "A written answer goes here." : "A short answer goes here."}
            {" Marked by hand, so the attempt is not scored until someone marks it."}
          </p>
        </div>
      )}
    </li>
  );
}
