import { createFileRoute } from "@tanstack/react-router";
import { NotBuiltPage } from "@/components/dashboard/NotBuiltPage";

const title = "Messages";

/*
 * Kept as a route, removed from the navigation. Nothing in the API can fill it,
 * so it says so rather than rendering an empty page that looks like a failure.
 */
export const Route = createFileRoute("/_authenticated/messages")({
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
      reason="There is no administrator inbox. Learner notifications live under /me/*, which refuses an administrator's token outright, and discussion threads exist only inside a course — there is no cross-course view of them."
      wouldNeed="either an admin-scoped moderation queue over discussion threads, which the discussion.moderate permission already implies, or a messaging model that does not exist yet. Until then, moderate discussions from inside the course they belong to."
    />
  ),
});
