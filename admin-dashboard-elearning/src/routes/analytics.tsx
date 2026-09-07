import { createFileRoute } from "@tanstack/react-router";
import {
  BarChart3,
  BookOpen,
  Users,
  Award,
  GraduationCap,
  FileText,
  HardDrive,
  Radio,
  Info,
} from "lucide-react";
import { useI18n } from "@/lib/i18n";
import {
  useCourseStatsQuery,
  useAdminUsersQuery,
  useAdminCertificatesQuery,
  useAdminInstructorsQuery,
  useAdminSubmissionsQuery,
  useAdminMediaQuery,
  useUserSessionsQuery,
  usePermissions,
} from "@/hooks/queries";
import { PERMISSIONS, type Permission } from "@/api";
import { PageHeader } from "@/components/dashboard/PageHeader";
import { PermissionGate } from "@/components/dashboard/PermissionGate";
import { StatTile } from "@/components/dashboard/StatTile";
import { cn } from "@/lib/utils";

const title = "Analytics";
const description = "What the platform actually holds.";

export const Route = createFileRoute("/analytics")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => (
    <PermissionGate permission={PERMISSIONS.COURSE_READ}>
      <AnalyticsPage />
    </PermissionGate>
  ),
});

/** A count is fetched by asking for one row and reading the page total. */
const COUNT_ONLY = { page: 0, size: 1 };

