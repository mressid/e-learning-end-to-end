import * as React from "react";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  BookOpen,
  GraduationCap,
  Users,
  BarChart3,
  Award,
  Settings,
  ArrowLeft,
  Layers,
  Star,
  FileText,
  HelpCircle,
  FileCheck,
  ChevronRight,
  LogOut,
  Shield,
  KeyRound,
  Radio,
} from "lucide-react";

import { Sidebar } from "@/components/ui/sidebar";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/lib/i18n";
import { cn } from "@/lib/utils";
import {
  useCourseQuery,
  useSectionsQuery,
  useItemsQuery,
  useCourseInstructorsQuery,
  useCourseReviewSummaryQuery,
  useAdminMeQuery,
  useLogoutMutation,
  usePermissions,
} from "@/hooks/queries";
import type { PeopleTab } from "@/routes/users";
import { PERMISSIONS, type Permission, type SectionResponse } from "@/api";

/**
 * The signed-in administrator, and the way out.
 *
 * Previously a hardcoded name and a pravatar.cc portrait of someone who does
 * not exist — which meant every session looked like the same person and there
 * was no way to sign out at all.
 */
interface NavItem {
  id: string;
  title: string;
  url: string;
  icon: React.ComponentType<{ className?: string }>;
  /** Omitted means everyone signed in may see it. */
  permission?: Permission;
  badge?: string;
}

