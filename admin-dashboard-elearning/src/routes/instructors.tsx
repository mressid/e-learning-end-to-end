import { createFileRoute, redirect } from "@tanstack/react-router";

/*
 * Moved into the people workspace.
 *
 * Learners, instructors, administrators, roles and sessions were answered in
 * four unlinked places; they are one subject and now share a context at
 * `/users`. The route stays so existing links and bookmarks land somewhere
 * useful instead of a 404.
 */
export const Route = createFileRoute("/instructors")({
  beforeLoad: () => {
    throw redirect({ to: "/users", search: { view: "instructors" } });
  },
  head: () => ({
    meta: [{ title: "Instructors — Lernova" }, { name: "robots", content: "noindex" }],
  }),
});