function AnalyticsPage() {
  const { t } = useI18n();
  const { has } = usePermissions();

  const canReadUsers = has(PERMISSIONS.USER_READ);
  const canReadCerts = has(PERMISSIONS.CERTIFICATE_READ);
  const canReadSubs = has(PERMISSIONS.SUBMISSION_READ);
  const canReadMedia = has(PERMISSIONS.MEDIA_READ);
  const canManageSettings = has(PERMISSIONS.SETTINGS_MANAGE);

  const courseStats = useCourseStatsQuery();
  const users = useAdminUsersQuery(COUNT_ONLY, canReadUsers);
  const suspended = useAdminUsersQuery({ ...COUNT_ONLY, status: "SUSPENDED" }, canReadUsers);
  const instructors = useAdminInstructorsQuery(COUNT_ONLY, canReadUsers);
  const certificates = useAdminCertificatesQuery(COUNT_ONLY, canReadCerts);
  const revoked = useAdminCertificatesQuery({ ...COUNT_ONLY, revoked: true }, canReadCerts);
  const submissions = useAdminSubmissionsQuery(COUNT_ONLY, canReadSubs);
  const media = useAdminMediaQuery(COUNT_ONLY, canReadMedia);
  const sessions = useUserSessionsQuery(COUNT_ONLY, canManageSettings);

  const counts = courseStats.data ?? {};
  const published = counts["PUBLISHED"] ?? 0;
  const drafts = counts["DRAFT"] ?? 0;
  const archived = counts["ARCHIVED"] ?? 0;
  const totalCourses = published + drafts + archived;

  const tiles: {
    key: string;
    label: string;
    value: number | undefined;
    hint: string;
    icon: typeof BookOpen;
    loading: boolean;
    permitted: boolean;
    permission: Permission;
  }[] = [
    {
      key: "courses",
      label: "Courses",
      value: totalCourses,
      hint: `${published} published · ${drafts} draft · ${archived} archived`,
      icon: BookOpen,
      loading: courseStats.isLoading,
      permitted: true,
      permission: PERMISSIONS.COURSE_READ,
    },
    {
      key: "learners",
      label: "Learners",
      value: users.data?.totalElements,
      hint:
        suspended.data?.totalElements != null
          ? `${suspended.data.totalElements} suspended`
          : "registered accounts",
      icon: Users,
      loading: users.isLoading,
      permitted: canReadUsers,
      permission: PERMISSIONS.USER_READ,
    },
    {
      key: "instructors",
      label: "Instructors",
      value: instructors.data?.totalElements,
      hint: "people who own at least one course",
      icon: GraduationCap,
      loading: instructors.isLoading,
      permitted: canReadUsers,
      permission: PERMISSIONS.USER_READ,
    },
    {
      key: "certificates",
      label: "Certificates",
      value: certificates.data?.totalElements,
      hint:
        revoked.data?.totalElements != null
          ? `${revoked.data.totalElements} revoked`
          : "issued on completion",
      icon: Award,
      loading: certificates.isLoading,
      permitted: canReadCerts,
      permission: PERMISSIONS.CERTIFICATE_READ,
    },
    {
      key: "submissions",
      label: "Submissions",
      value: submissions.data?.totalElements,
      hint: "assignments handed in",
      icon: FileText,
      loading: submissions.isLoading,
      permitted: canReadSubs,
      permission: PERMISSIONS.SUBMISSION_READ,
    },
    {
      key: "media",
      label: "Media files",
      value: media.data?.totalElements,
      hint: "uploaded to the library",
      icon: HardDrive,
      loading: media.isLoading,
      permitted: canReadMedia,
      permission: PERMISSIONS.MEDIA_READ,
    },
    {
      key: "sessions",
      label: "Live learner sessions",
      value: sessions.data?.totalElements,
      hint: "signed in right now",
      icon: Radio,
      loading: sessions.isLoading,
      permitted: canManageSettings,
      permission: PERMISSIONS.SETTINGS_MANAGE,
    },
  ];

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <PageHeader title={t("page.analytics.title")} description={description} />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {tiles.map(({ key, ...tile }) => (
          <StatTile key={key} {...tile} />
        ))}
      </div>

      {totalCourses > 0 && (
        <section className="card-surface p-5">
          <h2 className="text-sm font-bold">Catalogue by status</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Every course on the platform, across all owners.
          </p>
          <div className="mt-4 space-y-3">
            <StatusBar label="Published" value={published} total={totalCourses} tone="bg-primary" />
            <StatusBar
              label="Draft"
              value={drafts}
              total={totalCourses}
              tone="bg-muted-foreground/50"
            />
            <StatusBar
              label="Archived"
              value={archived}
              total={totalCourses}
              tone="bg-muted-foreground/25"
            />
          </div>
        </section>
      )}

      {/*
        Stated rather than faked. The page this replaced showed revenue, device
        splits and weekly traffic — none of which the platform records. There is
        no price, order or payment table anywhere in it, and nothing writes a
        page view. Inventing those numbers is worse than not having them.
      */}
      <section className="card-surface flex gap-3 p-5">
        <div className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
          <Info className="h-4 w-4" />
        </div>
        <div className="min-w-0 text-sm">
          <h2 className="font-bold">What is not measured</h2>
          <p className="mt-1 text-muted-foreground">
            These are counts, not engagement analytics. The platform records no revenue — there is
            no price, order or payment anywhere in it — and nothing tracks page views, watch time,
            device or retention. Those panels used to be here filled with invented figures; they
            were removed rather than re-plumbed.
          </p>
          <p className="mt-2 text-muted-foreground">
            Per-course progress <em>is</em> recorded and could support real completion analytics,
            but there is no endpoint that aggregates it across courses yet.
          </p>
        </div>
      </section>
    </div>
  );
}

function StatusBar({
  label,
  value,
  total,
  tone,
}: {
  label: string;
  value: number;
  total: number;
  tone: string;
}) {
  const pct = total === 0 ? 0 : Math.round((value / total) * 100);
  return (
    <div>
      <div className="flex items-center justify-between text-xs">
        <span className="font-medium">{label}</span>
        <span className="tabular-nums text-muted-foreground">
          {value} · {pct}%
        </span>
      </div>
      <div className="mt-1 h-2 overflow-hidden rounded-full bg-secondary">
        <div
          className={cn("h-full rounded-full transition-all", tone)}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}
