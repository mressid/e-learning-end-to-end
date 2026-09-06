import { createFileRoute } from "@tanstack/react-router";
import { PlaceholderPage } from "@/components/dashboard/PlaceholderPage";

const title = "Certificates";
const description = "Issued certificates and completion records.";

export const Route = createFileRoute("/certificates")({
  head: () => ({
    meta: [
      { title: `${title} — Lernova` },
      { name: "description", content: description },
      { property: "og:title", content: `${title} — Lernova` },
      { property: "og:description", content: description },
    ],
  }),
  component: () => (
    <PlaceholderPage title={title} description={description} pageKey="certificates" />
  ),
});
