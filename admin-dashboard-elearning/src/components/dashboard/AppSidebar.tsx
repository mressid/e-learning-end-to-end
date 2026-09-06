import * as React from "react";
import { Link, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  BookOpen,
  GraduationCap,
  Users,
  CalendarDays,
  MessagesSquare,
  BarChart3,
  Award,
  Settings,
  LifeBuoy,
  ArrowLeft,
  Layers,
  Star,
  FileText,
  HelpCircle,
  FileCheck,
  ChevronRight,
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
} from "@/hooks/queries";
import type { SectionResponse } from "@/api";

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

  // Detect if currently in a drilled-down course context: /courses/:courseId
  const courseMatch = currentPath.match(/^\/courses\/([a-zA-Z0-9_-]+)/);
  const isCourseDrilldown = Boolean(courseMatch && courseMatch[1] !== "new");
  const courseId = isCourseDrilldown ? courseMatch?.[1] || null : null;

  // Data for Course Drilldown
  const { data: activeCourse } = useCourseQuery(courseId || "");
  const { data: sections } = useSectionsQuery(courseId || "");
  const { data: instructors } = useCourseInstructorsQuery(courseId || "");
  const { data: reviewSummary } = useCourseReviewSummaryQuery(courseId || "");

  const main = [
    { id: "dashboard", title: t("nav.dashboard"), url: "/", icon: LayoutDashboard },
    { id: "courses", title: t("nav.courses"), url: "/courses", icon: BookOpen },
    { id: "students", title: t("nav.students"), url: "/students", icon: Users },
    { id: "instructors", title: t("nav.instructors"), url: "/instructors", icon: GraduationCap },
    { id: "schedule", title: t("nav.schedule"), url: "/schedule", icon: CalendarDays },
    {
      id: "messages",
      title: t("nav.messages"),
      url: "/messages",
      icon: MessagesSquare,
      badge: "8",
    },
  ];

  const insights = [
    { id: "analytics", title: t("nav.analytics"), url: "/analytics", icon: BarChart3 },
    { id: "certificates", title: t("nav.certificates"), url: "/certificates", icon: Award },
  ];

  const bottom = [
    { id: "settings", title: t("nav.settings"), url: "/settings", icon: Settings },
    { id: "support", title: t("nav.support"), url: "/support", icon: LifeBuoy },
  ];

  const isItemActive = (url: string) => {
    if (url === "/") {
      return currentPath === "/";
    }
    return currentPath === url || currentPath.startsWith(url + "/");
  };

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
      collapsible="none"
      side={isRtl ? "right" : "left"}
      className={cn(
        "w-64 select-none bg-sidebar text-sidebar-foreground transition-all duration-200",
        isRtl ? "border-l border-r-0" : "border-r",
      )}
      style={{ "--sidebar-width": "16rem" } as React.CSSProperties}
    >
      {isCourseDrilldown && courseId ? (
        /* ========================================================================= */
        /* Drill-Down Course Contextual Navigation                                  */
        /* ========================================================================= */
        <div className="flex h-full w-full flex-col justify-between p-3.5">
          <div className="flex-1 overflow-y-auto space-y-4 pr-1">
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
          <div className="pt-3 border-t border-sidebar-border/60 shrink-0">
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
        <div className="flex h-full w-full flex-col justify-between p-3.5">
          <div className="space-y-4">
            {/* Top Brand Logo */}
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

            {/* Main Navigation Group */}
            <div className="space-y-1">
              <div className="px-2 py-1 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                {t("nav.learning")}
              </div>
              {main.map((item) => {
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
              {insights.map((item) => {
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

          {/* Bottom Actions & User Profile */}
          <div className="space-y-2 pt-3 border-t border-sidebar-border/60">
            <div className="space-y-1">
              {bottom.map((item) => {
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

            {/* User Profile Card */}
            <div className="flex items-center gap-2.5 rounded-xl border border-sidebar-border/60 bg-sidebar-accent/30 p-2 text-xs">
              <img
                src="https://i.pravatar.cc/64?img=47"
                alt="Amina Bel"
                className="h-8 w-8 rounded-full object-cover ring-1 ring-border/50"
              />
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-sidebar-foreground">Amina Bel</p>
                <p className="truncate text-[10px] text-muted-foreground">{t("sidebar.role")}</p>
              </div>
            </div>
          </div>
        </div>
      )}
    </Sidebar>
  );
}
