import { createFileRoute } from "@tanstack/react-router";
import { usePermissions } from "@/hooks/queries";
import { PERMISSIONS } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { LearnersPanel } from "@/components/people/LearnersPanel";
import { NotAllowed } from "@/components/people/NotAllowed";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * The learners.
 *
 * A page of its own again, rather than one view of a combined People workspace.
 * Merging them made sense while a learner and an instructor were the same row
 * with a flag between them — you went to one place to ask "who is this person".
 * They are separate kinds of account now: separate tables, separate endpoints,
 * and no way to turn one into the other. Two lists that can never contain the
 * same person are two pages.
 *
 * There is no "add" action here on purpose. Learners register themselves; an
 * administrator creating one is the unusual case, and it lives in the API rather
 * than being offered as the obvious thing to do on this screen.
 */
export const Route = createFileRoute("/_authenticated/students")({
  head: () => ({
    meta: [
      { title: "Learners — Lernova" },
      { name: "description", content: "Everyone who takes courses, and what to do about them." },
    ],
  }),
  component: StudentsPage,
});

function StudentsPage() {
  const { has, isReady } = usePermissions();

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader
        title="Learners"
        description="Everyone who takes courses, and what to do about them."
      />

      {!isReady ? (
        <div className="space-y-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      ) : !has(PERMISSIONS.USER_READ) ? (
        <NotAllowed permission={PERMISSIONS.USER_READ} />
      ) : (
        <LearnersPanel />
      )}
    </div>
  );
}
