import { createFileRoute } from "@tanstack/react-router";
import { PlaceholderPage } from "@/components/dashboard/PlaceholderPage";

const title = "Students";
const description = "Manage learners, cohorts and enrollment status.";

export const Route = createFileRoute("/students")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => <PlaceholderPage title={title} description={description} pageKey="students" />,
});
