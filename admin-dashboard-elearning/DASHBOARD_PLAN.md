# Dashboard — what is real and what is scenery

Follows on from `FRONTEND_PLAN.md`. All phases below are now done; this is kept
as the record of what was found and what was decided.

---

## Where it started, and where it is

| Route | Was | Now |
| --- | --- | --- |
| `/` | 663 lines, 2 API calls, invented revenue | 239 lines, every figure from an endpoint |
| `/analytics` | placeholder | real counts, and a statement of what is not measured |
| `/students` | placeholder | search, filter, suspend/reinstate, sign-out-everywhere |
| `/instructors` | placeholder | roster, read-only by necessity |
| `/certificates` | placeholder | list, filter, revoke behind a confirmation |
| `/settings` | theme, language, 3 switches saving nowhere | + password, sessions, roles, admins, audit |
| `/courses`, `/courses/$id` | real | unchanged, one latent bug fixed |
| `/schedule`, `/messages`, `/support` | placeholder | stated as not built, out of the nav |

The backend exposes **36 admin endpoints**. The dashboard called **five**. It
now calls **all 36** — 35 through the generated client, plus
`/admin/auth/refresh`, which `client.ts` calls with a raw `fetch` on purpose so
a 401 refresh cannot recurse through its own middleware.

**Verified end to end.** A throwaway role holding only `certificate.read` was
created, granted to a throwaway administrator, and used to sign in: the token's
scope claim carried exactly `certificate.read`, `/admin/certificates` answered
200, and users, courses, audit, sessions, roles and admins all answered 403.
That is precisely the split `usePermissions()` renders against. Both fixtures
were deleted afterwards.

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

**Settled: all three are out of the navigation, and the routes now say what is
missing and what building it would take.** They still resolve, so existing links
do not 404, but they no longer render a title over blank space — the difference
between "no data yet" and "this feature does not exist" is the difference
between waiting and investigating.

The topbar's bell went with them: no click handler, and a permanently lit unread
dot over an endpoint that answers 401 to every administrator. The global search
box went too — no handler, and no cross-entity search endpoint to give it.

---

## Phase A — the API layer — **done**

All 31 endpoints are reachable, typed and verified against the running backend:
13 read surfaces return 200, and every non-empty payload matches its declared
schema with no undeclared fields. Certificates, submissions, media and audit are
empty on this database, so those shapes are typed but not yet exercised.

Fixing this turned up a backend defect worth recording. `@Schema(name =
"PageResponse")` on a generic class pinned one name to every instantiation, so
springdoc erased the element type — the document claimed every paged endpoint
returned the same content, and any DTO appearing *only* as page content was
never emitted at all. `AdminCourseResponse`, `InstructorRosterEntry`,
`SessionResponse` and `AuditEntryResponse` were all invisible for that reason.
Removing the annotation took the document from 107 schemas to 127. It is a
documentation-only change: the JSON Jackson emits is unaffected, so a server
running the old bytecode serves responses identical to the new spec.

### Phase A as built

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

Built as four modules grouped by consuming page rather than eleven one-endpoint
files: `admin-directory`, `admin-catalog`, `admin-access`, `admin-platform`,
with hooks in `src/hooks/queries/useAdmin.ts`.

## Phase B — the pages that have a backend waiting

In dependency order, each shippable on its own:

1. ~~**`/students`**~~ — **done.** Search, status filter, suspend/reinstate, and
   sign-out-everywhere. Suspension and session revocation are separate actions
   because they are separate things: a suspended account's existing access token
   keeps working until it expires.
2. ~~**`/instructors`**~~ — **done.** Read-only by necessity, not by omission —
   the roster is derived from course ownership, so there is no membership to
   edit and the page says so.
3. ~~**`/certificates`**~~ — **done.** Validity filter and revoke, behind a
   confirmation stating that verification starts reporting it revoked, the
   record is kept, and there is no undo.
4. ~~**`/analytics`**~~ — **done.** Real counts only, with an explicit note on
   what the platform does not measure.

Two defects surfaced while building these:

- **The toaster was never mounted.** `ui/sonner.tsx` existed and nothing
  rendered it, so every toast the app raised went nowhere.
- **`usePermissions` read localStorage during render**, which the server cannot
  see — so every gated page server-rendered a refusal and corrected itself on
  hydration, flashing "you may not do this" at administrators who hold the
  permission. It now reports `isReady`, and `PermissionGate` waits.

Shared pieces now available: `PageHeader`, `PermissionGate`, `Pager`,
`StatusBadge`, `lib/format.ts`.

## Phase C — `/` (the dashboard home) — **done**

663 lines of which almost all the numbers are invented — revenue figures, device
splits, weekly traffic, an "upcoming" list. `/admin/courses/stats` gives counts
by status; learner and certificate counts come from their list endpoints.

**There is no revenue anywhere in this platform.** No price, no order, no
payment table. The revenue chart is not stale, it is fiction, and it should be
deleted rather than re-plumbed.

## Phase D — `/settings` — **done**

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

## Phase E — permission gating — **done**

`usePermissions()` exists and nothing consumes it. Once the pages are real, the
nav and their actions hide what this administrator cannot do. Courtesy, not
security — the server still decides, and `SUPER_ADMIN_ONLY` is not grantable at
all.

---

## Order

A → B1 → B2 → B3 → B4 → C → D → E, committing each. Phase A is the only hard
prerequisite; after it, any page can be taken in isolation.


---

## Two backend defects found by building against it

Both were invisible from the backend's own tests, which pass either way, and
both were found by checking generated types against the live server.

**1. `@Schema(name = "PageResponse")` erased every element type.** Pinning one
name to a generic class collapses all instantiations into one schema. The
document claimed every paged endpoint returned the same content, and any DTO
appearing *only* as page content was never emitted at all. Removing it took the
document from 107 schemas to 127.

**2. springdoc and Jackson disagreed about Kotlin's `is` prefix.** springdoc
applied the Java bean convention and documented `isSuper` as `super`; Jackson
serialises `isSuper`. Seven properties across six DTOs were affected. A client
generated from that document reads a field the server never sends and gets
`undefined`, silently — and it already was: `courses.$courseId.tsx` tested
`item.required`, so the "Required" badge could never appear. Fixed with explicit
`@get:JsonProperty`, which leaves the wire format untouched.

Both are documentation-only changes. `AdminCertificateResponse.valid` and
`CertificateVerificationResponse.valid` were checked and left alone — those
fields are genuinely named `valid`.

## What is still worth doing

- **Media library** — `admin-catalog` wires `GET /admin/media` and
  `DELETE /admin/media/{id}`, and nothing renders them yet. `media.read` and
  `media.delete` are enforced and unused.
- **Submissions** — same: wired, unrendered, `submission.read` unused.
- **Categories** — `category.manage` has a full client and no UI.
- **Course prerequisites** — free text now; the course editor does not expose
  them.
- **Resumable upload** — the five multipart routes are typed and unused; the
  course editor still has no upload control.
- **A browser pass.** Everything here is verified by typecheck, build, rendered
  markup and live API calls. Nothing has been clicked.
