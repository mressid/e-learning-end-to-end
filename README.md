# Lernova — E-Learning Platform

A course platform: a Kotlin/Spring backend, an administrator dashboard, and a
workspace for the people who teach.

## The three pieces

| | what it is | runs on |
| --- | --- | --- |
| [`backend/`](backend) | Kotlin + Spring Boot modular monolith, PostgreSQL, REST | `:8081` |
| [`admin-dashboard-elearning/`](admin-dashboard-elearning) | staff dashboard — directories, roles, catalogue, analytics | `:8082` |
| [`instructor-workspace-elearning/`](instructor-workspace-elearning) | authoring — your courses, curriculum, lessons, materials | `:8083` |

## Two account systems, deliberately

This is the thing to know before anything else, because it is the thing that
looks like a bug when you first meet it.

| | table | signs in at | token |
| --- | --- | --- | --- |
| dashboard staff | `admin_users` | `/api/v1/admin/auth/login` | `typ=admin` |
| learners & instructors | `users` | `/api/v1/auth/login` | `typ=user` |

The backend refuses each token on the other's routes. So signing in to the
dashboard with an instructor account fails with "invalid credentials" — not
because the password is wrong, but because the address is not in `admin_users`
at all, and the response deliberately does not say which of the two it is.

Within the platform side, a **student** and an **instructor** are different
kinds of account, decided when the account is created and permanent afterwards.
Not a role you can grant: `courses.owner_id` and `course_instructors.instructor_id`
reference the instructor table, so a student cannot be given a course by any
code path, including one that forgets to check. Somebody who both learns and
teaches holds two accounts.

## Backend

A modular monolith organised by business domain rather than by technical layer.
The modules are `identity`, `courses`, `learning`, `assessment`, `community`,
`platform`, `admin` and `shared`; dependencies run one way, and where two
modules must talk it is through an interface the consumer declares.

- **Java 17**, Kotlin, Spring Boot, Spring Security (JWT resource server)
- **PostgreSQL** with Flyway — 14 migrations, and the schema is built by them in
  tests too, so every run exercises them
- **Redis**, **RabbitMQ** for asynchronous work, **S3-compatible** object storage
  for media (MinIO locally)
- **331 tests**, integration tests included, against real containers rather than
  stubs

Conventions worth reading before contributing: [`backend/AGENT.md`](backend/AGENT.md).
The data model: [`backend/desgin/`](backend/desgin).

## Frontends

Both are **TanStack Start** + React 19 + Tailwind v4 + shadcn/ui, and both
generate their entire API layer from the backend's live OpenAPI document. They
share a design system by copy, not by package — see the note in the instructor
workspace's [README](instructor-workspace-elearning/README.md) about what that
costs.

A stale generated client does not fail loudly; it compiles against shapes the
backend no longer serves and breaks at runtime. After changing a controller or a
request/response record:

```bash
npm run api:sync      # in either frontend, with the backend running
```

That script shells out to `bun`. Without it, run the two halves directly:
`npm run api:fetch && npm run api:types`.

## Running it

Infrastructure first, then the backend, then whichever frontend you need.

```bash
cd backend
docker compose up -d postgres redis minio minio-init rabbitmq mailpit
./gradlew bootRun                     # :8081

cd ../admin-dashboard-elearning
npm install && npm run dev            # :8082

cd ../instructor-workspace-elearning
npm install && npm run dev            # :8083
```

Flyway builds the schema on first start, seeding reference data and a super
admin whose password the application will warn you to change.

```bash
cd backend && ./gradlew test          # needs Docker; uses Testcontainers
```

## What is not built yet

Honest list, so nobody goes looking:

- **Instructor workspace** — quiz and assignment authoring, the grading queue,
  enrolled students and their progress, course discussions and reviews. All have
  working endpoints; none has a screen.
- **Learner-facing app** — none. Learners exist, enrol, progress and are
  certificated through the API only.
- **Dashboard** — schedule, messages and support are routes that say what they
  are, because nothing in the API models a calendar, an administrator inbox or a
  ticketing system.

## Licence

MIT. See [LICENSE.txt](LICENSE.txt).
