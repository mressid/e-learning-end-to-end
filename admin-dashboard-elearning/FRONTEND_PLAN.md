# Admin dashboard — plan

Written against the backend as it stands (292 tests green, ten migrations). The
short version: the API layer here was generated from an OpenAPI document that
was both incomplete and **wrong** — that is now fixed (step 0) — and the
dashboard still signs in through the learner's door rather than its own.

Ordered so each step unblocks the next.

---

## 0. Regenerate `openapi.json` — **done**

The captured document described 73 paths against a backend serving 113. It is
now regenerated from the running app: **113 paths, 107 schemas, 32 of them under
`/api/v1/admin`**, and `src/api/types/openapi.ts` is generated from it.
`tsc --noEmit` passes.

Keeping it current is now one command, so it cannot silently rot again:

```bash
bun run api:sync            # fetch the spec, then regenerate the types
API_URL=http://host:port bun run api:sync    # against a non-default backend
```

`scripts/fetch-openapi.mjs` writes the document pretty-printed and refuses to
overwrite `openapi.json` with an empty or unreachable response — a truncated
spec would delete types rather than fail, which is the failure mode worth
guarding.

What arrived with the regeneration: every `/admin/*` endpoint (auth, admins,
roles, permissions, users, instructors, courses, categories, certificates,
submissions, media, audit, sessions), the five resumable multipart-upload
routes, and HLS playback at `/api/v1/items/{itemId}/lesson/stream.m3u8`.

The wrong shape is gone too. `SetPrerequisitesRequest` (a UUID array) no longer
exists; course prerequisites are `SetCoursePrerequisitesRequest`
(`{"prerequisites": ["Basic Python"]}`), while item prerequisites stay real
references as `SetItemPrerequisitesRequest`. Nothing in the dashboard referenced
the old name, so this cost nothing to correct — but only because no page had
been built on it yet.

`src/api/types/index.ts`, the hand-written alias façade, was missing 54 of the
107 schemas — it had drifted well before this. It now covers all of them.
**It is hand-maintained: `bun run api:sync` does not touch it.** When an endpoint
gains a schema, add the alias there too, or the generated type is present and
simply invisible to the rest of the app.

---

## 1. Point authentication at the admin surface — **done**

The dashboard signed in at `/api/v1/auth/login` and refreshed at
`/api/v1/auth/refresh`. Those are the **learner** endpoints, and no amount of
work above them would have helped: tokens carry a `typ` claim, so a learner
token is refused on every `/admin/**` route. Verified against the running
backend rather than assumed:

| check | result |
|---|---|
| `POST /api/v1/admin/auth/login` | 200, 16 permissions, `typ=admin`, 16 scope codes |
| `GET /api/v1/admin/auth/me` | 200, role `super-admin` |
| admin token → `GET /api/v1/me` | **401** |
| anonymous → `GET /api/v1/admin/users` | **401** |
| admin token → `GET /api/v1/admin/users` | **200** |
| refresh, first use | 200, token rotated, permissions recomputed |
| refresh, replayed | **401** — *and the rotated token died with it* |
| logout, then refresh | 204, then **401** |

What changed:

- `src/api/endpoints/admin-auth.api.ts` replaces `auth.api.ts`, which was
  deleted rather than kept. Every call in it (register, verify-email, password
  reset, `/me/profile`) is a learner route with no administrator equivalent —
  leaving it exported would have invited a page to be built on a 401.
- `client.ts` refreshes at `/api/v1/admin/auth/refresh` and no longer attempts a
  refresh when the failure came from the auth surface itself.
- **The token keys changed** to `lernova_admin_access_token` /
  `lernova_admin_refresh_token`. Sharing the learner app's key names on one
  origin meant a stale learner token would be sent to `/admin/**` and 403 every
  call, with nothing on screen explaining why. Anyone signed in under the old
  keys is signed out once, by design.

### Refresh is single-use — confirmed the hard way

Row 8 of that table is the one that matters. Replaying a spent refresh token
returned 401 *and killed the token that legitimately replaced it* — the whole
family is revoked as suspected theft. So two parallel 401s that each trigger a
refresh will sign the user out. `client.ts` holds a single in-flight
`refreshPromise` that concurrent callers await; that is load-bearing, not tidy.

### Session lifecycle

- `__root.tsx` has a `beforeLoad` guard: no admin token → `/login`, carrying
  `?redirect=` so sign-in resumes where the user was going. It is **client-side
  only**, because the token lives in localStorage and guarding during SSR would
  redirect every first paint to the sign-in page.
- The guard checks `typ=admin`, not merely that a token exists. A learner token
  in localStorage is not a session here whatever its presence suggests.
- The sign-in page only accepts a redirect target beginning with `/`. An
  absolute URL from the query string would be an open redirect.
- `client.ts` dispatches `lernova:auth-expired` on **every** failed-refresh path
  — including having no refresh token at all, which previously returned silently
  and left the user on a dashboard 401ing in the background.

### Not verified

The guard's redirect happens on hydration, so it was not exercised end to end —
SSR was confirmed to render `/`, `/courses` and `/login` at 200 without
crashing, and the redirect logic itself is only typechecked. Worth one manual
pass in a browser.

The sign-in form no longer prefills credentials; the "demo admin" button fills
the **seeded** pair (`admin@elearning.local` / `change this password now`). On
this machine that password has been rotated, so the button will not work here
until it is changed back or the button is updated.

---

## 2. Drive the UI from `permissions[]` — plumbing done, pages pending

`src/api/permissions.ts` and the `usePermissions()` hook exist:
`has(PERMISSIONS.COURSE_WRITE)` / `hasAny(...)`. Nothing consumes them yet — that
happens per page in step 3.

