import { createFileRoute } from "@tanstack/react-router";
import { NotBuiltPage } from "@/components/dashboard/NotBuiltPage";

const title = "Schedule";

/*
 * Kept as a route, removed from the navigation. Nothing in the API can fill it,
 * so it says so rather than rendering an empty page that looks like a failure.
 */
export const Route = createFileRoute("/schedule")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: "Not built yet." },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: () => (
    <NotBuiltPage
      title={title}
      reason="Nothing in the platform models a calendar. There is no cohort, live session or office-hours table, and no endpoint that could fill this page."
      wouldNeed="a scheduling model in the backend first — sessions with times, a cohort they belong to, and enrolment against them. This is a feature, not a wiring job."
    />
  ),
});
