import { createFileRoute } from "@tanstack/react-router";
import { PlaceholderPage } from "@/components/dashboard/PlaceholderPage";

const title = "Instructors";
const description = "Your teaching team and their class load.";

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
    <PlaceholderPage title={title} description={description} pageKey="instructors" />
  ),
});
