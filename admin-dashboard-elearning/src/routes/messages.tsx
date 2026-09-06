import { createFileRoute } from "@tanstack/react-router";
import { PlaceholderPage } from "@/components/dashboard/PlaceholderPage";

const title = "Messages";
const description = "Conversations with students and instructors.";

export const Route = createFileRoute("/messages")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => <PlaceholderPage title={title} description={description} pageKey="messages" />,
});