function SidebarAccount() {
  const { data: admin, isLoading } = useAdminMeQuery();
  const logout = useLogoutMutation();
  const navigate = useNavigate();

  const signOut = () => {
    logout.mutate(undefined, {
      // onSettled, not onSuccess: the local session is dropped either way, so
      // staying on the dashboard after a failed call would be the wrong result.
      onSettled: () => navigate({ to: "/login" }),
    });
  };

  if (isLoading || !admin) {
    return (
      <div className="flex items-center gap-2.5 rounded-xl border border-sidebar-border/60 bg-sidebar-accent/30 p-2">
        <Skeleton className="h-8 w-8 shrink-0 rounded-full" />
        <div className="min-w-0 flex-1 space-y-1.5">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-2.5 w-16" />
        </div>
      </div>
    );
  }

  const name = admin.username || admin.email || "Administrator";
  const initials = name.slice(0, 2).toUpperCase();
  // Role names are author-defined on the backend, so they are shown as stored
  // rather than run through i18n.
  const roles = admin.roles?.map((r) => r.name).join(", ");

  return (
    <div className="flex items-center gap-2.5 rounded-xl border border-sidebar-border/60 bg-sidebar-accent/30 p-2 text-xs">
      <span
        aria-hidden
        className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-primary/15 text-[11px] font-bold text-primary ring-1 ring-border/50"
      >
        {initials}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-sidebar-foreground" title={admin.email}>
          {name}
        </p>
        <p className="truncate text-[10px] text-muted-foreground">{roles || admin.email}</p>
      </div>
      <button
        type="button"
        onClick={signOut}
        disabled={logout.isPending}
        title="Sign out"
        aria-label="Sign out"
        className="grid h-7 w-7 shrink-0 place-items-center rounded-lg text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-foreground disabled:opacity-50"
      >
        <LogOut className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function SidebarSectionItem({
  courseId,
  section,
  index,
  activeItemId,
  isRtl,
}: {
  courseId: string;
  section: SectionResponse;
  index: number;
  activeItemId: string | null;
  isRtl: boolean;
}) {
  const [isExpanded, setIsExpanded] = React.useState(true);
  const { data: items, isLoading } = useItemsQuery(section.id || "");

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => setIsExpanded((prev) => !prev)}
        className="flex w-full items-center justify-between rounded-lg px-2 py-1.5 text-xs text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground cursor-pointer transition-colors group"
      >
        <div className="flex items-center gap-1.5 min-w-0">
          <ChevronRight
            className={cn(
              "h-3.5 w-3.5 shrink-0 transition-transform duration-150 text-muted-foreground/80",
              isExpanded && (isRtl ? "-rotate-90 text-foreground" : "rotate-90 text-foreground"),
            )}
          />
          <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded bg-secondary text-[10px] font-bold text-foreground">
            {index + 1}
          </span>
          <span className="truncate text-xs font-medium text-foreground text-left rtl:text-right">
            {section.title}
          </span>
        </div>

        {items && items.length > 0 && (
          <span className="rounded-full bg-secondary/80 px-1.5 py-0.2 text-[9px] font-medium text-muted-foreground shrink-0">
            {items.length}
          </span>
        )}
      </button>

      {isExpanded && (
        <div
          className={cn(
            "space-y-0.5 ml-3 border-l border-sidebar-border/50 pl-2.5 rtl:ml-0 rtl:mr-3 rtl:border-l-0 rtl:border-r rtl:pl-0 rtl:pr-2.5",
          )}
        >
          {isLoading ? (
            <div className="py-1 space-y-1">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-4 w-28" />
            </div>
          ) : items && items.length > 0 ? (
            items.map((item) => {
              const isSelected = activeItemId === item.id;
              const isLesson = item.type === "LESSON";
              const isQuiz = item.type === "QUIZ";
              const Icon = isLesson ? FileText : isQuiz ? HelpCircle : FileCheck;

              return (
                <Link
                  key={item.id}
                  to="/courses/$courseId"
                  params={{ courseId }}
                  search={{ tab: "curriculum", item: item.id, itemTitle: item.title }}
                  className={cn(
                    "flex items-center gap-2 rounded-md px-2 py-1 text-left rtl:text-right text-[11px] transition-all cursor-pointer group/item",
                    isSelected
                      ? "bg-primary text-primary-foreground font-medium shadow-xs"
                      : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                  )}
                >
                  <Icon
                    className={cn(
                      "h-3 w-3 shrink-0",
                      isSelected
                        ? "text-primary-foreground"
                        : "text-muted-foreground group-hover/item:text-foreground",
                    )}
                  />
                  <span className="truncate flex-1">{item.title}</span>
                  <span
                    className={cn(
                      "text-[9px] uppercase tracking-wider shrink-0",
                      isSelected ? "text-primary-foreground/80" : "text-muted-foreground/60",
                    )}
                  >
                    {item.type === "LESSON" ? "Lesson" : item.type === "QUIZ" ? "Quiz" : "Task"}
                  </span>
                </Link>
              );
            })
          ) : (
            <p className="px-2 py-1 text-[10px] text-muted-foreground/60 italic">
              No items in section
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export function AppSidebar() {
  const currentPath = useRouterState({ select: (r) => r.location.pathname });
  const search = useRouterState({
    select: (r) => (r.location.search ?? {}) as Record<string, string | undefined>,
  });
  const activeTab = (search["tab"] as string) || "overview";
  const activeItemId = search["item"] || null;

  const { t, dir } = useI18n();
  const isRtl = dir === "rtl";
  const { has, isReady } = usePermissions();
  // Super-admin is not a permission code — it is whether one of your roles is
  // the super role. React Query dedupes this with SidebarAccount's own call.
  const me = useAdminMeQuery();
  const isSuperAdmin = (me.data?.roles ?? []).some((r) => r.isSuper);

  // The people workspace has the same shape as the course drill-down: one
  // section at a time, chosen from the sidebar, held in the URL.
  const isPeopleWorkspace = currentPath === "/users" || currentPath.startsWith("/users/");
  const activePeopleTab = (search["view"] as PeopleTab | undefined) ?? "learners";

  // Detect if currently in a drilled-down course context: /courses/:courseId
  const courseMatch = currentPath.match(/^\/courses\/([a-zA-Z0-9_-]+)/);
  const isCourseDrilldown = Boolean(courseMatch && courseMatch[1] !== "new");
  const courseId = isCourseDrilldown ? courseMatch?.[1] || null : null;

  // Data for Course Drilldown
  const { data: activeCourse } = useCourseQuery(courseId || "");
  const { data: sections } = useSectionsQuery(courseId || "");
  const { data: instructors } = useCourseInstructorsQuery(courseId || "");
  const { data: reviewSummary } = useCourseReviewSummaryQuery(courseId || "");

  /*
   * Schedule, Messages and Support are gone from here.
   *
   * Not an oversight — there is nothing behind them. Nothing in the API models
   * a calendar or a cohort timetable; there is no administrator inbox (learner
   * notifications live under /me/*, which refuses an admin token, and
   * discussion threads exist only inside a course); and there is no ticketing.
   * A permanent "8" badge over an inbox that cannot load is worse than no link.
   * The routes still resolve, so old bookmarks do not 404 — they say what they
   * are instead.
   */
  const main: NavItem[] = [
    { id: "dashboard", title: t("nav.dashboard"), url: "/", icon: LayoutDashboard },
    {
      id: "courses",
      title: t("nav.courses"),
      url: "/courses",
      icon: BookOpen,
      permission: PERMISSIONS.COURSE_READ,
    },
    // One entry, not two. Learners, instructors, administrators, roles and
    // sessions are the same subject, and used to be answered in four unlinked
    // places. Deliberately not gated on user.read: a super admin with no
    // permission codes still belongs in here for roles and administrators.
    {
      id: "people",
      title: "People",
      url: "/users",
      icon: Users,
    },
  ];

  const insights: NavItem[] = [
    {
      id: "analytics",
      title: t("nav.analytics"),
      url: "/analytics",
      icon: BarChart3,
      permission: PERMISSIONS.COURSE_READ,
    },
    {
      id: "certificates",
      title: t("nav.certificates"),
      url: "/certificates",
      icon: Award,
      permission: PERMISSIONS.CERTIFICATE_READ,
    },
  ];

  const bottom: NavItem[] = [
    { id: "settings", title: t("nav.settings"), url: "/settings", icon: Settings },
  ];

  // Hiding a link an administrator cannot follow is courtesy; the server still
  // refuses the call. Until the token has been read, show everything rather
  // than flash a shrunken menu that then grows.
  const permitted = (items: NavItem[]) =>
    isReady ? items.filter((i) => !i.permission || has(i.permission)) : items;

  const isItemActive = (url: string) => {
    if (url === "/") {
      return currentPath === "/";
    }
    return currentPath === url || currentPath.startsWith(url + "/");
  };

  const peopleLinks: Array<{
    id: PeopleTab;
    title: string;
    icon: React.ComponentType<{ className?: string }>;
    /** Omitted means it is super-admin gated, which is not a permission code. */
    permission?: Permission;
    superOnly?: boolean;
  }> = [
    { id: "learners", title: "Learners", icon: Users, permission: PERMISSIONS.USER_READ },
    {
      id: "instructors",
      title: "Instructors",
      icon: GraduationCap,
      permission: PERMISSIONS.USER_READ,
    },
    { id: "admins", title: "Administrators", icon: Shield, superOnly: true },
    { id: "roles", title: "Roles & permissions", icon: KeyRound, superOnly: true },
    {
      id: "sessions",
      title: "Live sessions",
      icon: Radio,
      permission: PERMISSIONS.SETTINGS_MANAGE,
    },
  ];

  const workspaceLinks: Array<{
    id: string;
    title: string;
    icon: React.ComponentType<{ className?: string }>;
    tab: "overview" | "curriculum" | "instructors" | "reviews";
    badge?: string | undefined;
  }> = [
    {
      id: "overview",
      title: "Overview",
      icon: LayoutDashboard,
      tab: "overview",
    },
    {
      id: "curriculum",
      title: "Lessons & Curriculum",
      icon: Layers,
      tab: "curriculum",
      badge:
        sections && sections.length > 0
          ? `${sections.length} ${sections.length === 1 ? "ch" : "chs"}`
          : undefined,
    },
    {
      id: "instructors",
      title: "Instructors",
      icon: GraduationCap,
      tab: "instructors",
      badge: instructors && instructors.length > 0 ? `${instructors.length}` : undefined,
    },
    {
      id: "reviews",
      title: "Reviews & Ratings",
      icon: Star,
      tab: "reviews",
      badge: reviewSummary?.average != null ? `${reviewSummary.average.toFixed(1)}` : undefined,
    },
  ];

  return (
    <Sidebar
      // Off-canvas, not "none". With "none" the rail was permanently mounted at
      // 16rem on every screen — so the topbar's toggle drove state nothing read,
      // and a phone lost most of its width to navigation it could not dismiss.
      // Off-canvas keeps the desktop rail exactly as it was, gives that button
      // something to do, and turns the mobile rail into a sheet.
      collapsible="offcanvas"
      side={isRtl ? "right" : "left"}
      className={cn(
        "h-svh w-64 shrink-0 select-none bg-sidebar text-sidebar-foreground",
        isRtl ? "border-l border-r-0" : "border-r",
      )}
      style={{ "--sidebar-width": "16rem" } as React.CSSProperties}
    >
      {isPeopleWorkspace ? (
        /* ========================================================================= */
        /* People Workspace Contextual Navigation                                    */
        /* ========================================================================= */
        <div className="flex h-full min-h-0 w-full flex-col p-3.5">
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto sidebar-scroll">
            <Link
              to="/"
              className="group flex cursor-pointer items-center gap-2 rounded-xl border border-sidebar-border bg-sidebar-accent/50 px-3 py-2 text-xs font-semibold text-sidebar-foreground transition-colors hover:bg-sidebar-accent hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4 shrink-0 text-primary transition-transform group-hover:-translate-x-1 rtl:group-hover:translate-x-1" />
              <div className="min-w-0">
                <span className="block truncate">Back to dashboard</span>
                <span className="block text-[10px] font-normal text-muted-foreground">
                  Leave people management
                </span>
              </div>
            </Link>

            <div className="space-y-2 rounded-xl border border-sidebar-border/70 bg-sidebar-accent/30 p-3">
              <span className="grid h-6 w-6 place-items-center rounded-lg bg-primary/15 text-primary">
                <Users className="h-3.5 w-3.5" />
              </span>
              <div>
                <h3 className="text-xs font-bold text-sidebar-foreground">People</h3>
                <p className="mt-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
                  Accounts &amp; access
                </p>
              </div>
            </div>

            <div className="space-y-1">
              <div className="px-2 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Manage
              </div>
              {peopleLinks.map((item) => {
                // Sections this account cannot open are hidden rather than shown
                // and refused — but only once the token has been read, so the
                // menu does not visibly shrink a beat after loading.
                if (isReady) {
                  if (item.superOnly && !isSuperAdmin) return null;
                  if (item.permission && !has(item.permission)) return null;
                }
                const isSelected = activePeopleTab === item.id;
                const Icon = item.icon;
                return (
                  <Link
                    key={item.id}
                    to="/users"
                    search={{ view: item.id }}
                    className={cn(
                      "flex cursor-pointer items-center gap-2.5 rounded-lg px-2.5 py-2 text-xs font-medium transition-colors",
                      isSelected
                        ? "bg-primary font-semibold text-primary-foreground shadow-xs"
                        : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                    )}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span className="flex-1 truncate text-left rtl:text-right">{item.title}</span>
                  </Link>
                );
              })}
            </div>
          </div>

          <div className="mt-3 shrink-0 space-y-2 border-t border-sidebar-border/60 pt-3">
            <Link
              to="/settings"
              className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-xs font-medium text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
            >
              <Settings className="h-4 w-4 shrink-0" />
              <span>Platform Settings</span>
            </Link>
            <SidebarAccount />
          </div>
        </div>
      ) : isCourseDrilldown && courseId ? (
        /* ========================================================================= */
        /* Drill-Down Course Contextual Navigation                                  */
        /* ========================================================================= */
        <div className="flex h-full min-h-0 w-full flex-col p-3.5">
          {/*
            The curriculum tree is the one genuinely unbounded thing in here —
            a course can have any number of sections and items — so it gets its
            own scroll. Everything around it stays fixed.
          */}
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto sidebar-scroll">
            {/* Return to Platform Navigation Action */}
            <Link
              to="/courses"
              className="flex items-center gap-2 rounded-xl border border-sidebar-border bg-sidebar-accent/50 px-3 py-2 text-xs font-semibold text-sidebar-foreground transition-colors hover:bg-sidebar-accent hover:text-foreground group cursor-pointer"
            >
              <ArrowLeft className="h-4 w-4 shrink-0 transition-transform group-hover:-translate-x-1 rtl:group-hover:translate-x-1 text-primary" />
              <div className="min-w-0">
                <span className="block truncate">Return to All Courses</span>
                <span className="block text-[10px] text-muted-foreground font-normal">
                  Exit course workspace
                </span>
              </div>
            </Link>

            {/* Course Identity Header */}
            <div className="rounded-xl border border-sidebar-border/70 bg-sidebar-accent/30 p-3 space-y-2">
              <div className="flex items-center justify-between gap-1.5">
                <span className="grid h-6 w-6 place-items-center rounded-lg bg-primary/15 text-primary">
                  <BookOpen className="h-3.5 w-3.5" />
                </span>
                {activeCourse?.status && (
                  <Badge
                    variant={
                      activeCourse.status === "PUBLISHED"
                        ? "default"
                        : activeCourse.status === "DRAFT"
                          ? "secondary"
                          : "outline"
                    }
                    className="text-[10px] uppercase font-semibold tracking-wider h-5 px-1.5"
                  >
                    {activeCourse.status}
                  </Badge>
                )}
              </div>

              <div>
                <h3 className="text-xs font-bold text-sidebar-foreground line-clamp-2 leading-snug">
                  {activeCourse?.title || "Loading Course..."}
                </h3>
                {activeCourse?.level && (
                  <p className="text-[10px] text-muted-foreground mt-0.5 uppercase tracking-wide">
                    {activeCourse.level}
                  </p>
                )}
              </div>
            </div>

            {/* Course Navigation Sections */}
            <div className="space-y-1">
              <div className="px-2 py-1 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                Course Workspace
              </div>

              {workspaceLinks.map((item) => {
                const isSelected = activeTab === item.tab;
                const Icon = item.icon;

                return (
                  <Link
                    key={item.id}
                    to="/courses/$courseId"
                    params={{ courseId }}
                    search={{ tab: item.tab }}
                    className={cn(
                      "flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-xs font-medium transition-colors cursor-pointer",
                      isSelected
                        ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                        : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                    )}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span className="truncate flex-1 text-left rtl:text-right">{item.title}</span>
                    {item.badge && (
                      <span
                        className={cn(
                          "ml-auto rtl:ml-0 rtl:mr-auto rounded-full px-1.5 py-0.2 text-[10px] font-medium",
                          isSelected
                            ? "bg-primary-foreground/20 text-primary-foreground"
                            : "bg-sidebar-accent text-sidebar-foreground",
                        )}
                      >
                        {item.badge}
                      </span>
                    )}
                  </Link>
                );
              })}
            </div>

            {/* Curriculum Explorer: Sections & Items Tree */}
            <div className="space-y-1.5 pt-2 border-t border-sidebar-border/60">
              <div className="px-2 py-1 flex items-center justify-between text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                <span>Sections & Items</span>
                {sections && sections.length > 0 && (
                  <span className="text-[10px] font-normal normal-case text-muted-foreground">
                    {sections.length} {sections.length === 1 ? "chapter" : "chapters"}
                  </span>
                )}
              </div>

              <div className="space-y-1">
                {sections && sections.length > 0 ? (
                  sections.map((sec, idx) => (
                    <SidebarSectionItem
                      key={sec.id}
                      courseId={courseId}
                      section={sec}
                      index={idx}
                      activeItemId={activeItemId}
                      isRtl={isRtl}
                    />
                  ))
                ) : (
                  <p className="px-2 py-2 text-xs text-muted-foreground italic">
                    No sections created yet.
                  </p>
                )}
              </div>
            </div>
          </div>

          {/* Bottom Settings Link */}
          <div className="mt-3 shrink-0 border-t border-sidebar-border/60 pt-3">
            <Link
              to="/settings"
              className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-xs font-medium text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground transition-colors"
            >
              <Settings className="h-4 w-4 shrink-0" />
              <span>Platform Settings</span>
            </Link>
          </div>
        </div>
      ) : (
        /* ========================================================================= */
        /* Primary Hub Platform Navigation (Standard Single-Tier)                    */
        /* ========================================================================= */
        <div className="flex h-full min-h-0 w-full flex-col p-3.5">
          {/* Top Brand Logo — pinned */}
          <div className="shrink-0">
            <div className="flex items-center gap-2.5 px-2 py-1.5">
              <div className="grid h-9 w-9 place-items-center rounded-xl bg-primary text-primary-foreground shadow-xs">
                <GraduationCap className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <h1 className="text-sm font-bold tracking-tight text-foreground">
                  {t("brand.name")}
                </h1>
                <p className="text-[11px] text-muted-foreground truncate">{t("brand.tagline")}</p>
              </div>
            </div>
          </div>

          {/*
            The nav itself is a fixed, known length, so at any ordinary window
            height nothing here scrolls. `overflow-y-auto` is a floor, not a
            feature: on a very short viewport it keeps Settings and the account
            card reachable instead of clipping them off the bottom.
          */}
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pt-4 sidebar-scroll">
            {/* Main Navigation Group */}
            <div className="space-y-1">
              <div className="px-2 py-1 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                {t("nav.learning")}
              </div>
              {permitted(main).map((item) => {
                const active = isItemActive(item.url);
                const Icon = item.icon;
                return (
                  <Link
                    key={item.url}
                    to={item.url}
                    className={cn(
                      "flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-medium transition-all duration-150 cursor-pointer",
                      active
                        ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                        : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                    )}
                  >
                    <Icon className="h-4.5 w-4.5 shrink-0" />
                    <span className="truncate">{item.title}</span>
                    {item.badge && (
                      <span
                        className={cn(
                          "ml-auto rtl:ml-0 rtl:mr-auto rounded-full px-1.5 py-0.5 text-[10px] font-bold",
                          active
                            ? "bg-primary-foreground/20 text-primary-foreground"
                            : "bg-sidebar-accent text-sidebar-foreground",
                        )}
                      >
                        {item.badge}
                      </span>
                    )}
                  </Link>
                );
              })}
            </div>

            {/* Insights Group */}
            <div className="space-y-1 pt-2 border-t border-sidebar-border/60">
              <div className="px-2 py-1 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                {t("nav.insights")}
              </div>
              {permitted(insights).map((item) => {
                const active = isItemActive(item.url);
                const Icon = item.icon;
                return (
                  <Link
                    key={item.url}
                    to={item.url}
                    className={cn(
                      "flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-medium transition-all duration-150 cursor-pointer",
                      active
                        ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                        : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                    )}
                  >
                    <Icon className="h-4.5 w-4.5 shrink-0" />
                    <span className="truncate">{item.title}</span>
                  </Link>
                );
              })}
            </div>
          </div>

          {/* Bottom Actions & User Profile — pinned */}
          <div className="mt-3 shrink-0 space-y-2 border-t border-sidebar-border/60 pt-3">
            <div className="space-y-1">
              {permitted(bottom).map((item) => {
                const active = isItemActive(item.url);
                const Icon = item.icon;
                return (
                  <Link
                    key={item.url}
                    to={item.url}
                    className={cn(
                      "flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-medium transition-all duration-150 cursor-pointer",
                      active
                        ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                        : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                    )}
                  >
                    <Icon className="h-4.5 w-4.5 shrink-0" />
                    <span className="truncate">{item.title}</span>
                  </Link>
                );
              })}
            </div>

            <SidebarAccount />
          </div>
        </div>
      )}
    </Sidebar>
  );
}
