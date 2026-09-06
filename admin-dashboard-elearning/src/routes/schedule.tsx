import { createFileRoute } from "@tanstack/react-router";
import { PlaceholderPage } from "@/components/dashboard/PlaceholderPage";

const title = "Schedule";
const description = "Live classes, deadlines and cohort calendars.";

export const Route = createFileRoute("/schedule")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => <PlaceholderPage title={title} description={description} pageKey="schedule" />,
});
