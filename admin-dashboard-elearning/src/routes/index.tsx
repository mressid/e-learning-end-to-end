import { createFileRoute, Link } from "@tanstack/react-router";
import {
  BookOpen,
  Users,
  Award,
  GraduationCap,
  Plus,
  ArrowRight,
  ScrollText,
  Radio,
} from "lucide-react";
import { useI18n } from "@/lib/i18n";
import {
  useCourseStatsQuery,
  useAdminUsersQuery,
  useAdminCertificatesQuery,
  useAdminInstructorsQuery,
  useAdminCoursesQuery,
  useAuditQuery,
  useUserSessionsQuery,
  usePermissions,
} from "@/hooks/queries";
import { PERMISSIONS } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { StatTile } from "@/components/dashboard/StatTile";
import { StatusBadge } from "@/components/dashboard/StatusBadge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatRelative } from "@/lib/format";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Dashboard — Lernova" },
      { name: "description", content: "An overview of the platform." },
      { property: "og:title", content: "Dashboard — Lernova" },
      { property: "og:description", content: "An overview of the platform." },
    ],
  }),
  component: Dashboard,
});

const COUNT_ONLY = { page: 0, size: 1 };

/*
 * This page used to open with a revenue chart, a device-split donut and a
 * weekly-traffic line, none of which the platform records — there is no price,
 * order or payment table anywhere in it, and nothing writes a page view. Worse,
 * the course table pulled *real* titles from the API and hung invented student
 * counts, watch hours and ratings off them, which is the most convincing way to
 * be wrong. All of it is gone. What is left is what the backend can answer.
 */
function Dashboard() {
  const { t } = useI18n();
  const { has } = usePermissions();

  const canReadUsers = has(PERMISSIONS.USER_READ);
  const canReadCerts = has(PERMISSIONS.CERTIFICATE_READ);
  const canReadCourses = has(PERMISSIONS.COURSE_READ);
  const canReadAudit = has(PERMISSIONS.AUDIT_READ);
  const canManageSettings = has(PERMISSIONS.SETTINGS_MANAGE);

  const courseStats = useCourseStatsQuery(canReadCourses);
  const users = useAdminUsersQuery(COUNT_ONLY, canReadUsers);
  const instructors = useAdminInstructorsQuery(COUNT_ONLY, canReadUsers);
  const certificates = useAdminCertificatesQuery(COUNT_ONLY, canReadCerts);
  const sessions = useUserSessionsQuery(COUNT_ONLY, canManageSettings);

  const recentCourses = useAdminCoursesQuery({ page: 0, size: 6 }, canReadCourses);
  const recentAudit = useAuditQuery({ page: 0, size: 6 }, canReadAudit);

  const counts = courseStats.data ?? {};
  const published = counts["PUBLISHED"] ?? 0;
  const drafts = counts["DRAFT"] ?? 0;
  const archived = counts["ARCHIVED"] ?? 0;

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader title={t("dash.title")} description="An overview of the platform.">
        {canReadCourses && (
          <Button asChild size="sm">
            <Link to="/courses">
              <Plus className="h-4 w-4" />
              {t("dash.newCourse")}
            </Link>
          </Button>
        )}
      </PageHeader>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile
          label="Published courses"
          value={published}
          hint={`${drafts} draft · ${archived} archived`}
          icon={BookOpen}
          loading={courseStats.isLoading}
          permitted={canReadCourses}
          permission={PERMISSIONS.COURSE_READ}
        />
        <StatTile
          label="Learners"
          value={users.data?.totalElements}
          hint="registered accounts"
          icon={Users}
          loading={users.isLoading}
          permitted={canReadUsers}
          permission={PERMISSIONS.USER_READ}
        />
        <StatTile
          label="Instructors"
          value={instructors.data?.totalElements}
          hint="own at least one course"
          icon={GraduationCap}
          loading={instructors.isLoading}
          permitted={canReadUsers}
          permission={PERMISSIONS.USER_READ}
        />
        <StatTile
          label={canManageSettings ? "Live sessions" : "Certificates"}
          value={
            canManageSettings ? sessions.data?.totalElements : certificates.data?.totalElements
          }
          hint={canManageSettings ? "learners signed in now" : "issued on completion"}
          icon={canManageSettings ? Radio : Award}
          loading={canManageSettings ? sessions.isLoading : certificates.isLoading}
          permitted={canManageSettings || canReadCerts}
          permission={PERMISSIONS.CERTIFICATE_READ}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <section className="card-surface p-5">
          <div className="flex items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-bold">Newest courses</h2>
              <p className="text-xs text-muted-foreground">Across every owner, drafts included.</p>
            </div>
            {canReadCourses && (
              <Button asChild variant="ghost" size="sm">
                <Link to="/courses">
                  All courses
                  <ArrowRight className="h-3.5 w-3.5 rtl:rotate-180" />
                </Link>
              </Button>
            )}
          </div>

          <div className="mt-4">
            {!canReadCourses ? (
              <NeedsPermission code={PERMISSIONS.COURSE_READ} />
            ) : recentCourses.isLoading ? (
              <RowSkeletons />
            ) : (recentCourses.data?.content?.length ?? 0) === 0 ? (
              <Empty icon={BookOpen} text="No courses yet." />
            ) : (
              <ul className="divide-y">
                {recentCourses.data?.content?.map((course) => (
                  <li key={course.id} className="flex items-center gap-3 py-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{course.title}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {course.ownerName || course.ownerEmail || "Unknown owner"} ·{" "}
                        {course.level?.toLowerCase()}
                      </p>
                    </div>
                    <StatusBadge status={course.status} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <section className="card-surface p-5">
          <div className="flex items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-bold">Recent activity</h2>
              <p className="text-xs text-muted-foreground">
                From the audit trail. Read-only by design.
              </p>
            </div>
          </div>

          <div className="mt-4">
            {!canReadAudit ? (
              <NeedsPermission code={PERMISSIONS.AUDIT_READ} />
            ) : recentAudit.isLoading ? (
              <RowSkeletons />
            ) : (recentAudit.data?.content?.length ?? 0) === 0 ? (
              <Empty icon={ScrollText} text="Nothing recorded yet." />
            ) : (
              <ul className="divide-y">
                {recentAudit.data?.content?.map((entry) => (
                  <li key={entry.id} className="py-2.5">
                    <p className="text-sm">{entry.summary}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {entry.actorLabel || entry.actorType} · {formatRelative(entry.occurredAt)}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function RowSkeletons() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}

function Empty({ icon: Icon, text }: { icon: typeof BookOpen; text: string }) {
  return (
    <div className="grid place-items-center py-10 text-center">
      <Icon className="h-6 w-6 text-muted-foreground/60" />
      <p className="mt-2 text-sm text-muted-foreground">{text}</p>
    </div>
  );
}

function NeedsPermission({ code }: { code: string }) {
  return (
    <p className="py-10 text-center text-sm text-muted-foreground">
      Needs the <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">{code}</code>{" "}
      permission.
    </p>
  );
}
