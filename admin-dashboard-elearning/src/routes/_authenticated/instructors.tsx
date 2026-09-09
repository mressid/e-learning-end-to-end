import { useCallback, useRef } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Plus } from "lucide-react";
import { usePermissions } from "@/hooks/queries";
import { PERMISSIONS } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { InstructorsPanel } from "@/components/people/InstructorsPanel";
import { NotAllowed } from "@/components/people/NotAllowed";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * The instructors.
 *
 * Its own page for the same reason [students] is: an instructor account and a
 * learner account are different kinds of thing, and the two lists can never
 * overlap. Reading the roster needs `user.read` like any directory; creating one
 * needs `user.write`, because unlike a learner an instructor only exists if an
 * administrator makes it.
 */
export const Route = createFileRoute("/_authenticated/instructors")({
  head: () => ({
    meta: [
      { title: "Instructors — Lernova" },
      {
        name: "description",
        content: "People who author courses. A separate kind of account from a learner.",
      },
    ],
  }),
  component: InstructorsPage,
});

function InstructorsPage() {
  const { has, isReady } = usePermissions();
  const canRead = has(PERMISSIONS.USER_READ);
  const canWrite = has(PERMISSIONS.USER_WRITE);

  // The panel owns the sheet — it knows what it is creating — while the button
  // belongs up here beside the title. The panel hands its opener out on mount.
  const openAdd = useRef<(() => void) | null>(null);
  const registerAdd = useCallback((open: () => void) => {
    openAdd.current = open;
  }, []);

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader
        title="Instructors"
        description="People who author courses. A separate kind of account from a learner."
      >
        {isReady && canRead && canWrite && (
          <Button size="sm" onClick={() => openAdd.current?.()}>
            <Plus className="h-4 w-4" />
            Add instructor
          </Button>
        )}
      </PageHeader>

      {!isReady ? (
        <div className="space-y-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      ) : !canRead ? (
        <NotAllowed permission={PERMISSIONS.USER_READ} />
      ) : (
        <InstructorsPanel onAdd={registerAdd} />
      )}
    </div>
  );
}
