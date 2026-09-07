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

## 1. Point authentication at the admin surface

`src/api/endpoints/auth.api.ts` calls `/api/v1/auth/login`, and `client.ts`
refreshes against `/api/v1/auth/refresh`. Those are the **learner** endpoints.

Administrators are a separate table with a separate sign-in:

```
POST /api/v1/admin/auth/login      → accessToken, refreshToken, permissions[]
POST /api/v1/admin/auth/refresh
POST /api/v1/admin/auth/logout
GET  /api/v1/admin/auth/me         → the admin, with roles
POST /api/v1/admin/auth/me/password
```

This is not a cosmetic swap. Tokens carry a `typ` claim, and the backend refuses
a learner token on every `/admin/**` route — so the dashboard cannot work at all
until this changes. A learner token reaching an admin endpoint gets **403**, and
an admin token reaching `/api/v1/me` gets **401**.

Seeded super admin for local work: `admin@elearning.local`. The password was
rotated out of the default; if the app logs *"the seeded super admin still has
the default development password"* at startup, it is still `change this password
now`.

### Refresh is single-use

`client.ts` already refreshes on 401 — check it does so **once** and queues
concurrent failures. Each refresh token works exactly once and returns its
replacement; presenting a spent one **revokes the entire session** as suspected
theft. Two parallel 401s that both refresh will log the user out.

---

## 2. Drive the UI from `permissions[]`

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
