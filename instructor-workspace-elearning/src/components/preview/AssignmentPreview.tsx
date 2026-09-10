import { CalendarClock, Target } from "lucide-react";

import { useAssignmentQuery } from "@/hooks/queries";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * An assignment's brief, as a student reads it.
 *
 * Read only in every sense: authoring an assignment has no screen yet, and
 * handing work in needs an enrolment. It is here because a preview that quietly
 * skipped assignments would tell the author their course is shorter than it is.
 */
export function AssignmentPreview({ courseId, itemId }: { courseId: string; itemId: string }) {
  const assignment = useAssignmentQuery(courseId, itemId);

  if (assignment.isLoading) return <Skeleton className="h-40 w-full rounded-xl" />;

  if (!assignment.data) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        No brief has been written for this assignment yet. Authoring one has no screen so far, so it
        can only be written through the API.
      </p>
    );
  }

  const { instructions, dueAt, maxScore, allowLateSubmission } = assignment.data;

  return (
    <article className="space-y-4">
      <div className="flex flex-wrap gap-x-5 gap-y-1.5 text-xs text-muted-foreground">
        {maxScore != null && (
          <span className="inline-flex items-center gap-1.5">
            <Target className="h-3.5 w-3.5" />
            Out of {maxScore}
          </span>
        )}
        {dueAt && (
          <span className="inline-flex items-center gap-1.5">
            <CalendarClock className="h-3.5 w-3.5" />
            Due {new Date(dueAt).toLocaleString()}
            {allowLateSubmission ? " · late work accepted" : ""}
          </span>
        )}
      </div>

      {instructions ? (
        <p className="whitespace-pre-wrap text-sm leading-relaxed">{instructions}</p>
      ) : (
        <p className="text-sm text-muted-foreground">This assignment has no instructions yet.</p>
      )}
    </article>
  );
}
