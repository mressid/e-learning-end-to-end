# Lernova — Instructor workspace

Where an instructor authors and manages the courses they teach.

## Why this is a separate app

The platform has two account systems, and they are not interchangeable:

| | table | signs in at | token | app |
| --- | --- | --- | --- | --- |
| dashboard staff | `admin_users` | `/api/v1/admin/auth/login` | `typ=admin` | `admin-dashboard-elearning` |
| learners & instructors | `users` | `/api/v1/auth/login` | `typ=user` | this one |

The backend refuses each token on the other's routes. Signing in to the admin
dashboard with an instructor account therefore fails with "invalid credentials"
— not because the password is wrong, but because the address is not in
`admin_users` at all, and the login deliberately does not say which of the two
it is.

Being two apps rather than two sections of one is what keeps that honest. One
origin means one `localStorage`, so a signed-in instructor and a signed-in
administrator would fight over the same slot. The token keys here are
`lernova_instructor_*`; the dashboard's are `lernova_admin_*`.

## Relationship to the dashboard

The design system is shared by copy, not by package: `src/components/ui`,
`src/lib/{utils,format,i18n,theme}` and the build config came from
`admin-dashboard-elearning` so the two look like siblings. What is *not* shared
is anything that knows about administrators — auth, the API modules, the
navigation and every page.

The cost of copying is drift. If the two ever need to change together often
enough to hurt, the fix is a workspace package, not hand-syncing.

## Running it

The backend must be up on `:8081`.

```bash
npm install
npm run dev          # :8083, alongside the dashboard on :8082
```

`src/api/types/openapi.ts` is generated. After any backend change to a
controller or a request/response record:

```bash
npm run api:sync     # fetches /v3/api-docs, regenerates the types
```

A stale copy does not fail loudly — it compiles against shapes the backend no
longer serves and breaks at runtime.

## What is here

- `/login` — platform sign-in.
- `/` — the courses you own or co-instruct, drafts included, and a sheet to
  create one.
- `/courses/$courseId` — title and descriptions, publish / return to draft, and
  a read-only list of the curriculum.

Courses are read from `GET /api/v1/courses/mine`, not `GET /api/v1/courses`.
The latter is public and published-only, so an author's unfinished work — the
reason they opened this app — is missing from it entirely.

## What is not here yet

Editing the curriculum (sections, items, lessons, media), quiz and assignment
authoring, the grading queue, enrolled students and their progress, course
discussions, and reviews. All of them have working backend endpoints; none has
a screen.
