import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { GraduationCap } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { useAdminInstructorsQuery } from "@/hooks/queries";
import { PERMISSIONS, parseApiError } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { PermissionGate } from "@/components/dashboard/PermissionGate";
import { Pager } from "@/components/dashboard/Pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";

const title = "Instructors";
const description = "People who own courses on the platform.";

export const Route = createFileRoute("/instructors")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => (
    <PermissionGate permission={PERMISSIONS.USER_READ}>
      <InstructorsPage />
    </PermissionGate>
  ),
});

const PAGE_SIZE = 20;

function InstructorsPage() {
  const { t } = useI18n();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error, refetch } = useAdminInstructorsQuery({
    page,
    size: PAGE_SIZE,
  });

  const rows = data?.content ?? [];

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader title={t("page.instructors.title")} description={t("page.instructors.desc")} />

      <section className="card-surface p-4 sm:p-5">
        {/*
          There is no instructor role to enumerate, and so nothing to add or
          remove here. The backend derives this list from course ownership and
          orders it busiest first — someone joins it by being given a course.
        */}
        <p className="mb-4 text-xs text-muted-foreground">
          Derived from course ownership, busiest first. There is no instructor role to grant —
          someone appears here once they own a course.
        </p>

        <div className="overflow-x-auto">
          {isLoading ? (
            <div className="space-y-3 py-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <Skeleton className="h-8 w-8 shrink-0 rounded-full" />
                  <Skeleton className="h-4 flex-1" />
                  <Skeleton className="h-4 w-24" />
                </div>
              ))}
            </div>
          ) : isError ? (
            <div className="grid place-items-center py-14 text-center">
              <div className="max-w-sm">
                <p className="font-semibold">Could not load instructors</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {parseApiError(error).message || "The server did not respond."}
                </p>
                <Button variant="outline" size="sm" className="mt-3" onClick={() => refetch()}>
                  Try again
                </Button>
              </div>
            </div>
          ) : rows.length === 0 ? (
            <div className="grid place-items-center py-14 text-center">
              <div className="max-w-xs">
                <div className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-muted text-muted-foreground">
                  <GraduationCap className="h-5 w-5" />
                </div>
                <p className="mt-3 font-semibold">No instructors yet</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  Anyone who owns a course appears here automatically.
                </p>
              </div>
            </div>
          ) : (
            <table className="w-full min-w-[38rem] text-sm">
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wider text-muted-foreground rtl:text-right">
                  <th className="pb-2 pr-3 font-semibold">Instructor</th>
                  <th className="pb-2 pr-3 font-semibold">Courses</th>
                  <th className="pb-2 font-semibold">Published</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {rows.map((person) => {
                  const drafts = (person.courseCount ?? 0) - (person.publishedCourseCount ?? 0);
                  return (
                    <tr key={person.id}>
                      <td className="py-2.5 pr-3">
                        <div className="flex items-center gap-2.5">
                          <span
                            aria-hidden
                            className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-primary/10 text-[11px] font-bold text-primary"
                          >
                            {(person.displayName || person.username || "?")
                              .slice(0, 2)
                              .toUpperCase()}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate font-medium">
                              {person.displayName || person.username}
                            </p>
                            <p className="truncate text-xs text-muted-foreground">{person.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="py-2.5 pr-3 font-semibold tabular-nums">
                        {person.courseCount ?? 0}
                      </td>
                      <td className="py-2.5">
                        <div className="flex items-center gap-1.5">
                          <Badge variant="default" className="h-5 px-1.5 text-[10px] font-semibold">
                            {person.publishedCourseCount ?? 0} live
                          </Badge>
                          {drafts > 0 && (
                            <Badge
                              variant="secondary"
                              className="h-5 px-1.5 text-[10px] font-semibold"
                            >
                              {drafts} unpublished
                            </Badge>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        <div className="mt-4">
          <Pager
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 0}
            totalElements={data?.totalElements ?? 0}
            onChange={setPage}
            noun="instructor"
          />
        </div>
      </section>
    </div>
  );
}
