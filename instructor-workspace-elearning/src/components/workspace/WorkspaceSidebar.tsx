import * as React from "react";
import { Link, useRouterState } from "@tanstack/react-router";
import {
  ArrowLeft,
  BookOpen,
  ChevronRight,
  FileCheck,
  FileText,
  GraduationCap,
  HelpCircle,
  Layers,
  LayoutDashboard,
  LogOut,
  Paperclip,
} from "lucide-react";

import { Sidebar } from "@/components/ui/sidebar";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { useCourseQuery, useSectionsQuery, useItemsQuery } from "@/hooks/queries";
import type { SectionResponse } from "@/api";
import { useI18n } from "@/lib/i18n";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

/** The tabs of a course, as the sidebar knows them. */
type CourseTab = "overview" | "curriculum" | "resources";

/**
 * The signed-in instructor, and the way out.
 */
function SidebarAccount() {
  const { user, isLoadingUser, logout, isLoggingOut } = useAuth();

  if (isLoadingUser || !user) {
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

  const name = user.username || user.email || "Instructor";

  return (
    <div className="flex items-center gap-2.5 rounded-xl border border-sidebar-border/60 bg-sidebar-accent/30 p-2 text-xs">
      <span
        aria-hidden
        className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-primary/15 text-[11px] font-bold text-primary ring-1 ring-border/50"
      >
        {name.slice(0, 2).toUpperCase()}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-sidebar-foreground" title={user.email}>
          {name}
        </p>
        <p className="truncate text-[10px] text-muted-foreground">Instructor</p>
      </div>
      <button
        type="button"
        onClick={() => void logout()}
        disabled={isLoggingOut}
        title="Sign out"
        aria-label="Sign out"
        className="grid h-7 w-7 shrink-0 place-items-center rounded-lg text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-foreground disabled:opacity-50"
      >
        <LogOut className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function itemIcon(type: string | undefined) {
  if (type === "QUIZ") return HelpCircle;
  if (type === "ASSIGNMENT") return FileCheck;
  return FileText;
}

/**
 * One section in the tree, with its items underneath.
 *
 * Fetches its own items rather than the parent fetching all of them: a course
 * with twenty sections would otherwise fire twenty requests to draw a rail
 * that is mostly collapsed. Expanded sections pay for themselves; collapsed
 * ones cost nothing.
 */
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
  const { data: items, isLoading } = useItemsQuery(section.id ?? "", isExpanded);

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => setIsExpanded((prev) => !prev)}
        className="group flex w-full cursor-pointer items-center justify-between rounded-lg px-2 py-1.5 text-xs text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
      >
        <div className="flex min-w-0 items-center gap-1.5">
          <ChevronRight
            className={cn(
              "h-3.5 w-3.5 shrink-0 text-muted-foreground/80 transition-transform duration-150",
              isExpanded && (isRtl ? "-rotate-90 text-foreground" : "rotate-90 text-foreground"),
            )}
          />
          <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded bg-secondary text-[10px] font-bold text-foreground">
            {index + 1}
          </span>
          <span className="truncate text-left text-xs font-medium text-foreground rtl:text-right">
            {section.title}
          </span>
        </div>
        {items && items.length > 0 && (
          <span className="shrink-0 rounded-full bg-secondary/80 px-1.5 text-[9px] font-medium text-muted-foreground">
            {items.length}
          </span>
        )}
      </button>

      {isExpanded && (
        <div className="ml-3 space-y-0.5 border-l border-sidebar-border/50 pl-2.5 rtl:ml-0 rtl:mr-3 rtl:border-l-0 rtl:border-r rtl:pl-0 rtl:pr-2.5">
          {isLoading ? (
            <div className="space-y-1 py-1">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-4 w-28" />
            </div>
          ) : items && items.length > 0 ? (
            items.map((item) => {
              const isSelected = activeItemId === item.id;
              const Icon = itemIcon(item.type);
              // A lesson opens its editor; a quiz or assignment has none yet,
              // so those still just select themselves in the curriculum. Two
              // literal <Link>s rather than one with spread props: the router
              // types each destination against its own params and search, and a
              // union of the two satisfies neither.
              const isLesson = item.type === "LESSON";
              const rowClass = cn(
                "group/item flex cursor-pointer items-center gap-2 rounded-md px-2 py-1 text-left text-[11px] transition-all rtl:text-right",
                isSelected
                  ? "bg-primary font-medium text-primary-foreground shadow-xs"
                  : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
              );
              const label = (
                <>
                  <Icon
                    className={cn(
                      "h-3 w-3 shrink-0",
                      isSelected
                        ? "text-primary-foreground"
                        : "text-muted-foreground group-hover/item:text-foreground",
                    )}
                  />
                  <span className="flex-1 truncate">{item.title}</span>
                  <span
                    className={cn(
                      "shrink-0 text-[9px] uppercase tracking-wider",
                      isSelected ? "text-primary-foreground/80" : "text-muted-foreground/60",
                    )}
                  >
                    {item.type === "LESSON" ? "Lesson" : item.type === "QUIZ" ? "Quiz" : "Task"}
                  </span>
                </>
              );

              return isLesson ? (
                <Link
                  key={item.id}
                  to="/courses/$courseId/items/$itemId"
                  params={{ courseId, itemId: item.id ?? "" }}
                  className={rowClass}
                >
                  {label}
                </Link>
              ) : (
                <Link
                  key={item.id}
                  to="/courses/$courseId"
                  params={{ courseId }}
                  search={{ tab: "curriculum", item: item.id ?? "" }}
                  className={rowClass}
                >
                  {label}
                </Link>
              );
            })
          ) : (
            <p className="px-2 py-1 text-[10px] italic text-muted-foreground/60">
              Nothing in this section
            </p>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * The rail, in its two states.
 *
 * Outside a course it lists the app's few destinations. Inside one it becomes
 * that course: the parts of it, and the sections and items themselves. Course
 * navigation belongs here rather than in a row of tabs above the content —
 * the curriculum is a tree, it is unbounded, and you move around it constantly
 * while editing. A tab strip can hold three words; this holds the course.
 */
export function WorkspaceSidebar() {
  const currentPath = useRouterState({ select: (r) => r.location.pathname });
  const search = useRouterState({
    select: (r) => (r.location.search ?? {}) as Record<string, string | undefined>,
  });
  const { dir } = useI18n();
  const isRtl = dir === "rtl";

  const activeTab = (search["tab"] as CourseTab | undefined) ?? "overview";
  // Either the item the curriculum has selected, or the lesson whose editor is
  // open — the tree marks both the same way.
  const openItemId = currentPath.match(/^\/courses\/[a-zA-Z0-9-]+\/items\/([a-zA-Z0-9-]+)/)?.[1];
  const activeItemId = openItemId ?? search["item"] ?? null;

  // Inside a course, the rail becomes that course.
  const courseMatch = currentPath.match(/^\/courses\/([a-zA-Z0-9-]+)/);
  const courseId = courseMatch?.[1] ?? null;
  const inCourse = Boolean(courseId);

  const course = useCourseQuery(courseId ?? "", inCourse);
  const sections = useSectionsQuery(courseId ?? "", inCourse);

  const workspaceLinks: Array<{
    id: CourseTab;
    title: string;
    icon: React.ComponentType<{ className?: string }>;
    badge?: string | undefined;
  }> = [
    { id: "overview", title: "Overview", icon: LayoutDashboard },
    {
      id: "curriculum",
      title: "Curriculum",
      icon: Layers,
      badge: sections.data?.length ? String(sections.data.length) : undefined,
    },
    { id: "resources", title: "Documents & links", icon: Paperclip },
  ];

  return (
    <Sidebar
      collapsible="offcanvas"
      side={isRtl ? "right" : "left"}
      className={cn(
        "h-svh w-64 shrink-0 select-none bg-sidebar text-sidebar-foreground",
        isRtl ? "border-l border-r-0" : "border-r",
      )}
      style={{ "--sidebar-width": "16rem" } as React.CSSProperties}
    >
      {inCourse && courseId ? (
        <div className="flex h-full min-h-0 w-full flex-col p-3.5">
          {/*
            The curriculum tree is the one genuinely unbounded thing in here —
            a course can have any number of sections and items — so it gets its
            own scroll. Everything around it stays fixed.
          */}
          <div className="sidebar-scroll min-h-0 flex-1 space-y-4 overflow-y-auto">
            <Link
              to="/"
              className="group flex cursor-pointer items-center gap-2 rounded-xl border border-sidebar-border bg-sidebar-accent/50 px-3 py-2 text-xs font-semibold text-sidebar-foreground transition-colors hover:bg-sidebar-accent hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4 shrink-0 text-primary transition-transform group-hover:-translate-x-1 rtl:group-hover:translate-x-1" />
              <div className="min-w-0">
                <span className="block truncate">Back to my courses</span>
                <span className="block text-[10px] font-normal text-muted-foreground">
                  Leave this course
                </span>
              </div>
            </Link>

            <div className="space-y-2 rounded-xl border border-sidebar-border/70 bg-sidebar-accent/30 p-3">
              <div className="flex items-center justify-between gap-1.5">
                <span className="grid h-6 w-6 place-items-center rounded-lg bg-primary/15 text-primary">
                  <BookOpen className="h-3.5 w-3.5" />
                </span>
                {course.data?.status && (
                  <Badge
                    variant={
                      course.data.status === "PUBLISHED"
                        ? "default"
                        : course.data.status === "DRAFT"
                          ? "secondary"
                          : "outline"
                    }
                    className="h-5 px-1.5 text-[10px] font-semibold uppercase tracking-wider"
                  >
                    {course.data.status}
                  </Badge>
                )}
              </div>
              <div>
                <h3 className="line-clamp-2 text-xs font-bold leading-snug text-sidebar-foreground">
                  {course.data?.title || "Loading course…"}
                </h3>
                {course.data?.level && (
                  <p className="mt-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
                    {course.data.level}
                  </p>
                )}
              </div>
            </div>

            <div className="space-y-1">
              <div className="px-2 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                This course
              </div>
              {workspaceLinks.map((link) => {
                const isSelected = activeTab === link.id;
                const Icon = link.icon;
                return (
                  <Link
                    key={link.id}
                    to="/courses/$courseId"
                    params={{ courseId }}
                    search={{ tab: link.id }}
                    className={cn(
                      "flex cursor-pointer items-center gap-2.5 rounded-lg px-2.5 py-2 text-xs font-medium transition-colors",
                      isSelected
                        ? "bg-primary font-semibold text-primary-foreground shadow-xs"
                        : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                    )}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span className="flex-1 truncate text-left rtl:text-right">{link.title}</span>
                    {link.badge && (
                      <span
                        className={cn(
                          "ml-auto rounded-full px-1.5 text-[10px] font-medium rtl:ml-0 rtl:mr-auto",
                          isSelected
                            ? "bg-primary-foreground/20 text-primary-foreground"
                            : "bg-sidebar-accent text-sidebar-foreground",
                        )}
                      >
                        {link.badge}
                      </span>
                    )}
                  </Link>
                );
              })}
            </div>

            <div className="space-y-1.5 border-t border-sidebar-border/60 pt-2">
              <div className="flex items-center justify-between px-2 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                <span>Sections &amp; items</span>
                {sections.data && sections.data.length > 0 && (
                  <span className="text-[10px] font-normal normal-case text-muted-foreground">
                    {sections.data.length}
                  </span>
                )}
              </div>

              <div className="space-y-1">
                {sections.isLoading ? (
                  <div className="space-y-1 px-2">
                    <Skeleton className="h-5 w-32" />
                    <Skeleton className="h-5 w-28" />
                  </div>
                ) : sections.data && sections.data.length > 0 ? (
                  sections.data.map((section, index) => (
                    <SidebarSectionItem
                      key={section.id}
                      courseId={courseId}
                      section={section}
                      index={index}
                      activeItemId={activeItemId}
                      isRtl={isRtl}
                    />
                  ))
                ) : (
                  <p className="px-2 py-2 text-xs italic text-muted-foreground">No sections yet.</p>
                )}
              </div>
            </div>
          </div>

          <div className="mt-3 shrink-0 border-t border-sidebar-border/60 pt-3">
            <SidebarAccount />
          </div>
        </div>
      ) : (
        <div className="flex h-full min-h-0 w-full flex-col p-3.5">
          <div className="shrink-0">
            <div className="flex items-center gap-2.5 px-2 py-1.5">
              <div className="grid h-9 w-9 place-items-center rounded-xl bg-primary text-primary-foreground shadow-xs">
                <GraduationCap className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <h1 className="text-sm font-bold tracking-tight text-foreground">Lernova</h1>
                <p className="truncate text-[11px] text-muted-foreground">Instructor workspace</p>
              </div>
            </div>
          </div>

          <div className="sidebar-scroll min-h-0 flex-1 space-y-1 overflow-y-auto pt-4">
            <div className="px-2 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
              Teaching
            </div>
            <Link
              to="/"
              className={cn(
                "flex cursor-pointer items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-medium transition-all duration-150",
                currentPath === "/"
                  ? "bg-primary font-semibold text-primary-foreground shadow-xs"
                  : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
              )}
            >
              <BookOpen className="h-4.5 w-4.5 shrink-0" />
              <span className="truncate">My courses</span>
            </Link>
          </div>

          <div className="mt-3 shrink-0 border-t border-sidebar-border/60 pt-3">
            <SidebarAccount />
          </div>
        </div>
      )}
    </Sidebar>
  );
}
