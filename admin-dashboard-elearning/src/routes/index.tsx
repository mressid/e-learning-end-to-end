import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  ArrowUpRight,
  ArrowDownRight,
  BookOpen,
  CheckCircle2,
  Clock3,
  Download,
  ExternalLink,
  GraduationCap,
  MoreHorizontal,
  PlayCircle,
  Plus,
  Star,
  Users,
} from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { FloatingDetailSheet } from "@/components/dashboard/FloatingDetailSheet";
import { useCoursesQuery } from "@/hooks/queries";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Dashboard — Lernova Learning Platform" },
      {
        name: "description",
        content:
          "Track enrollments, watch time, course completion and student performance across your e-learning programs.",
      },
      { property: "og:title", content: "Dashboard — Lernova Learning Platform" },
      {
        property: "og:description",
        content: "Enrollments, watch time and completion analytics for online courses.",
      },
    ],
  }),
  component: Dashboard,
});

const C = {
  blue: "oklch(0.58 0.2 259)",
  green: "oklch(0.65 0.16 155)",
  orange: "oklch(0.72 0.16 55)",
};

const revenue = [
  { d: "1 Jan", v: 18, p: 14 },
  { d: "5 Jan", v: 24, p: 19 },
  { d: "9 Jan", v: 21, p: 20 },
  { d: "13 Jan", v: 33, p: 24 },
  { d: "17 Jan", v: 29, p: 26 },
  { d: "21 Jan", v: 41, p: 30 },
  { d: "25 Jan", v: 38, p: 33 },
  { d: "29 Jan", v: 52, p: 36 },
];

const courses = [
  {
    id: "#CR-901",
    name: "Intro to Data Science",
    cat: "Data",
    students: 2310,
    hours: "12,480 h",
    rating: 5.0,
    up: true,
  },
  {
    id: "#CR-874",
    name: "UI/UX Design Masterclass",
    cat: "Design",
    students: 1230,
    hours: "8,912 h",
    rating: 4.8,
    up: true,
  },
  {
    id: "#CR-812",
    name: "Advanced React Patterns",
    cat: "Dev",
    students: 812,
    hours: "6,048 h",
    rating: 4.7,
    up: false,
  },
  {
    id: "#CR-780",
    name: "Business English B2",
    cat: "Language",
    students: 645,
    hours: "5,120 h",
    rating: 4.5,
    up: true,
  },
  {
    id: "#CR-744",
    name: "Financial Modeling",
    cat: "Finance",
    students: 572,
    hours: "4,724 h",
    rating: 4.5,
    up: false,
  },
];

const upcoming = [
  { time: "09:30", title: "Live: Neural Networks Q&A", tutor: "Dr. Hana Ferjani" },
  { time: "12:00", title: "Workshop: Design Systems", tutor: "Marco Silva" },
  { time: "16:15", title: "Cohort review — React 12", tutor: "Yassine Ben A." },
];

function Delta({ value, up }: { value: number; up: boolean }) {
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={`inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-semibold ${
        up ? "bg-success/15 text-success" : "bg-destructive/15 text-destructive"
      }`}
    >
      <Icon className="h-3 w-3" />
      {value}%
    </span>
  );
}

interface DashboardCourseItem {
  id: string;
  realId?: string | undefined;
  name: string;
  cat: string;
  students: number;
  hours: string;
  rating: number;
  up: boolean;
}

