import { createFileRoute } from "@tanstack/react-router";
import { NotBuiltPage } from "@/components/dashboard/NotBuiltPage";

const title = "Help & Support";

/*
 * Kept as a route, removed from the navigation. Nothing in the API can fill it,
 * so it says so rather than rendering an empty page that looks like a failure.
 */
export const Route = createFileRoute("/_authenticated/support")({
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
      reason="There is no ticketing or contact model in the platform."
      wouldNeed="a support-request model, or simply a link to wherever your team already handles this. A page that pretends to submit a ticket into nothing would be worse."
    />
  ),
});
