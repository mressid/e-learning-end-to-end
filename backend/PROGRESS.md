# Backend Progress

Working checklist for the E-Learning backend. Architecture rules live in
[AGENT.md](AGENT.md); the schema and domain model live in [desgin/](desgin/).

Status: `[x]` done · `[~]` partial · `[ ]` not started

---

## Local development

```bash
cp .env.example .env      # fill in the passwords, then:
docker compose up -d      # postgres, redis, minio, rabbitmq, mailpit
./gradlew bootRun         # reads .env automatically, serves on :8081
```

`.env` is imported by the `dev` profile via `spring.config.import`, so no
`source .env` and no IntelliJ EnvFile plugin is needed. Real environment
variables override anything in `.env`. If `.env` is missing the app fails
immediately and says so.

### Try it

Accounts start `PENDING`, so registering is not enough to sign in — the address
has to be confirmed first. Locally the link lands in Mailpit
(http://localhost:8026); copy the `token` out of it.

```bash
curl -X POST localhost:8081/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","username":"you","password":"correct horse battery staple"}'

# Without this, login answers 403 EMAIL_NOT_VERIFIED rather than a token.
curl -X POST localhost:8081/api/v1/auth/verify-email -H 'Content-Type: application/json' \
  -d '{"token":"<paste from the Mailpit message>"}'

TOKEN=$(curl -s -X POST localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"correct horse battery staple"}' | jq -r .accessToken)

curl localhost:8081/api/v1/me -H "Authorization: Bearer $TOKEN"
```

To skip the round trip while poking at other endpoints, set
`REQUIRE_EMAIL_VERIFICATION=false` in `.env`; accounts are then created `ACTIVE`
and no mail is sent.

The login response also carries a `refreshToken`. It works **once** — `POST
/api/v1/auth/refresh` returns its replacement, and presenting a spent one
revokes the whole session.

### What is here

| Module | Surface |
| --- | --- |
| `identity` | register, sign in, verify, reset, profile, rotating sessions |
| `courses` | authoring, publication, taxonomy, structure, prerequisites |
| `learning` | enrolment, progress, lessons, certificates, resources, access windows |
| `assessment` | quizzes with auto and manual grading, assignments |
| `community` | discussions and reviews, gated by participation |
| `platform/media` | presigned uploads, asset library, reference-guarded deletes |
| `platform/transcode` | HLS ladder in a worker, signed playback (§10) |
| `platform/notifications` | domain events, RabbitMQ delivery, Mailgun |
| `platform/audit` | append-only trail of privileged action |
| `admin` | administrators, roles, 16 permissions, sessions (§8) |

Nine migrations, 285 integration tests against real PostgreSQL, MinIO and
RabbitMQ. Two container images: the API, and a worker that carries ffmpeg.

### Sending real email

Mailpit is the default and needs no credentials — it catches everything at
http://localhost:8026. To send through Mailgun instead:

```bash
MAIL_PROVIDER=mailgun
MAILGUN_API_KEY=<private API key>
MAILGUN_DOMAIN=<your sending domain>
MAIL_FROM=no-reply@<your sending domain>   # must be ON that domain
APP_BASE_URL=http://localhost:3000         # the FRONT END, not the API
# MAILGUN_BASE_URL=https://api.eu.mailgun.net   # only for EU-region domains
```

Four things that each fail in a way that does not point at itself:

- **Region.** US and EU are separate stacks and a domain lives in exactly one.
  The wrong host returns `401`, which reads like a bad key. Settle it with
  `curl --user api:$KEY https://api.mailgun.net/v3/domains/$DOMAIN` — 200 means
  US, 404 means try the EU host.
- **`MAIL_FROM` must be on the Mailgun domain.** An address on a domain you do
  not own is rejected regardless of whether the key is valid.
- **Sandbox domains only deliver to authorized recipients**, added in the
  Mailgun dashboard. Anything else is a `403` whose body says so.
- **`APP_BASE_URL` is the front end.** Links are built as
  `{APP_BASE_URL}/verify-email?token=…` and the user lands on a page that posts
  the token back to the API. Point it at `:8081` and every emailed link is dead.

A failed send never fails the request — it is a `WARN` carrying Mailgun's own
explanation. If mail does not arrive, grep the log for
`Could not send verification mail`.

### Ports

Shifted off the defaults so this stack coexists with other local services.
All bound to `127.0.0.1` only. Override any of them in `.env`.

| Service | Port | URL / notes |
| --- | --- | --- |
| API | 8081 | http://localhost:8081 |
| Swagger UI | 8081 | http://localhost:8081/swagger-ui.html |
| OpenAPI spec | 8081 | http://localhost:8081/v3/api-docs |
| PostgreSQL | 5433 | `elearning` / db `elearning` |
| Redis | 6380 | password-protected |
| MinIO API | 9100 | S3 endpoint, path-style |
| MinIO console | 9101 | http://localhost:9101 |
| RabbitMQ AMQP | 5673 | |
| RabbitMQ UI | 15673 | http://localhost:15673 |
| Mailpit SMTP | 1026 | catches all outbound mail |
| Mailpit UI | 8026 | http://localhost:8026 |

---

## 1. Infrastructure

- [x] Docker Compose: PostgreSQL 17, Redis 7, MinIO, RabbitMQ 4, Mailpit
- [x] Healthchecks on every stateful service
- [x] `minio-init` one-shot creates the `elearning` and `elearning-public` buckets
- [x] Credentials from `.env`; compose refuses to start with unset passwords
- [x] `.env` git-ignored, `.env.example` committed
- [ ] Prometheus + Grafana (deliberately deferred — add when there is something to measure)
- [x] Dockerfile — multi-stage, layered jar, non-root, container-aware heap.
      **Image layout verified statically, never run** (see Verification gaps)
- [x] `Dockerfile.worker` — the same jar, a runtime image that also carries the
      ffmpeg binary, and a lower heap share because FFmpeg is a child process
      whose memory is not the JVM's. Separate from the API image because that is
      ~100MB the API never invokes, and the two scale on different signals
- [x] `transcode-worker` in compose, behind a `worker` profile, so the default
      stack stays infrastructure-only and needs no application image built.
      `docker compose --profile worker up -d --scale transcode-worker=3`

## 2. Configuration

- [x] `application.yaml` — base, env-driven, no secrets, no hostnames
- [x] `application-dev.yaml` — local compose ports, SQL logging
- [x] Hikari pool, `ddl-auto: validate` (Flyway owns the schema), `open-in-view: false`
- [x] Error responses never leak messages, stack traces, or exception types
- [x] Redis / RabbitMQ / mail wired, RabbitMQ listener retry + no requeue-on-reject
- [x] Mail provider selected by `elearning.mail.provider` (`smtp` | `mailgun`),
      both senders `@ConditionalOnProperty` so exactly one bean exists. SMTP is
      the default so a fresh clone runs against Mailpit with no credentials
- [x] `application-test.yaml` — no `.env`, database from Testcontainers, CI-safe
- [x] `.env` imported by the dev profile; missing `.env` fails with a message
      that names the cause (Spring binds unresolved `${...}` literally, so a
      missing value would otherwise look like a wrong password)
- [x] `@ConfigurationProperties` for `elearning.*`: `StorageProperties`,
      `EmailProperties`, `ApiProperties`, `IdentityProperties`, `JwtProperties`
      — storage/mail validated at startup
- [x] `application-prod.yaml` — no `.env` import, no secret fallbacks, OpenAPI
      off by default, `forward-headers-strategy` for running behind a proxy

## 3. API surface

- [x] springdoc OpenAPI 3.1 at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`
- [x] Scoped to `/api/**`, actuator excluded, deterministic key ordering
- [x] Document metadata: "E-Learning Platform API" v1, `bearerAuth` JWT scheme
      declared in components (referenced once operations exist)
- [x] `GlobalExceptionHandler` returning `{code, message, requestId, errors}` (§15)
- [x] Security-layer 401/403 return the same `ApiError` body — Spring Security
      rejects inside the filter chain, before `@RestControllerAdvice`, so it
      needed its own entry point and access-denied handler
- [x] `RequestIdFilter` — `X-Request-Id` echoed on every response, carried in
      the error body and the log MDC; inbound ids are sanitised before logging
- [x] `ApiError` now published in the OpenAPI schemas, along with the identity
      request/response models
- [x] `PageResponse<T>` — one pagination envelope for every collection endpoint
      (§28). Spring Data's `Page` is not returned directly: its JSON shape is an
      implementation detail that would leak the persistence library into the
      public contract
- [x] Spring's own request failures now map to correct statuses instead of being
      swallowed as 500s by the catch-all: bad UUID → 400, malformed JSON → 400,
      out-of-range page size → 400 with a field error, wrong method → 405
- [x] `DataIntegrityViolationException` → 409, not 500: a constraint violation
      that slips past an application check is a conflict about the request, and
      the driver's message can quote table and column names, so it is logged
      rather than returned

## 4. Security

- [x] `SecurityConfig` — deny by default, stateless, CSRF off, no form login / basic auth
- [x] Public: OpenAPI docs, Swagger UI, `/actuator/health`, `/actuator/info`
- [x] `PasswordEncoder` bean — delegating encoder (bcrypt by default), so the
      algorithm can change later without invalidating stored hashes
- [x] JWT resource server wired: HS256, secret from `JWT_SECRET` (no default —
      a signing key must never fall back to a built-in value)
- [x] `JwtConfig` issues and verifies tokens; symmetric while one app does both,
      swappable to RSA without touching the rest of the security config
- [x] Malformed/expired tokens return the standard `ApiError` — the resource
      server installs its own entry point, which had to be overridden too
- [x] **Refresh tokens, logout, revocation.** An access token cannot be
      withdrawn — the resource server checks a signature, not a table — so its
      lifetime dropped to 15 minutes and the refresh token carries the revocable
      half of the session. "Logout" means revoking here and waiting out those
      minutes, not pretending a signed JWT can be recalled
- [x] **Rotation with reuse detection.** Each refresh token works exactly once
      and its replacement comes back in the response. That is what makes theft
      detectable at all: a stolen token and the real one cannot both work, so
      whichever arrives second is already spent — and the whole family is then
      revoked, because there is no way to tell which holder is genuine. Logging
      the victim out beats leaving the thief a live session
- [x] The revocation commits in its **own transaction** (`REQUIRES_NEW`, in a
      separate bean so the proxy applies). Found by a failing test: the kill was
      written inside the transaction that then threw the 401, so the rollback
      undid it and the session stayed alive. Same shape as the notification
      listener bug, and a self-call would have reintroduced it silently
- [x] Refresh re-checks the account, so one suspended an hour ago stops minting
      access tokens at its next refresh
- [x] Separate logins are separate families: signing out on one device does not
      sign the person out everywhere
- [x] Resource-level authorization for courses (`CourseAuthorization`) — see
      the courses module below
- [x] Anonymous GET allowed on course discovery paths only; writes stay
      authenticated
- [x] Resource-level authorization in every module: courses by ownership,
      learning by enrolment, assessment by enrolment/editorship, community by
      participation, media by uploader. No module consults a bare role (§11)

## 5. Database

- [x] Flyway wired, `clean-disabled`, migrations at `classpath:db/migration`
- [x] `V1__initial_schema.sql` — all 40 tables from the data model doc:
      68 foreign keys, 18 unique constraints, 101 indexes
- [x] `media_objects` with a unique `(bucket, object_key)`
- [x] Enums as `varchar` + `CHECK` rather than native PG enum types, so adding a
      value is an ordinary migration instead of an `ALTER TYPE`
- [x] `courses.search_vector` — generated `tsvector` + GIN index, so PostgreSQL
      FTS needs no triggers or application code (Stack.md)
- [x] Partial index on unread notifications; GIN index on `notifications.data`
- [x] `position` uniqueness is `DEFERRABLE INITIALLY DEFERRED` so reordering
      within a transaction does not trip over itself
- [x] `V3__seed_categories.sql` — 8 top-level categories and 18 children. The
      read-only category API is unusable without this: an empty table means
      nothing can be categorised. `ON CONFLICT (slug) DO NOTHING` so it can land
      on a database where an operator already inserted some by hand. Tags need
      no seed — they are created on demand by the editors who use them
- [x] **Seeding has since moved out of migrations** into one JSON-driven
      component — see §9. V3 and V6 remain as the history of how the first copy
      arrived; Flyway migrations are immutable, so their inserts cannot be
      emptied retroactively
- [x] `V5__course_prerequisite_notes.sql` — drops `course_prerequisites` and
      replaces it with free text. V1 modelled prerequisites as course → course
      foreign keys; see the courses module for why that shape had no safe
      failure mode. `item_prerequisites` is untouched
- [x] `V6__admin_roles.sql` — `admin_users`, `admin_refresh_tokens`,
      `permissions`, `roles`, `role_permissions`, `admin_user_roles`, the seeded
      permission catalogue, the super-admin role and a bootstrap account. Seed
      values are Flyway placeholders, so production supplies its own by
      environment rather than by editing a migration (§8)
- [x] `V7__audit_log.sql` — an append-only trail, deliberately without a
      foreign key on the actor so a record outlives the account that made it
- [x] `V8__course_access_duration.sql` — `courses.access_duration_days`, the
      policy that V1's `enrollments.expires_at` had been waiting for

## 6. Modules

All modules are implemented. `identity` was the first vertical slice and remains
the reference for the shape.

Only create
subpackages (`api/application/domain/infrastructure`) when a module is big
enough to need them (AGENT.md §5).

- [x] `identity` — registration, login, verification, reset, profile, sessions
  - [x] `User` / `UserProfile` entities, validated against the schema by
        `ddl-auto: validate`
  - [x] `UserRegistrationService` — one transaction per business operation (§14)
  - [x] `AuthenticationService` — issues JWTs; unknown account and wrong password
        return the same code, and the password is hashed either way so response
        timing does not reveal whether an account exists
  - [x] `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/me`
  - [x] 7 integration tests, real PostgreSQL + real JWT verification
  - [x] **Email verification** — accounts now start `PENDING` and are activated
        by the emailed link. `POST /auth/verify-email`, `/verify-email/resend`
  - [x] The login gate names `EMAIL_NOT_VERIFIED` **only once the password is
        already known to be correct**. At that point the caller has proved they
        hold the credentials, so the reason reveals nothing they could not
        establish anyway — while a wrong password on a pending account is still
        an indistinguishable `INVALID_CREDENTIALS`. Suspended and disabled stay
        indistinguishable too: unlike a pending address, that is not the
        caller's state to learn and there is nothing they can do about it
  - [x] Verification mail is sent **after** registration commits and can never
        fail it: an account whose mail bounced is recoverable with a resend,
        whereas a failed register loses the account and leaves the address taken
  - [x] Resending spends the previous link — two live links for one address
        means a leaked older one still works after the newer was used
  - [x] A second click reports `ALREADY_VERIFIED` rather than an error implying
        forgery: double-clicking a link is a normal thing for a person to do
  - [x] **Profile** — `GET/PATCH /api/v1/me/profile`. The row was created at
        registration and then unreachable: nobody could set a display name or an
        avatar afterwards. PATCH changes only the fields present, so a client
        sending one key cannot blank the rest
  - [x] An avatar must be the caller's own completed upload **and** PUBLIC —
        same rule as course thumbnails: it is shown wherever the user appears,
        so a presigned URL that expires in 15 minutes is useless. A private
        object is rejected rather than given a URL that dies
  - [x] **Password reset** — `POST /auth/password-reset`, `/password-reset/confirm`.
        A link in an inbox is a standing takeover of the account, which drives
        all three rules: short TTL (30m), single use, and redeeming it **revokes
        every refresh token** the user holds. A reset that left old sessions
        alive would not lock out whoever prompted it — the one thing a reset
        exists to do
  - [x] Both the reset request and the verification resend answer identically
        whether or not the address is registered; anything else makes an
        unauthenticated endpoint a membership oracle
  - [x] A completed reset also settles a `PENDING` registration — receiving mail
        at the address proves control of it as well as the verification link does
  - [x] Tokens are stored as **SHA-256, never in the clear** (`SecureTokens`),
        so a dump of these tables hands out no live sessions and no account
        takeovers. SHA-256 rather than bcrypt is deliberate and the opposite of
        the choice for `users.password_hash`: these are 256-bit server-generated
        values with no dictionary to run against them, and a slow KDF would cost
        real time on every refresh while buying nothing
  - [x] `V4__auth_tokens.sql` — `refresh_tokens` (with `family_id` for the
        rotation chain), `email_verification_tokens`, `password_reset_tokens`
  - [x] 22 integration tests (10 refresh/logout, 12 verification/reset)
  - [x] Roles were deliberately not invented here, and still are not:
        instructor-ness remains course ownership and `course_instructors`.
        Platform administration is a separate concern with a separate table —
        see §8. Learners have no path to a role at all
- [x] `courses` — authoring, publication, discovery, taxonomy, structure
  - [x] `Course` / `CourseSection` / `CourseItem` / `CourseInstructor` entities
  - [x] `ownerId` is a plain UUID, not a `User` association: courses must not
        depend on identity's entities (§8)
  - [x] Publication rules live on the entity (`Course.publish`), not the service
  - [x] `CourseAuthorization` — authority comes from the relationship to *this*
        course (owner or co-instructor), never from a bare role (§11)
  - [x] Drafts return **404, not 403**, to strangers: a 403 would confirm the
        course exists
  - [x] Slugs generated once from the title, accent-stripped, uniqueness-suffixed;
        never regenerated, so renaming cannot break existing links
  - [x] PostgreSQL full-text search over the generated `search_vector`, using
        `plainto_tsquery` so user input cannot inject query operators
  - [x] `POST/GET/PATCH /api/v1/courses`, `/publish`, `/unpublish`,
        `/{id}/sections`, `/api/v1/sections/{id}/items`
  - [x] 21 integration tests
  - [x] **Co-instructor management** — `POST/GET/DELETE
        /api/v1/courses/{id}/instructors`. `CourseAuthorization` had always
        granted edit rights to anyone in `course_instructors`, but nothing could
        write a row there, so the capability was unreachable
  - [x] Managing the roster is **owner-only**: if a co-instructor could add
        co-instructors, one assistant could quietly hand out authority over
        somebody else's course. Listing stays open to any editor
  - [x] The owner is refused as an instructor row — their authority comes from
        `courses.owner_id`, and duplicating it would create two sources of truth
        for the same person. Adding twice updates the role instead of duplicating
  - [x] `UserDirectory` port — courses asks identity the one thing it needs
        (does this user exist), never touching `User` (§8)
  - [x] 8 integration tests (6 roster, 2 profile)
  - [x] **Categories** — `GET /api/v1/categories` (public, flat, `parentId`
        carries the tree) and `PUT /api/v1/courses/{id}/categories`. Curated
        reference data seeded by migration: a shared browse tree only means
        something if one authority decides what is in it, and V1 has no admin
        role to grant that (§11), so there is deliberately **no create endpoint**
  - [x] **Tags** — `GET /api/v1/tags` and `PUT /api/v1/courses/{id}/tags`.
        Unlike categories these are minted on demand by whoever tags their own
        course, which needs no admin concept: a tag structures nothing outside
        the courses carrying it. Slug is the identity, so "Machine Learning",
        "machine learning" and "Machine-Learning" are one tag rather than three
        near-duplicates no filter could group
  - [x] Both are set **wholesale with PUT**, not add/remove: an editor picking
        from a form sends the list they want, and only a replacing write makes
        removing the last one expressible
  - [x] An unknown category id is a 404, not a quiet skip — silently dropping it
        would leave the editor believing it was applied
  - [x] Taxonomy on the listing is resolved in **one batched query per kind**,
        the same way thumbnails already are: per-row lookups would be an N+1 on
        the busiest endpoint on the platform
  - [x] `SlugGenerator.slugify` took a length bound and became shared — the cap
        was hardcoded at 200, which would overflow `tags.slug` (varchar 80)
  - [x] 10 integration tests
  - [x] **Course prerequisites are free text** — `PUT/GET
        /api/v1/courses/{id}/prerequisites` takes and returns sentences
        ("Basic Python", "comfortable with matrices"), in the author's order,
        and **enforces nothing**
  - [x] They were foreign keys with an enrolment gate, and that shape had no
        safe failure mode. Nothing stopped the required course being unpublished
        afterwards, and when it was, the gated course silently stopped accepting
        new students with no signal to its owner. Both repairs were worse than
        the disease: refusing the unpublish holds one author hostage to a
        dependency they never agreed to, and quietly dropping the requirement
        rewrites a course's pedagogy behind its author's back
  - [x] Nothing even required the two courses to share an owner — anyone could
        make their course depend on somebody else's without asking. Free text
        removes the whole class of problem: a sentence addressed to a
        prospective student cannot be broken by someone else's publish decision
  - [x] Blank entries and case-duplicates are dropped rather than rendered as
        empty or repeated bullets — both are typos, not intent
  - [x] **Item prerequisites stay enforced**, `PUT/GET
        /api/v1/items/{id}/prerequisites`. The hazard does not exist there: both
        items sit in one course under one owner, so no second author's publish
        decision can break the requirement, and "watch the video before the
        quiz" is a guarantee worth keeping
  - [x] **Cycle detection** on items. The table carries a CHECK rejecting a
        self-reference, but a constraint sees one row: it cannot notice that A
        requires B while B already requires A, which would leave both
        permanently locked. The service walks the existing edges breadth-first
        from the proposed prerequisites and refuses if the walk arrives back at
        the target. A `visited` set makes it terminate even on a graph that
        already contains a loop — a walk assuming a clean graph would hang on a
        dirty one
  - [x] An item's prerequisites must be **in the same course**, or the item
        could never be unlocked from inside it
  - [x] Reads follow `requireCanView`, so a draft's prerequisites 404 to
        strangers rather than confirming the draft exists
  - [x] 10 integration tests
  - [x] Reordering sections and items (a reorder must be a permutation)
  - [x] **`POST /{id}/archive`** — the service method existed and was correct
        but no route reached it. Archiving drops the course out of discovery
        (which lists PUBLISHED only, so no extra filtering was needed) and
        refuses new enrolments; students already enrolled keep their access and
        their progress, because `isPublished` is consulted at enrolment and
        nowhere else. Retiring a course must not take back what someone started
  - [x] Archived courses 404 to strangers by the same rule drafts follow, and
        restoring one is deliberate: `publish` refuses ARCHIVED, so a course has
        to be returned to draft first. That transition is now pinned by a test
        rather than left as an accident of `unpublish` having no guard
  - [x] **Delete endpoints** — `DELETE /api/v1/items/{id}` and
        `/api/v1/sections/{id}`. A section cascades to its items, as decided
  - [x] **Refused once a student has touched it.** `course_items` cascades into
        `lessons`, `quizzes` and `assignments`, and onward into quiz attempts,
        quiz responses, assignment submissions and `learning_progress`. So
        deleting a worked-through item does not tidy a course, it destroys
        graded student work with no way back. Authoring material is the editor's
        to remove; a student's answers and marks are not
  - [x] Section deletes are **all or nothing**: one worked-on item refuses the
        whole thing, rather than leaving a half-emptied section whose survivors
        are whichever items happened to have submissions
  - [x] `ItemActivityProbe` — courses declares the question ("has anyone touched
        these?") and learning and assessment each register an answer, so courses
        depends on neither (§8). A new kind of student record means a new probe,
        not an edit to the delete path. Asked as a batch because a section
        delete covers every item under it
  - [x] The refusal names what is in the way ("student progress", "quiz attempts
        or assignment submissions") — a bare 422 would leave the editor guessing
  - [x] 7 integration tests
- [~] `learning` — enrolments and progress
  - [x] `Enrollment` / `LearningProgress` entities
  - [x] **`CourseCatalog` port** — learning declares the eight things it needs
        to know about courses and nothing more, so it never touches `Course`
        internals. The adapter lives in `learning.infrastructure`, keeping the
        dependency one-directional: learning → courses, never the reverse (§8)
  - [x] Enrolment rules: published courses only, one per student/course,
        re-enrolling resumes the original record so past progress survives
  - [x] **Locked items refuse progress** until their prerequisites are complete.
        Enforced inside `ProgressService.record` rather than at the controller,
        because that is the single chokepoint: a quiz pass and an assignment
        grade both complete their item through the same use case, so a gate
        anywhere else would be bypassable by finishing the quiz instead
  - [x] Progress is monotonic — a late-arriving event from earlier in a video
        cannot walk the percentage back, and re-watching cannot un-complete
  - [x] Completion counts **required** items only; finishing the last one
        completes the enrolment. An empty course is never "complete"
  - [x] Authorization is by enrolment, not by role: progress can only be recorded
        against a course the student is actively enrolled in
  - [x] `POST /api/v1/courses/{id}/enroll`, `GET /api/v1/me/enrollments`,
        `POST /api/v1/enrollments/{id}/cancel`,
        `POST /api/v1/items/{id}/progress`, `GET /api/v1/courses/{id}/progress`
  - [x] 10 integration tests
  - [x] **Lesson content** — `Lesson` + `VideoContent`/`ArticleContent`/
        `DocumentContent`, sharing the course item's primary key
  - [x] `PUT/GET /api/v1/items/{id}/lesson`, `GET .../lesson/content-url`
  - [x] Authoring is for course editors; reading needs an active enrolment
  - [x] Media ids never leave the server: a student asks for "this lesson's
        file" and gets a presigned URL, with access decided by their enrolment
  - [x] Attachment guard — a file must be fully uploaded *and* uploaded by
        someone who can edit that course, so a course editor who guessed another
        user's media UUID cannot republish their file to students
  - [x] AUDIO/EXTERNAL rejected rather than half-saved: V1 has no content table
        for them, so a saved lesson could never be read back
  - [x] 11 integration tests
  - [x] **Certificates** — issued in the same transaction as completion, so both
        commit together; idempotent, so re-completion mints no second certificate.
        Number is human-quotable and random (a counter would leak issuance
        volume); verification code is separate and unguessable so the public
        check cannot be brute-forced. Staff revocation. `GET /certificates/verify/{code}`
        is public — an employer with a printed certificate needs no account.
  - [x] `EnrollmentExpirySweeper` moves lapsed enrolments to EXPIRED
  - [x] **Access duration, per course** (`V8__course_access_duration.sql`).
        `enrollments.expires_at` had been in the schema since V1 with nothing
        writing it, so `isClosed()` never fired on it and the sweeper swept
        nothing — the mechanism was complete and only the policy was missing.
        `courses.access_duration_days` supplies it; NULL means access never
        lapses, which is both the sensible default and what every existing
        enrolment already had
  - [x] The window is **copied onto the enrolment at enrolment**, not read live
        from the course. Reading it live would let an author shorten a course's
        duration and retroactively cut short access people already held, or
        lengthen it and quietly reopen deals that had closed. The deal somebody
        already has is not the author's to revise
  - [x] **Re-enrolling starts a fresh window.** This was a live trap:
        `reactivate()` set ACTIVE without touching `expiresAt`, and `isClosed()`
        reads that field whatever the status says — so a lapsed enrolment would
        have come back reporting ACTIVE and refused every request. Dormant only
        because the column was always null
  - [x] `EnrollmentExpirySweeper` now has something to do, and its role is
        unchanged: `isClosed()` is what refuses the work, the sweep is what
        stops listings showing a stale ACTIVE
  - [x] 7 integration tests
  - [x] **Resources** — reusable materials independent of any course, with FILE,
        URL and INLINE sources, attachable at course/section/item scope with a
        `relationshipType` saying why. Knowing a resource id grants nothing: you
        must be its creator or a participant of a course it hangs off. Only
        http/https URLs accepted — a `javascript:` link would reach a student's
        browser. Attaching twice is idempotent; detaching leaves the resource.
        13 integration tests.
- [x] `assessment` — quizzes and assignments
  - [x] `Quiz`/`Question`/`QuestionOption`/`QuizAttempt`/`QuizResponse`
  - [x] `LearningContext` port — assessment reaches courses and learning through
        one narrow interface, never their entities (§8)
  - [x] **The answer key has no path to a student**: the student-facing type has
        no `isCorrect` field at all, so it cannot leak by forgetting to strip it.
        The test asserts the string is absent from the body, not merely false.
  - [x] Authoring validation rejects un-passable quizzes at authoring time —
        choice questions need ≥2 options and exactly one correct answer
        (several only for MULTIPLE_CHOICE); text questions may not carry options
  - [x] Auto-grading for choice questions; MULTIPLE_CHOICE requires the exact
        correct set, no partial credit
  - [x] A quiz containing free-text questions is left SUBMITTED with **no score**
        for a human to mark — scoring it would report a mark that ignored the
        written answers
  - [x] Passing score completes the course item, through the same
        `ProgressService` use case a student's own progress call uses
  - [x] `max_attempts` enforced; restarting resumes an unexpired attempt in
        progress rather than consuming another; expired attempts are closed
  - [x] Attempts are private to their student; answers for questions outside the
        quiz are rejected; resubmission replaces answers rather than duplicating
  - [x] 18 integration tests
  - [x] **Manual grading** — `GET /attempts/{id}/grading` lists only the written
        answers; `POST /attempts/{id}/grade` marks them and finishes the attempt.
        Refuses to finish while any answer is unmarked (a partial grade would
        understate the result) and refuses to override auto-graded questions
        (that would silently diverge from the answer key). Score spans the whole
        paper, not just the written half.
  - [x] **Assignments** — brief, submission, instructor grading with feedback and
        `graded_by` attribution
  - [x] Late work refused unless the assignment allows it
  - [x] A student may attach only their own completed uploads, so a guessed media
        id cannot be used to submit someone else's work
  - [x] Handing in marks the item IN_PROGRESS; grading completes it. There is no
        pass mark in the V1 assignment model, so any grade completes the item
  - [x] Students see only their own submissions; the class list is editors-only
  - [x] 12 more integration tests
  - [x] **`QuizAttemptExpirySweeper`** — expiry used to be noticed only when
        someone touched the attempt, so a student who closed the tab left a row
        IN_PROGRESS forever, which also blocked them starting again: an open
        attempt is resumed, not replaced. Only quizzes with a time limit are
        eligible; untimed attempts stay open
- [x] `community` — discussions, comments, reviews
  - [x] `DiscussionThread`/`DiscussionComment`/`CourseReview`
  - [x] `CommunityContext` port over courses and learning (§8)
  - [x] Access is by **participation**: enrolled students and course staff, never
        a platform role. `GET /courses/**` is public for discovery, so the rule
        is enforced in the service — an anonymous thread listing gets 401
  - [x] Nested replies; a parent comment must be in the same thread, or a reply
        could be grafted onto a thread its author cannot see
  - [x] Closed threads take no replies; only staff may hide, and hidden threads
        leave both the listing and the author's reach
  - [x] Reviews require having enrolled; instructors cannot review their own
        course; one per student per course; staff moderation
  - [x] Rating average computed in SQL rather than by loading every review
  - [x] 17 integration tests
- [~] `platform/media` — direct-to-storage uploads
  - [x] `ObjectStorage` port; business modules never see the S3 SDK (§17)
  - [x] `S3ObjectStorage` + presigner config — same code drives MinIO locally
        and S3 in production, only properties differ
  - [x] **Bytes never pass through the API**: presigned PUT/GET, which is why the
        app's own multipart cap stays small
  - [x] Row written PENDING *before* the URL is handed out, so an abandoned
        upload leaves a sweepable row rather than an untracked object
  - [x] Size read back from storage on complete, never trusted from the client
  - [x] Content-Type is signed into the upload URL — storage itself rejects a
        mismatch (verified: wrong type → 403)
  - [x] Object keys are generated, never client-supplied, so a caller cannot
        traverse into another prefix; the original filename is kept in the DB
  - [x] `V2__media_object_owner.sql` — V1 modelled media as pure file metadata
        with no owner, leaving no basis to authorize reads
  - [x] `POST /api/v1/media/uploads`, `/{id}/complete`, `GET /{id}/download-url`
  - [x] 7 integration tests against a real MinIO container
  - [x] Attachment to lessons done (see `learning`). `downloadUrlForAuthorizedCaller`
        lets a business module grant access it has already authorized; the
        uploader-only path remains for a user's own uploads
  - [x] Course thumbnails via the public bucket, with stable unsigned URLs
  - [x] `PendingUploadSweeper` — recovers uploads that finished but were never
        confirmed; only genuinely absent objects are marked FAILED
  - [x] Checksum (the storage ETag) read back alongside the size on complete
        and on sweep recovery — from storage, never from the client
  - [x] **Resumable multipart uploads** (`V10__multipart_uploads.sql`). Two
        problems, not one: a presigned PUT expires after 15 minutes while a 4GB
        lecture takes closer to an hour on an ordinary connection, so large
        uploads failed *deterministically* rather than unluckily — and when one
        was interrupted there was nowhere for the transferred bytes to live, so
        the client began again from zero. S3 refuses a single PUT above 5GB in
        any case
  - [x] Resuming is a **question, not a mechanism**: storage holds the parts
        between requests, so `GET .../parts` tells a client what landed and it
        sends only the difference. An upload interrupted at 90% costs the last
        10%
  - [x] `media_objects.upload_id` is the piece the server has to keep. A browser
        that closed cannot reconstruct it, and without it a half-finished upload
        is unresumable however many parts storage still holds. Cleared on
        completion, so nothing mistakes a finished object for one in flight
  - [x] **Part size scales with the file.** S3 allows at most 10,000 parts, so a
        fixed 5MB would cap an upload at 50GB, while a fixed 100MB would make a
        30MB file a pointless three-round-trip dance
  - [x] Bytes still never pass through the application — parts are presigned
        exactly as whole objects already are, which is what keeps the app's own
        multipart limit at 25MB while gigabyte files upload fine
  - [x] `PendingUploadSweeper` now **aborts** the multipart upload as well as
        marking the row FAILED. Parts neither completed nor aborted stay in the
        bucket, do not appear in an ordinary listing, and are billed — silent
        growth is exactly what that sweep exists to prevent
  - [x] The five endpoints map one-to-one onto what `@uppy/aws-s3` calls, so the
        browser handles chunking, parallelism, retries and progress while the
        server only signs and records. A server-side uploader (tus, Uppy
        Companion) would have put bytes back in the request path
  - [x] 7 integration tests against real MinIO — multipart semantics are exactly
        what a mock would get wrong
  - [x] **FFmpeg transcoding** — see §10
- [x] `platform/notifications` — notification service, channels, RabbitMQ worker
- [x] `platform/maintenance` — scheduled sweeps guarded by a PostgreSQL
      transaction-scoped advisory lock, so a second instance skips rather than
      duplicating work (no ShedLock dependency, no extra table)

`identity`, `courses` and `learning` are in place. The module boundary held:
`learning` reaches `courses` through an eight-method port and nothing else.

Lesson content is done, so a LESSON item now has something behind it.

`assessment` is complete: quizzes with auto- and manual grading, and assignments
with submission and grading. Every course item type now does something.

All five business modules plus certificates and notifications are implemented.

Co-instructor management and the profile endpoints close two gaps where the data
model and the authorization rules already supported something the API could not
reach: `course_instructors` rows nothing could create, and a `user_profiles` row
nothing could edit.

**Notifications** (`platform/notifications`): business modules publish domain
events (`CourseCompleted`, `AssignmentGraded`, `CertificateIssued`) and know
nothing about delivery (§23/§24). Listeners run AFTER_COMMIT — a notification
about rolled-back work would be a lie, and an SMTP round trip must not hold a
business transaction open. Delivery runs off a RabbitMQ queue with a dead-letter
queue; if the broker is unreachable it falls back to inline delivery rather than
losing the notification. A channel failure is recorded, never thrown: a dead mail
server must not lose the in-app notification. Verified against a real RabbitMQ
container, not a stubbed publisher.

**Certificate PDFs** are rendered with PDFBox after the completion transaction
commits, then stored via `ObjectStorage.put` (server-side, not presigned — there
is no client involved) as a media object with `createdBy = null`, so the
uploader-only access rule does not apply and `CertificateDocumentService`
authorizes readers itself: the holder or the course's staff. Rendering is
idempotent and never blocks completion — a storage outage must not roll back a
course completion that genuinely happened.

Two limitations recorded on purpose:
- The layout is deliberately plain. Branding is a product decision, and a
  placeholder that looks finished is worse than one that obviously is not.
- Standard-14 fonts cannot encode characters outside Latin-1 and PDFBox throws
  rather than substituting, so unsupported characters in a learner's name are
  stripped. Picking a real embedded font removes this.

**Course thumbnails and reordering** finish course authoring.

Thumbnails use the public bucket that `minio-init` creates with an anonymous
download policy. Uploads take `visibility: PUBLIC`, which routes the object there
and yields a **stable, unsigned URL** — a presigned URL expires, which is useless
on a cacheable listing page, so a private object is rejected as a thumbnail
rather than silently given a URL that dies in 15 minutes. The course listing
resolves every thumbnail in one batched lookup: per-row resolution would be an
N+1 on the busiest endpoint on the platform.

Reordering (`PUT .../sections/order`, `.../items/order`, `.../questions/order`)
relies on the `DEFERRABLE INITIALLY DEFERRED` position constraints from V1, so
positions are rewritten in one transaction without hitting an intermediate
collision. A reorder must be a **permutation** of the current set: a partial
order would leave the rest at stale positions and silently interleave them.

`ReorderRequest` lives in `shared/api` because course structure and quiz
questions both need it; in either module it would force the other to depend on
that module's API package for a DTO unrelated to its domain (§8).

**Maintenance sweeps** run on a schedule, each bounded by a batch size and
switchable off. `platform/maintenance` owns the shared machinery —
`SchedulerLock`, `MaintenanceProperties` — and `PendingUploadSweeper`. The two
sweeps over business data live in the modules that own it instead
(`EnrollmentExpirySweeper` in `learning`, `QuizAttemptExpirySweeper` in
`assessment`), so a sweep goes through its own module's rules rather than
reaching across a boundary (§8).

`PendingUploadSweeper` resolves uploads that were requested but never confirmed.
If the object *is* in storage the client uploaded and only failed to call
complete, so the sweep finishes the job and reads the real size back; only
genuinely absent objects are marked FAILED. Deleting a file a user successfully
uploaded because their browser closed would be the wrong default. Rows inside the
grace period are never touched.

`QuizAttemptExpirySweeper` closes timed attempts that were started and never
submitted. Checking expiry only when someone next touched the attempt left the
student stuck: `startAttempt` resumes an attempt in progress rather than opening
a second one, so an abandoned row left them unable to begin a fresh attempt at
all. Only timed quizzes are eligible — an untimed attempt has nothing to expire.

`EnrollmentExpirySweeper` moves ACTIVE enrolments past `expires_at` to EXPIRED.
`Enrollment.isClosed()` already blocks access, so this exists to keep the stored
status honest for listings and reporting. **Nothing sets `expires_at` yet** —
access duration is a product decision (per course? per enrolment? a platform
default?) and inventing one would bake a guess into the schema. The job is inert
by design until that policy exists.

Sweeps are disabled in the test profile and invoked directly: a background
schedule mutating rows underneath assertions is the same async-shared-state trap
that caused an earlier flake.

Multi-instance safety is handled: `SchedulerLock` takes a **transaction-scoped**
advisory lock (`pg_try_advisory_xact_lock`). Transaction-scoped matters — a
session-scoped lock would be wrong with a connection pool, because the unlock
can land on a different connection and leak the lock forever.

**Email delivery** goes through the `EmailSender` port, which is why adding a
provider was one class and a property (§17/§22). `SmtpEmailSender` stays the
default and `MailgunEmailSender` activates on `elearning.mail.provider=mailgun`,
both `@ConditionalOnProperty` so exactly one bean exists.

Mailgun **fails at startup** without its key or domain, naming the missing
environment variable. That is deliberate rather than a lazy check on first send:
the send path swallows failures on purpose, so that registration cannot be lost
to a bounce — which means a missing key would otherwise surface days later as
accounts nobody could verify, with only a log line to show for it.

`MailgunClientConfig` supplies the `RestClient.Builder`, which Spring Boot 4
does **not** provide here: Boot 4 splits auto-configuration across modules, and
this application depends on `spring-boot-starter-webmvc` — which serves HTTP but
configures no client for calling out. The symptom is a startup failure naming a
missing `RestClient$Builder` bean, and it reads like a mail-configuration problem
when it is a dependency one. Declaring the builder locally beat pulling in
another starter for a single outbound call.

Declaring it also made the **timeouts** ours to set, and they had to be: with no
auto-configured builder there are no defaults, and a client with no read timeout
holds its thread until the far end answers. Mailgun is called on a request thread
during registration and password reset, so an unresponsive provider would have
tied up the container's threads instead of failing and letting the caller move
on. Set to 5s connect, 10s read.

The wire format is pinned by 7 unit tests with `MockRestServiceServer`, not an
integration test: posting real mail through a paid account is not something a
suite should do. What they cover is the part that is easy to get wrong and
impossible to notice afterwards — Mailgun authenticates the literal username
`api` with the key as the Basic **password** (not a bearer token), the US and EU
stacks are separate hosts and a domain lives in exactly one, and every one of
those mistakes fails as an indistinguishable 401. So the provider's explanation
is carried into the exception rather than dropped for the status code.

**Deliberately not built** (decisions, not gaps):
- **Email templates.** There are four notification bodies, all one line of plain
  text. A template engine for that is premature infrastructure (§32). Trigger to
  revisit: the first HTML email, or localization.
- **Push channel.** No provider is wired; deliveries record SKIPPED rather than
  pretending to send. Choosing APNs/FCM is a product decision.
- **Roles for learners and instructors.** Authority over a course still comes
  from ownership and enrolment, never a role (§11). The roles in §8 govern
  *platform* administration, which has no relationship to derive authority from,
  and live in their own table so a learner cannot hold one.
- **Assignment pass mark.** Assignments have `max_score` but no threshold, so any
  grade completes the item.

**Verified by hand, not only by tests:**
- The app **starts under the `dev` profile** against the compose stack and serves
  requests (18s to ready). Registration was driven end to end through the real
  Mailgun API, which is how the missing-builder failure and the region mistake
  below were both caught — neither is reachable from the test suite.
- Mailgun credentials were checked against the live API: `GET /v3/domains/{d}`
  answers 200 on the US host and 404 on the EU one, which is how a domain's
  region is settled. A sandbox domain refuses any recipient not on its
  authorized list, with a 403 that says so.

**Verification gaps** (things believed correct but not observed working):
- **Neither Docker image has ever been built or run.** For the API image the
  layered layout, launcher class and classpath index were checked statically
  against the real jar, so only the build is unverified. `Dockerfile.worker` has
  had no such check: whether `apk add ffmpeg` resolves, whether the binary is on
  PATH for the JVM's ProcessBuilder, and whether `/tmp/transcode` is writable by
  the unprivileged user are all assumptions.
- `application-prod.yaml` has never been loaded — no prod-profile startup. This
  matters more now: Mailgun's fail-at-startup check only fires under a real boot,
  and prod is the profile that has no `.env` to fall back on.
- Certificate PDFs are asserted to be valid PDFs, but nobody has looked at one.
- No HTML email, and no delivery to a real inbox: the sandbox domain only reaches
  authorized recipients, so nobody has seen a verification mail as a user would.

**Remaining work**, honestly:
- Admin: nothing. All 16 permissions are enforced; the unbuilt parts of the
  `/settings` page are recorded in §8 as decisions, not gaps
- Ops: build and run **both** images, and boot once under the `prod` profile.
  This now spans ten migrations, two images, a JSON seeder and a worker whose
  entire point is a binary the API image does not have

Everything else on this list is either finished or a deliberate non-goal
recorded above. The four business modules and the three platform modules all
ship working endpoints with integration tests against real infrastructure.

## 7. Testing

- [x] JUnit 5, MockK, Testcontainers on the test classpath
- [x] `IntegrationTest` base class — real PostgreSQL 17 per JVM run
- [x] `ELearningApplicationTests` runs against Testcontainers, asserts Flyway
      applied the migration and the core tables exist (a context-load test alone
      would pass even with broken migrations). 3 tests, green.
- [x] Security/authorization tests — unauthenticated, tampered token, wrong
      password, account enumeration
- [x] API integration tests for identity (MockMvc + Testcontainers)
- [x] `IntegrationTest` base provides PostgreSQL, MinIO *and* RabbitMQ, shared by
      every test class so Spring caches one context rather than fragmenting it
- [x] Failure injection in the fake mail sender is scoped **per recipient**, not
      a global flag: delivery is asynchronous, so a message produced by one test
      can be consumed after that test ends and a global switch would poison an
      unrelated test. A `@BeforeEach` reset does not fix state that async work
      reads outside a test's lifetime.
- [x] Singleton containers: `@Testcontainers`/`@Container` tie a container's life
      to one test class, but Spring caches one context across all of them — the
      second class then reuses a context pointing at a stopped container. Started
      once per JVM, removed by Ryuk at exit.
- [x] **292 tests, all green** (verified run, not a stale report)
- [x] **Notification delivery race, found and fixed.** `NotificationEventListener`
      was annotated `@Transactional(REQUIRES_NEW)`, so the row was created *and*
      the RabbitMQ message published inside one transaction. The worker is fast,
      regularly won the race, found no row, and discarded the message — emails
      lost intermittently, with only a warning to show for it (7 in one run).
      The first fix (dropping the annotation) made it worse, deterministically:
      in an AFTER_COMMIT listener the completed transaction is still bound to the
      thread, so a plain `@Transactional` silently joins a transaction that never
      commits and the row vanishes (38 warnings). The correct boundary is
      `REQUIRES_NEW` on `NotificationService.create`/`dispatch` themselves: the
      write commits when `create` returns, and the publish happens after it from
      a non-transactional listener. Verified by the warning count going 7 → 38 → 0.
- [x] Quiz manual grading now notifies the student (`QUIZ_GRADED`) — marking
      happens long after they left, so otherwise they would have to poll.
- [x] Every module has integration tests against real infrastructure
- [x] `MaintenanceSweepTest` covers all three sweeps plus the advisory lock:
      a job already held elsewhere is skipped rather than failed, and two
      different jobs do not block each other

---

## 8. Roles and permissions (admin dashboard)

Platform administration, for the Lernova admin dashboard. The V1 data model
defines no admin concept at all — 40 tables, none for admins, roles, permissions
or audit — and "staff" throughout the code means *course* staff (owner or
co-instructor). So a platform administrator currently cannot moderate a review
or revoke a certificate on a course they do not own. This section closes that.

### The rule that keeps this compatible with §11

> **Relationships decide authority over a specific resource. Permissions decide
> authority over the platform.**

§11 warns against bare roles because "INSTRUCTOR" says nothing about *which*
course — there, a relationship exists to derive authority from. Platform
administration has no such relationship: there is no edge between an admin and
an arbitrary course, so the authority is intrinsically global, which is exactly
what a permission is for.

The load-bearing consequence: **an admin permission is a fallback that adds to
the relationship check, never one that replaces it.** Replace rather than add
and co-instructorship silently stops mattering.

### Design decisions

- [x] **A separate `admin_users` table.** Administering the platform and
      learning on it are different jobs, and keeping them in different tables
      means a self-registered learner has no path to a role at all — the
      guarantee is structural rather than a rule somebody has to remember. The
      cost, accepted deliberately, is a parallel authentication stack
- [x] **Admins do not self-register.** Accounts are created by a super admin, so
      there is no email-verification flow and no public signup: the two things
      verification exists to establish — that the address is real and that
      somebody consented — are already true when a colleague creates the account
- [x] **Super admin is a flag, not an enumerated permission set.** If it were "a
      role holding every permission row", a permission added in a later
      migration would silently not be granted, and super admins would quietly
      lose a capability. The flag means *all, including future ones*
- [x] **Permissions are seeded reference data**, roles are user-created. The set
      of codes is defined by what the application actually checks, so an
      endpoint to invent one would create a row nothing enforces — the same
      reasoning categories follow
- [x] **Managing roles is the super-admin flag, not a grantable permission.**
      Anyone who can edit roles can grant themselves everything, so it cannot be
      one of the things a role hands out
- [x] **The last super admin cannot be stripped or deleted**, or the platform
      locks itself out with no recovery short of SQL

### What the separate table costs, and how each cost is paid

- [x] **Two token audiences.** A `typ` claim separates them: `CurrentUser`
      resolves only `typ=user`, a new `CurrentAdmin` only `typ=admin`. Without
      it an admin token would be a platform identity whose id matches no `users`
      row — failing in confusing ways at best, and authenticating across the
      boundary at worst
- [x] **A second refresh-token table.** `refresh_tokens.user_id` has a foreign
      key to `users`; admins get `admin_refresh_tokens` with the same rotation
      and reuse detection rather than losing the FK to a polymorphic column
- [x] **No admin email flow at first.** A super admin sets the initial password
      when creating the account and hands it over; the admin changes it with
      current-password-plus-new. Email reset for admins is a later addition, not
      a prerequisite
- [x] **`assignment_submissions.graded_by` references `users`**, so an admin
      cannot be recorded as a grader. Admins therefore get `submission.read` but
      **not** grading: marking work stays with the course's instructors, which is
      defensible on its own terms. Making admins grade would need a second
      nullable column, and that is a schema decision to take deliberately if the
      requirement appears

### Work

- [x] `V6__admin_roles.sql` — `admin_users`, `admin_refresh_tokens`,
      `permissions`, `roles`, `role_permissions`, `admin_user_roles`; seeded
      permission catalog; seeded `SUPER_ADMIN` role; seeded super-admin account
- [x] Seed credentials come from **Flyway placeholders**, so development works
      untouched while production supplies its own by environment rather than by
      editing a migration
- [x] Domain, repositories, `PermissionService` (resolution + super-admin
      short-circuit), `RoleService` (CRUD + assignment), `AdminAccountService`
- [x] `POST /api/v1/admin/auth/login`, `/refresh`, `/logout`; permission codes
      carried as JWT authorities so `@PreAuthorize` works
- [x] Admin API: list permissions, role CRUD, assign/revoke roles, admin account
      CRUD — all super-admin-gated where they confer privilege
- [x] Enforcement wired into the existing checks as a fallback:
      `CourseAuthorization`, review and discussion moderation, certificate
      revocation
- [x] Integration tests

### Known trade-off

- [x] A revoked permission stays live until the access token expires (≤15 min),
      because a signed JWT cannot be withdrawn — the same property already
      accepted for logout. Stripping a role therefore also revokes that admin's
      refresh tokens, so the session dies at its next refresh rather than
      lasting 30 days

### Verified

- [x] 11 integration tests, plus a **real dev-profile boot**: V5 and V6 applied
      to the live database, the seeded super admin signed in and received all 16
      permission codes, its token was refused at `/api/v1/me` with 401, and the
      startup warning about the default password fired
- [x] `MeController` read `jwt.subject` directly, bypassing the type check, so
      an admin token reached it and answered `USER_NOT_FOUND` — a token meant
      for another audience reported as a missing account. It now goes through
      `CurrentUser` like everything else. Found by the test that asserts an
      admin token is not a learner identity

### Not built yet

- [x] **`PlatformAccess.require`** — the scheme could answer "do you hold
      this?" but not refuse. 403 rather than 401 throughout: a learner's token
      is authenticated, it simply is not for this audience
- [x] **Learner directory** — `GET /api/v1/admin/users`, `/{id}`, and
      `POST /{id}/status`, behind `user.read` and `user.suspend`. Served from
      `identity`, not from `admin`: the data belongs there, and moving it would
      make `admin` depend on every module that owns something the dashboard
      renders (§8). The `/api/v1/admin` prefix is a URL namespace, not a claim
      about which module answers
- [x] Reading the directory and changing what is in it are **separate
      permissions**, so a support role can look without being able to suspend
- [x] Suspending revokes the learner's refresh tokens, bounding their access to
      one token lifetime instead of the refresh token's month. Login afterwards
      is an indistinguishable `INVALID_CREDENTIALS` — unlike an unverified
      address, a suspension is not the caller's state to learn
- [x] `PENDING` is refused as an administrative status: it means "has not
      confirmed their address", a state only registration produces, and setting
      it by hand would fake that
- [x] **Instructor roster** — `GET /api/v1/admin/instructors`, derived from
      **course ownership** rather than a role, because instructor-ness on this
      platform is a relationship and there is no column saying otherwise (§11).
      Served from `courses`, which owns that relationship; identity is reached
      through the widened `UserDirectory` port for names only
- [x] Both listings resolve their per-row detail in **one batched lookup**, the
      same way course thumbnails and taxonomy already do
- [x] 9 integration tests
- [x] **Admin course view** — `GET /api/v1/admin/courses` lists the whole
      catalogue including drafts and archived courses, filterable by title,
      status or owner, plus `/stats` for the overview tiles. A separate endpoint
      from `/api/v1/courses` rather than a flag on it: that one is PUBLISHED-only
      by design, and that filter is the single thing keeping drafts off the
      public listing — making it conditional would put the decision one boolean
      away from being wrong on the busiest endpoint on the platform
- [x] **The four course permissions stay separable**, each tested. `course.read`
      turns a draft's 404 into a view without conferring any write;
      `course.publish` publishes a finished course without the right to rewrite
      it — someone working a review queue is not thereby an author;
      `course.delete` prunes structure without conferring publish. Collapsing
      them into `course.write` would have been less code and a worse answer
- [x] The relationship is still checked **first**: an owner governs their own
      course with no permission at all, and an admin holding none is still a
      stranger who gets 404 on a draft
- [x] `CurrentActor` extended to publish, unpublish, archive and the structure
      deletes. These sit on learner-facing controllers that resolve a learner
      id, so an admin token reached them as 401 until the actor could be either
- [x] 9 integration tests
- [x] **Certificate register** — `GET /api/v1/admin/certificates` and `/{id}`,
      behind `certificate.read`, filterable by course or revoked state
- [x] **The verification code is never returned.** It is the unguessable half of
      the public check at `/certificates/verify/{code}`, so listing codes for
      staff would turn a dashboard screenshot into a set of forgeable
      credentials. The test asserts the field is absent from the body, not
      merely null
- [x] Read-only: revocation already exists behind `certificate.revoke` on the
      learner-facing controller, and a second path to the same state change is a
      second chance for the two to drift
- [x] **Grading queue** — `GET /api/v1/admin/submissions` behind
      `submission.read`, filterable by status, with author, assignment and
      course resolved for the page
- [x] Also read-only, and for a structural reason:
      `assignment_submissions.graded_by` references `users`, and an
      administrator is not a row there — there is nowhere to record them as the
      grader. Marking stays with the course's instructors; the page exists to
      find work that is waiting, not to do it
- [x] The submitted **content is not returned**: the queue answers what is
      outstanding and whose, and a student's actual answers are for whoever
      marks them
- [x] **`UserLookupService`** — every admin listing renders a person, and each
      lived in a different module. Identity publishes one implementation rather
      than three private copies of the same two-table join; identity depends on
      nothing, so depending on it inverts no arrow (§8). `courses` keeps its
      consumer-declared port, whose adapter now delegates here
- [x] 8 integration tests
- [x] **Category management** — `POST/PATCH/DELETE /api/v1/admin/categories`
      behind `category.manage`. This closes a decision left open earlier: the
      taxonomy had deliberately *no* create endpoint because "a shared browse
      tree only means something if one authority decides what is in it, and V1
      has no admin role to grant that". That authority now exists, so the
      reasoning expired rather than being overruled
- [x] Renaming leaves the **slug** alone — it is what the seeder matches on and
      what a filter URL carries, so regenerating it would orphan the seed entry
      and break links already shared, the same rule course slugs follow
- [x] Deletion is refused while the category has children or any course is in
      it. Both foreign keys cascade, so a delete the database allows would take
      the subtree and silently strip categorisation from every course carrying it
- [x] **Media library** — `GET /api/v1/admin/media` behind `media.read`,
      searchable by original filename; **object keys are never returned**, since
      they are generated precisely so a caller cannot address storage directly
- [x] **`MediaReferenceProbe`** — the same inversion as `ItemActivityProbe`.
      Needed because the database will *not* stop the delete: almost every
      reference to `media_objects` is `ON DELETE SET NULL`, so removing a file a
      lesson plays succeeds and quietly leaves the lesson with no video. Four
      modules answer — course thumbnails, lesson content and certificates and
      resources, submission attachments, profile avatars
- [x] The row is deleted before the object, and a storage failure is logged
      rather than thrown: a stranded object costs storage, while the other order
      can leave a row pointing at a file that is gone
- [x] 10 integration tests
- [x] **Session manager** — `GET/DELETE /api/v1/admin/sessions`, plus the
      administrator listing and a sign-out-everywhere, behind `settings.manage`.
      **All 16 permissions are now enforced**
- [x] **A session is a token family, not a row.** Rotation mints a successor on
      every refresh, so counting tokens would report one login as dozens of
      sessions. Ending a session revokes the whole chain: killing only the
      newest token would leave its predecessors able to rotate a fresh chain
      back into existence
- [x] The liveness filter is a **HAVING, not a WHERE** — found by a failing
      test. Rotation revokes the old token each time, so filtering rows first
      left exactly one token per family and reported every session as never
      having been refreshed. The whole chain is counted; the clause only decides
      which chains are still going
- [x] Revocations are recorded in the audit trail
- [ ] **The rest of the dashboard's `/settings` page is deliberately not built**,
      and this is a decision rather than a gap. Its four sections are four
      different problems and only one was a setting:
      **Appearance** is a per-viewer browser preference with nothing for a
      server to store — it belongs in the dashboard's own `localStorage`, and
      server-side it would be wrong the moment two admins want different themes.
      **Localization** needs the application localized first: the verification
      mail and every notification body is a hardcoded English string, so a
      language list would gate locales that do not exist.
      **Token lifetimes** are `JwtProperties`, bound once at startup; making
      them database-backed means re-reading per request, and a UI control that
      can extend every session to a year or lock the platform out is a
      deployment concern wearing a settings hat — they stay environment
      variables.
      **Webhooks and API keys** are an outbound delivery subsystem (endpoint
      registry, secrets, HMAC signing, retry, dead-lettering), not a settings
      row. Building a `platform_settings` table now would have guarded an empty
      table and left all four sections still not working
- [x] **Audit log** — `V7__audit_log.sql` and `GET /api/v1/admin/audit` behind
      `audit.read`, filterable by action, by actor, or by target
- [x] **No foreign key on `actor_id`**, for two reasons and the second is the
      one that matters: an actor may be an administrator or a learner and those
      are separate tables, but more importantly **the record has to outlive its
      subject**. A key would either cascade the history away when an account is
      deleted or block the deletion, and neither is what a trail is for
- [x] `actor_label` **snapshots who the actor was** rather than joining. Live
      resolution would let a later rename rewrite the past, and would show
      deleted accounts as bare UUIDs exactly when the record matters most
- [x] Written **in the audited operation's own transaction**, deliberately: if
      the action rolled back it did not happen and should leave no record, and
      if the record cannot be written the action fails — an unrecorded privilege
      change is worse than a refused one. That is the opposite of the choice
      made for notifications, where a failed email must never lose the work it
      was announcing
- [x] **Append-only by construction.** No endpoint updates or deletes an entry;
      a log with a delete button is not evidence of anything
- [x] `request_id` carries the same value as the `X-Request-Id` header and the
      log MDC, so an entry traces back to its request and its application logs
- [x] Recorded today: role created/deleted/assigned/revoked, admin created and
      suspended, learner status changed, certificate revoked, media deleted,
      category deleted — the privilege changes and the destructive actions
- [x] 6 integration tests

---

## 9. Reference-data seeding

One file, one place. Categories used to live in `V3__seed_categories.sql` and
the permission catalogue in `V6__admin_roles.sql`, which meant adding a category
required writing a migration and deploying a schema change for what is really
content.

- [x] `seed/reference-data.json` — categories (with their tree), the permission
      catalogue, and system roles
- [x] `ReferenceDataSeeder` reads it on `ApplicationReadyEvent`, which is after
      Flyway: the tables have to exist before anything can be written into them
- [x] **Idempotent**, matched by natural key — category and role by slug,
      permission by code — so running on every startup updates rather than
      duplicates. Verified live: booting against a database the migrations had
      already seeded created nothing and left 26 categories with no duplicate
      slugs
- [x] **Never deletes.** Removing an entry from the JSON leaves the row alone. A
      category may already be attached to courses and a permission already held
      by a role, and both cascade — deleting on absence would silently strip
      live data because somebody tidied a file
- [x] A renamed entry updates in place rather than appearing beside the old one,
      because the slug is the identity and not the name
- [x] The super-admin role is seeded holding **no permission rows**: it means
      every permission including ones added later, and materialising today's
      catalogue onto it would freeze that promise
- [x] A missing seed file is logged at ERROR rather than passed over — an empty
      category table makes the taxonomy API unusable and an empty permission
      catalogue makes every role unbuildable
- [x] `elearning.seed.location` is a Spring resource path, so a deployment can
      point at a file on disk instead of the packaged one
- [x] 4 integration tests

**Not moved:** the super-admin *account* in V6 keeps its Flyway placeholders.
Its password hash is a credential, and a credential belongs in the environment
rather than in a file committed next to the code that reads it.

---

## 10. Video transcoding

`video_contents` has carried `hls_manifest_media_id`, `thumbnail_media_id` and
`duration_seconds` since V1 with nothing writing them. This fills them.

### The parts

- [x] `VideoPipeline` port — a port for the same reason `ObjectStorage` and
      `EmailSender` are: transcoding is the one job here genuinely cheaper to
      buy than to run, and behind this interface a managed encoder is a class
      and a property rather than a rewrite
- [x] `V10__multipart_uploads.sql` — `media_objects.upload_id`, the one thing
      the server must keep for an upload to be resumable
- [x] `V9__transcode_jobs.sql` — a job has attempts, an error and a life longer
      than the request that made it; `media_objects.status` describes an upload
- [x] `TranscodeMessaging` — exchange, queue and DLQ, the same shape as
      notifications. No requeue-on-reject matters more here: a video that kills
      FFmpeg would otherwise be redelivered forever and block every lesson
      behind it
- [x] `FfmpegVideoPipeline` — the only class that knows FFmpeg exists
- [x] `Dockerfile.worker` — JRE plus the ffmpeg binary. Separate because that is
      ~100MB the API never invokes, and the two scale on different signals:
      the API on request rate, this on queue depth. Its heap is capped lower,
      since FFmpeg is a child process whose memory is not the JVM's

### Decisions

- [x] **Queued on lesson attachment, not on upload.** Media has no idea whether
      a file is a lesson video, a submission attachment or a resource, so
      transcoding every uploaded MP4 would burn CPU on files nobody streams
- [x] **The row is written before the message**, and published after commit. The
      notification pipeline learned this the hard way — the worker is fast,
      won the race against its own transaction, found no row and discarded the
      message
- [x] **Failure degrades, it does not break.** If the encode never succeeds
      `hls_manifest_media_id` stays null and the player falls back to the
      original upload. Same rule certificate rendering follows
- [x] The ladder **is** the compression. Re-encoding to those bitrates is the
      compression; there is no separate step to add. Renditions above the
      source's own height are skipped rather than upscaled
- [x] **No client-side compression.** It saves upload bandwidth only — the
      server must transcode anyway for adaptive streaming — while costing a
      second lossy generation and a browser encoder that is often slower than
      the upload it replaces. Resumable multipart upload is the real answer to
      large sources, and is still open

### Playback

- [x] **Signed, over the private bucket.** The public bucket was the cheap
      option and is what thumbnails use; it is the wrong answer here, because
      enrolment gates every other thing a student can reach and making the most
      valuable asset readable by anyone holding a URL would turn that check into
      a formality
- [x] `GET /items/{id}/lesson/stream.m3u8` rewrites the manifest per request,
      presigning each segment for the caller. Bytes still never pass through the
      application — segments come from storage directly, only the playlist does
- [x] Segment URLs last 4 hours, not the usual 15 minutes: a player fetches
      segments across the whole runtime, and a two-hour lecture would stop dead
      partway through on URLs that had expired behind it
- [x] Re-pointing a lesson at a different file clears the old manifest, so
      renditions built from the previous video cannot serve under the new one

### Found by the tests

- [x] `TranscodeWorker.publish` was `@Transactional(REQUIRES_NEW)` and called
      from the same class, so **self-invocation bypassed the proxy**: it ran
      with no transaction, the lesson update was dirty-checked into nothing, and
      the job reported success while playback answered STREAM_NOT_READY. Split
      into `TranscodeResultWriter`, the third time this codebase has hit that
      proxy rule
- [x] The listener is separate from the worker so tests drive the logic
      directly; a live `@RabbitListener` would consume jobs underneath the
      assertions — the trap the maintenance sweeps already avoid
- [x] `DisabledVideoPipeline` keeps the API context loading without an encoder.
      It first used `@ConditionalOnMissingBean`, which is only dependable on
      auto-configuration classes and left 270 tests failing to start; it is now
      the inverse property condition, mutually exclusive with the real one
- [x] 8 integration tests, FFmpeg faked behind the port

---

## Open decisions

- ~~Base package `com.elearning.train`~~ — **resolved.** Renamed to
  `com.elearning`, `TrainApplication` → `ELearningApplication`, while it was
  still a two-file change.
- **Java toolchain is 17**, Spring Boot 4's baseline. JDK 21 is installed
  locally. Bumping to 21 would allow virtual threads
  (`spring.threads.virtual.enabled`), which is currently left out.
- ~~**Email provider**~~ — **resolved: Mailgun**, over its HTTP API rather than
  its SMTP endpoint. A lot of hosting blocks outbound 587, and a blocked port
  surfaces as a connection timeout on someone's first password reset rather
  than at deploy time; the HTTP path also returns a readable error body, where
  SMTP gives a numeric code. Mailpit is still the default locally — a fresh
  clone must run with no credentials at all.
