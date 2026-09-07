# Dashboard — what is real and what is scenery

Follows on from `FRONTEND_PLAN.md`, whose steps 0 and 1 are done: the OpenAPI
contract is current, and the dashboard signs in as an administrator.

This is the survey of what remains. The headline: **7 of 13 routes are a
placeholder component**, and the two largest real pages are substantially
mock data.

---

## The current state, measured

| Route | Lines | API calls | What it actually is |
| --- | ---: | ---: | --- |
| `/` | 663 | 2 | Mock. Revenue, devices, weekly traffic and "upcoming" are literals |
| `/courses` | 624 | 18 | Real |
| `/courses/$courseId` | 992 | 24 | Real |
| `/settings` | 199 | 0 | Theme + language + three switches that persist nowhere |
| `/students` | 17 | 0 | `PlaceholderPage` |
| `/instructors` | 19 | 0 | `PlaceholderPage` |
| `/certificates` | 19 | 0 | `PlaceholderPage` |
| `/analytics` | 17 | 0 | `PlaceholderPage` |
| `/messages` | 17 | 0 | `PlaceholderPage` |
| `/schedule` | 17 | 0 | `PlaceholderPage` |
| `/support` | 17 | 0 | `PlaceholderPage` |

The backend exposes **36 admin endpoints**. The dashboard currently calls
**five** of them, all under `/admin/auth`.

---

## Three pages have no backend, and never did

Worth settling before building, because no amount of frontend work fixes it.

**`/messages`** — there is no administrator inbox. Notifications live at
`/api/v1/me/notifications`, which is learner-scoped: an admin token is refused
there (`typ=admin` on a `/me/*` route returns 401, verified). Discussion threads
exist only per course — `/courses/{id}/threads` — with no global view and no
`/admin` equivalent. The permission `discussion.moderate` is real and enforced,
but it is exercised *inside a course*, not from an inbox.

> **This also means the topbar's notification bell cannot work.** It is
> currently a decorative dot over an endpoint this session gets 401 from.

**`/schedule`** — nothing in the API models a calendar, a cohort timetable or a
live session. There is no table for it either.

**`/support`** — no ticketing, no contact model.

Options, in order of honesty: drop them from the navigation; or keep the routes
with a stated "not built" panel rather than a title over blank space, which is
what `PlaceholderPage` renders today. **Recommendation: drop `/schedule` and
`/support` from the nav, and re-point `/messages` at per-course moderation from
inside a course** — the moderation queue the permission implies. Building three
backends is a separate project.

---

## Phase A — the API layer *(unblocks everything below)*

Nothing else can start until the client speaks to the 31 admin endpoints it
currently ignores. One module per area, matching the existing
`src/api/endpoints/*.api.ts` shape, plus hooks in `src/hooks/queries/`.

| Module | Endpoints | Permission |
| --- | --- | --- |
| `admin-users.api.ts` | `GET /admin/users`, `GET /admin/users/{id}`, `POST /admin/users/{id}/status` | `user.read`, `user.suspend` |
| `admin-instructors.api.ts` | `GET /admin/instructors` | `user.read` |
| `admin-courses.api.ts` | `GET /admin/courses`, `GET /admin/courses/stats` | `course.read` |
| `admin-certificates.api.ts` | `GET /admin/certificates`, `GET /admin/certificates/{id}` | `certificate.read` |
| `admin-submissions.api.ts` | `GET /admin/submissions` | `submission.read` |
| `admin-media.api.ts` | `GET /admin/media`, `DELETE /admin/media/{id}` | `media.read`, `media.delete` |
| `admin-roles.api.ts` | roles ×4, `GET /admin/permissions` | **super admin only** |
| `admin-accounts.api.ts` | admins ×5 | **super admin only** |
| `admin-audit.api.ts` | `GET /admin/audit` | `audit.read` |
| `admin-sessions.api.ts` | sessions ×5 | `settings.manage` |
| `admin-taxonomy.api.ts` | categories ×3 | `category.manage` |

Deliverable: every endpoint reachable and typed. No UI yet.

## Phase B — the pages that have a backend waiting

In dependency order, each shippable on its own:

1. **`/students`** — list, search, detail, suspend/reinstate. The largest single
   gap: `user.read` and `user.suspend` are enforced and entirely unused.
2. **`/instructors`** — list, busiest first. Small; mostly a table.
3. **`/certificates`** — issued certificates, with revoke.
4. **`/analytics`** — rebuild on `GET /admin/courses/stats` plus real counts.
   Whatever it cannot source, it should not display.

## Phase C — `/` (the dashboard home)

663 lines of which almost all the numbers are invented — revenue figures, device
splits, weekly traffic, an "upcoming" list. `/admin/courses/stats` gives counts
by status; learner and certificate counts come from their list endpoints.

**There is no revenue anywhere in this platform.** No price, no order, no
payment table. The revenue chart is not stale, it is fiction, and it should be
deleted rather than re-plumbed.

## Phase D — `/settings`

Currently theme, language, and three notification switches that save nowhere.
It should host what the API actually offers:

- change your own password — `POST /admin/auth/me/password`
- administrators and roles — super-admin only, the whole of Phase A's
  `admin-roles` / `admin-accounts`
- live sessions, learner and admin, with the ability to end one —
  `settings.manage`
- the audit trail — `audit.read`
- categories and tags — `category.manage`

The three fake switches should go, or be stated as not yet wired.

## Phase E — permission gating

`usePermissions()` exists and nothing consumes it. Once the pages are real, the
nav and their actions hide what this administrator cannot do. Courtesy, not
security — the server still decides, and `SUPER_ADMIN_ONLY` is not grantable at
all.

---

## Order

A → B1 → B2 → B3 → B4 → C → D → E, committing each. Phase A is the only hard
prerequisite; after it, any page can be taken in isolation.