function Dashboard() {
  const { t } = useI18n();
  const [selectedCourse, setSelectedCourse] = useState<DashboardCourseItem | null>(null);
  const { data: realCoursesData } = useCoursesQuery({ page: 0, size: 5 });

  const displayCourses: DashboardCourseItem[] =
    realCoursesData?.content && realCoursesData.content.length > 0
      ? realCoursesData.content.map((c, i) => {
          const courseId = c.id ?? `live-${i}`;
          return {
            id: `#CR-${courseId.slice(0, 4).toUpperCase()}`,
            realId: c.id,
            name: c.title ?? "Untitled Course",
            cat: c.level || "General",
            students: 120 + i * 45,
            hours: "48 h",
            rating: 4.9,
            up: true,
          };
        })
      : courses.map((c) => ({ ...c }));

  const statItems = [
    {
      label: t("stat.students"),
      value: "16,431",
      delta: 15.5,
      up: true,
      prev: t("stat.vs", { v: "14,653" }),
      icon: Users,
    },
    {
      label: t("stat.enrollments"),
      value: "6,225",
      delta: 8.4,
      up: true,
      prev: t("stat.vs", { v: "5,732" }),
      icon: BookOpen,
    },
    {
      label: t("stat.hours"),
      value: "28,320",
      delta: 10.5,
      up: false,
      prev: t("stat.vs", { v: "31,294" }),
      icon: PlayCircle,
    },
    {
      label: t("stat.completions"),
      value: "1,224",
      delta: 4.4,
      up: true,
      prev: t("stat.vs", { v: "1,186" }),
      icon: GraduationCap,
    },
  ];

  const weeklyData = [
    { d: t("day.sun"), v: 3120 },
    { d: t("day.mon"), v: 5480 },
    { d: t("day.tue"), v: 8162 },
    { d: t("day.wed"), v: 6240 },
    { d: t("day.thu"), v: 4980 },
    { d: t("day.fri"), v: 5610 },
    { d: t("day.sat"), v: 3890 },
  ];

  const deviceData = [
    { name: t("dev.mobile"), value: 52, color: C.blue },
    { name: t("dev.desktop"), value: 33, color: C.green },
    { name: t("dev.tablet"), value: 15, color: C.orange },
  ];

  const revenueBreakdown = [
    { n: "2,884", l: t("rev.self"), c: C.blue },
    { n: "1,432", l: t("rev.cohort"), c: C.green },
    { n: "562", l: t("rev.corp"), c: C.orange },
  ];

  return (
    <div className="space-y-4 p-4 sm:p-6">
      <header className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 sm:flex sm:flex-wrap sm:justify-between">
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-extrabold tracking-tight sm:text-3xl">
            {t("dash.title")}
          </h1>
          <p className="mt-1 truncate text-sm text-muted-foreground">{t("dash.range")}</p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button className="hidden h-10 items-center gap-2 rounded-xl border bg-card px-3 text-sm font-semibold transition-colors hover:bg-secondary sm:inline-flex">
            <Plus className="h-4 w-4" /> {t("dash.newCourse")}
          </button>
          <button className="inline-flex h-10 items-center gap-2 rounded-xl bg-primary px-3.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90">
            <Download className="h-4 w-4" /> {t("dash.export")}
          </button>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {statItems.map((s) => (
          <div key={s.label} className="card-surface p-4 transition-colors">
            <div className="flex min-w-0 items-center justify-between gap-2">
              <p className="truncate text-sm font-medium text-muted-foreground">{s.label}</p>
              <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-accent text-accent-foreground">
                <s.icon className="h-4 w-4" />
              </span>
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <span className="text-2xl font-extrabold tracking-tight">{s.value}</span>
              <Delta value={s.delta} up={s.up} />
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{s.prev}</p>
          </div>
        ))}
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="card-surface p-4 xl:col-span-2">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-muted-foreground">{t("rev.title")}</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <span className="text-3xl font-extrabold tracking-tight">$446.7K</span>
                <Delta value={24.4} up />
                <span className="text-xs text-muted-foreground">{t("rev.vs")}</span>
              </div>
            </div>
            <button className="rounded-lg p-1.5 text-muted-foreground hover:bg-secondary">
              <MoreHorizontal className="h-4 w-4" />
            </button>
          </div>

          <div className="mt-4 h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={revenue} margin={{ left: -20, right: 8, top: 8 }}>
                <defs>
                  <linearGradient id="fillV" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={C.blue} stopOpacity={0.35} />
                    <stop offset="100%" stopColor={C.blue} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="4 4" stroke="var(--border)" vertical={false} />
                <XAxis
                  dataKey="d"
                  tickLine={false}
                  axisLine={false}
                  fontSize={11}
                  stroke="var(--muted-foreground)"
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  fontSize={11}
                  stroke="var(--muted-foreground)"
                />
                <Tooltip
                  contentStyle={{
                    borderRadius: 12,
                    border: "1px solid var(--border)",
                    background: "var(--card)",
                    color: "var(--card-foreground)",
                    fontSize: 12,
                    boxShadow: "var(--shadow-card)",
                  }}
                />
                <Area
                  isAnimationActive={false}
                  type="monotone"
                  dataKey="p"
                  stroke="var(--muted-foreground)"
                  strokeDasharray="4 4"
                  fill="none"
                  strokeWidth={1.5}
                />
                <Area
                  isAnimationActive={false}
                  type="monotone"
                  dataKey="v"
                  stroke={C.blue}
                  strokeWidth={2.5}
                  fill="url(#fillV)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-3 rounded-xl border border-border p-3 sm:grid-cols-3">
            {revenueBreakdown.map((x) => (
              <div key={x.l} className="min-w-0">
                <p className="truncate text-lg font-extrabold">{x.n}</p>
                <p className="truncate text-xs text-muted-foreground">{x.l}</p>
                <div className="mt-2 h-1.5 rounded-full" style={{ backgroundColor: x.c }} />
              </div>
            ))}
          </div>
        </div>

        <div className="space-y-4">
          <div className="card-surface p-4">
            <p className="text-sm font-semibold">{t("day.title")}</p>
            <div className="mt-3 h-48 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={weeklyData} margin={{ left: 4, right: 4 }}>
                  <XAxis
                    dataKey="d"
                    interval={0}
                    tickLine={false}
                    axisLine={false}
                    fontSize={11}
                    stroke="var(--muted-foreground)"
                  />
                  <YAxis hide />
                  <Tooltip
                    cursor={{ fill: "var(--accent)" }}
                    contentStyle={{
                      borderRadius: 12,
                      border: "1px solid var(--border)",
                      background: "var(--card)",
                      color: "var(--card-foreground)",
                      fontSize: 12,
                      boxShadow: "var(--shadow-card)",
                    }}
                  />
                  <Bar isAnimationActive={false} dataKey="v" radius={8}>
                    {weeklyData.map((w) => (
                      <Cell key={w.d} fill={w.v === 8162 ? C.blue : "var(--muted)"} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
            <p className="text-xs text-muted-foreground">{t("day.peak")}</p>
          </div>

          <div className="card-surface p-4">
            <p className="text-sm font-semibold">{t("dev.title")}</p>
            <div className="mt-2 flex items-center gap-4">
              <div className="h-32 w-32 shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      isAnimationActive={false}
                      data={deviceData}
                      dataKey="value"
                      innerRadius={38}
                      outerRadius={58}
                      paddingAngle={3}
                      strokeWidth={0}
                    >
                      {deviceData.map((d) => (
                        <Cell key={d.name} fill={d.color} />
                      ))}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <ul className="min-w-0 flex-1 space-y-2">
                {deviceData.map((d) => (
                  <li key={d.name} className="flex min-w-0 items-center gap-2 text-sm">
                    <span
                      className="h-2.5 w-2.5 shrink-0 rounded-full"
                      style={{ backgroundColor: d.color }}
                    />
                    <span className="min-w-0 flex-1 truncate text-muted-foreground">{d.name}</span>
                    <span className="shrink-0 font-semibold">{d.value}%</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="card-surface overflow-hidden xl:col-span-2">
          <div className="flex items-center justify-between gap-3 p-4">
            <div className="flex items-center gap-2">
              <p className="truncate text-sm font-semibold">{t("top.courses")}</p>
              {realCoursesData?.content && realCoursesData.content.length > 0 && (
                <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary">
                  Live API
                </span>
              )}
            </div>
            <Link
              to="/courses"
              className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
            >
              <span>{t("nav.courses")}</span>
              <ArrowUpRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[620px] text-sm">
              <thead>
                <tr className="border-y text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="px-4 py-2.5 font-semibold">{t("th.id")}</th>
                  <th className="px-4 py-2.5 font-semibold">{t("th.course")}</th>
                  <th className="px-4 py-2.5 font-semibold">{t("th.students")}</th>
                  <th className="px-4 py-2.5 font-semibold">{t("th.watch")}</th>
                  <th className="px-4 py-2.5 font-semibold">{t("th.rating")}</th>
                </tr>
              </thead>
              <tbody>
                {displayCourses.map((c) => (
                  <tr
                    key={c.id}
                    onClick={() => setSelectedCourse(c)}
                    className="border-b last:border-0 hover:bg-secondary/60 transition-colors cursor-pointer group"
                  >
                    <td className="px-4 py-3 text-muted-foreground group-hover:text-foreground font-mono text-xs">
                      {c.id}
                    </td>
                    <td className="px-4 py-3">
                      <div className="min-w-0">
                        <p className="truncate font-semibold group-hover:text-primary transition-colors">
                          {c.name}
                        </p>
                        <p className="text-xs text-muted-foreground">{c.cat}</p>
                      </div>
                    </td>
                    <td className="px-4 py-3">{c.students.toLocaleString()}</td>
                    <td
                      className={`px-4 py-3 font-semibold ${c.up ? "text-success" : "text-destructive"}`}
                    >
                      {c.hours}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-between">
                        <span className="inline-flex items-center gap-1">
                          <Star className="h-3.5 w-3.5 fill-warning text-warning" />
                          {c.rating.toFixed(1)}
                        </span>
                        <ExternalLink className="h-3.5 w-3.5 opacity-0 group-hover:opacity-70 transition-opacity text-muted-foreground" />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="space-y-4">
          <div className="card-surface p-4">
            <p className="text-sm font-semibold">{t("comp.title")}</p>
            <div className="mt-3 flex items-end gap-3">
              <span className="text-4xl font-extrabold tracking-tight">68%</span>
              <span className="pb-1.5 text-xs text-muted-foreground">{t("comp.sub")}</span>
            </div>
            <div className="mt-3 h-2.5 overflow-hidden rounded-full bg-secondary">
              <div
                className="h-full rounded-full bg-primary transition-all duration-500"
                style={{ width: "68%" }}
              />
            </div>
          </div>

          <div className="card-surface p-4">
            <p className="text-sm font-semibold">{t("live.title")}</p>
            <ul className="mt-3 space-y-3">
              {upcoming.map((u) => (
                <li key={u.title} className="flex min-w-0 items-start gap-3">
                  <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-accent text-accent-foreground">
                    <Clock3 className="h-4 w-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{u.title}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {u.time} · {u.tutor}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      {/* Floating Detail Sheet (Slide-Over Inspector pattern) */}
      <FloatingDetailSheet
        open={Boolean(selectedCourse)}
        onOpenChange={(open) => {
          if (!open) setSelectedCourse(null);
        }}
        title={selectedCourse?.name ?? ""}
        description={
          selectedCourse
            ? `Course Code: ${selectedCourse.id} · Category: ${selectedCourse.cat}`
            : undefined
        }
        badge={
          selectedCourse && (
            <span className="inline-flex items-center rounded-md bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
              {selectedCourse.cat}
            </span>
          )
        }
        footerActions={
          <>
            <button
              type="button"
              onClick={() => setSelectedCourse(null)}
              className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium hover:bg-secondary cursor-pointer transition-colors"
            >
              Close
            </button>
            {selectedCourse?.realId ? (
              <Link
                to="/courses/$courseId"
                params={{ courseId: selectedCourse.realId }}
                className="rounded-lg bg-primary px-3.5 py-1.5 text-xs font-semibold text-primary-foreground shadow-xs hover:bg-primary/90 cursor-pointer transition-colors inline-flex items-center gap-1.5"
              >
                <span>Open Workspace</span>
                <ArrowUpRight className="h-3.5 w-3.5" />
              </Link>
            ) : (
              <Link
                to="/courses"
                className="rounded-lg bg-primary px-3.5 py-1.5 text-xs font-semibold text-primary-foreground shadow-xs hover:bg-primary/90 cursor-pointer transition-colors inline-flex items-center gap-1.5"
              >
                <span>Course Catalog</span>
                <ArrowUpRight className="h-3.5 w-3.5" />
              </Link>
            )}
          </>
        }
      >
        {selectedCourse && (
          <div className="space-y-6">
            {/* Quick Metrics */}
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-xl border border-border/60 bg-secondary/30 p-3">
                <p className="text-xs text-muted-foreground">Active Learners</p>
                <p className="text-xl font-bold mt-1">{selectedCourse.students.toLocaleString()}</p>
                <span className="text-[11px] text-success font-medium inline-flex items-center gap-0.5 mt-1">
                  <ArrowUpRight className="h-3 w-3" /> +12% this month
                </span>
              </div>
              <div className="rounded-xl border border-border/60 bg-secondary/30 p-3">
                <p className="text-xs text-muted-foreground">Watch Duration</p>
                <p className="text-xl font-bold mt-1">{selectedCourse.hours}</p>
                <span className="text-[11px] text-muted-foreground mt-1 block">
                  Across all modules
                </span>
              </div>
            </div>

            {/* Course Rating and Health */}
            <div className="rounded-xl border border-border/60 p-4 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  Course Satisfaction
                </span>
                <span className="inline-flex items-center gap-1 text-sm font-bold">
                  <Star className="h-4 w-4 fill-warning text-warning" />
                  {selectedCourse.rating.toFixed(1)} / 5.0
                </span>
              </div>
              <div className="h-2 w-full rounded-full bg-secondary overflow-hidden">
                <div
                  className="h-full bg-warning rounded-full"
                  style={{ width: `${(selectedCourse.rating / 5) * 100}%` }}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                Calculated from verified reviews and quiz completions.
              </p>
            </div>

            {/* Syllabus Overview */}
            <div className="space-y-2.5">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Syllabus Structure
              </h4>
              <div className="space-y-2">
                {[
                  { title: "Module 1: Foundations and Setup", items: "6 lessons · 2h 15m" },
                  { title: "Module 2: Practical Exercises", items: "8 lessons · 4h 30m" },
                  { title: "Module 3: Capstone Assessment", items: "1 project · 3h 00m" },
                ].map((mod, idx) => (
                  <div
                    key={mod.title}
                    className="flex items-center justify-between rounded-lg border border-border/40 p-3 text-sm bg-card/60"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className="grid h-6 w-6 place-items-center rounded-md bg-secondary text-xs font-semibold">
                        {idx + 1}
                      </span>
                      <span className="font-medium text-xs">{mod.title}</span>
                    </div>
                    <span className="text-xs text-muted-foreground">{mod.items}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Publication Status */}
            <div className="rounded-xl bg-success/10 border border-success/20 p-3.5 flex items-start gap-3">
              <CheckCircle2 className="h-4 w-4 text-success shrink-0 mt-0.5" />
              <div className="text-xs">
                <p className="font-semibold text-foreground">Course is Live and Published</p>
                <p className="text-muted-foreground mt-0.5">
                  Available in catalog with instant enrollment and certificate issuance.
                </p>
              </div>
            </div>
          </div>
        )}
      </FloatingDetailSheet>
    </div>
  );
}