They read the **access token's `scope` claim**, not the `permissions[]` array
returned by login. Same codes, but the claim is what the backend itself reads to
allow or refuse, it survives a page reload without a fetch, and a refresh
reissues it with permissions as they stand — so a role change reaches a
signed-in administrator on its own. Confirmed on this backend: a super admin's
scope is expanded server-side to all 16 codes, so there is no "is super" case to
special-case on the client.

Login returns the administrator's permission codes. There are 16, all enforced
server-side:

```
course.read  course.write  course.publish  course.delete
user.read    user.suspend
certificate.read  certificate.revoke
submission.read
media.read   media.delete
category.manage  settings.manage  audit.read
review.moderate  discussion.moderate
```

Hide what the caller cannot do, but treat that as courtesy rather than security —
the server decides. A super admin holds all of them, including any added later.

`403` with `{"code": "PERMISSION_DENIED"}` means the permission is missing;
`{"code": "SUPER_ADMIN_ONLY"}` means the action is super-admin-gated (managing
roles and administrators) and is not grantable at all.

---

## 3. Wire the pages that now have an API

| Page | Endpoint | Permission |
| --- | --- | --- |
| `/students` | `GET/POST /admin/users`, `/{id}/status` | `user.read`, `user.suspend` |
| `/instructors` | `GET /admin/instructors` | `user.read` |
| `/courses` | `GET /admin/courses`, `/stats` | `course.read` |
| `/certificates` | `GET /admin/certificates` | `certificate.read` |
| `/submissions` (new) | `GET /admin/submissions` | `submission.read` |
| `/media` (new) | `GET/DELETE /admin/media` | `media.read`, `media.delete` |
| `/audit-logs` (new) | `GET /admin/audit` | `audit.read` |
| `/settings` → Sessions | `GET/DELETE /admin/sessions` | `settings.manage` |
| roles (new) | `/admin/roles`, `/admin/admins` | super admin only |

Every listing is a `PageResponse<T>`: `{content, page, size, totalElements,
totalPages, first, last}`. Zero-based `page`, `size` capped at 100.

`/messages`, `/schedule`, `/support` and `/analytics` have **no backend at all**.
Leave them, or drop them — do not build against endpoints that do not exist.

---

## 4. Media upload — use `@uppy/aws-s3`

Nothing here handles uploads yet. Do not write a bespoke uploader: bytes go
straight to storage and never through the API, and the multipart endpoints map
one-to-one onto what Uppy's S3 plugin calls.

```js
uppy.use(AwsS3, {
  shouldUseMultipart: (file) => file.size > 100 * 1024 * 1024,
  createMultipartUpload:   → POST   /media/uploads/multipart
  signPart:                → GET    /media/uploads/multipart/{id}/parts/{n}
  listParts:               → GET    /media/uploads/multipart/{id}/parts
  completeMultipartUpload: → POST   /media/uploads/multipart/{id}/complete
  abortMultipartUpload:    → DELETE /media/uploads/multipart/{id}
})
```

Below the threshold keep the single-shot flow (`POST /media/uploads` → PUT the
signed URL → `POST /media/{id}/complete`). No reason to do a three-round-trip
dance for a 200KB thumbnail.

Two rules the server enforces:

- **Thumbnails and avatars must be uploaded with `visibility: PUBLIC`.** A
  private object gets a URL that expires in 15 minutes, which is useless on a
  cached listing page, so the server refuses it rather than handing over a link
  that dies.
- `Content-Type` is signed into the upload URL. Send exactly what was declared or
  storage itself returns 403.

---

## 5. Video playback

```
GET /api/v1/items/{itemId}/lesson/stream.m3u8   → HLS manifest
GET /api/v1/items/{itemId}/lesson/content-url   → the original file
```

Use an HLS player (hls.js) against the first. The manifest is rewritten per
request with each segment signed for the caller, valid four hours — so **fetch it
when playback starts, not at page load**, and do not cache it across sessions.

`422 STREAM_NOT_READY` means transcoding has not finished. Fall back to
`content-url`; the lesson is still playable. Encoding is queued when a video is
attached to a lesson and runs in a separate worker, so expect a delay.

---

## Contract notes worth knowing

- **Errors** are always `{code, message, requestId, errors[]}`. Branch on `code`,
  never on `message` — messages are for humans and will change.
- `requestId` matches the `X-Request-Id` response header and the server logs. Put
  it in error toasts; it turns "it broke" into a searchable incident.
- **Drafts 404, they do not 403.** An unpublished course is invisible rather than
  forbidden, deliberately. With `course.read` the same request returns 200 — so a
  404 may mean "you lack the permission", not "it does not exist".
- **422 is a business rule**, 400 is malformed input. `ITEM_HAS_STUDENT_ACTIVITY`,
  `CATEGORY_IN_USE`, `LAST_SUPER_ADMIN`, `MEDIA_IN_USE` are all refusals worth
  surfacing verbatim — each explains something the user can act on.
- **Media ids never appear in student-facing responses.** Ask for a lesson's
  content URL; do not try to resolve media directly.

---

## Suggested order

1. Regenerate the spec and types — nothing is trustworthy until then
2. Swap auth to `/admin/auth/*`; confirm the login round trip end to end
3. Gate navigation on `permissions[]`
4. Wire the listings, cheapest first: instructors, users, courses, certificates
5. Audit log and sessions — both are plain reads over data that already exists
6. Roles and administrators — super-admin-only screens
7. Media library, then Uppy uploads
8. Video playback

Steps 1 and 2 are the blockers. Everything after them is independent.
