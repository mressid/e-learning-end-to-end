# AGENTS.md

## Purpose

This repository contains the backend of an E-learning platform.

The backend is a **modular monolith** built with:

* Kotlin
* Spring Boot
* Spring Security
* Spring Data JPA / Hibernate
* PostgreSQL
* Flyway
* REST
* OpenAPI

The goal is to build a maintainable, modular backend without prematurely introducing microservices or unnecessary infrastructure.

---

# 1. Core Architectural Principles

## 1.1 Modular Monolith

The application MUST be organized around **business domains/modules**, not around database tables or technical layers.

Do NOT create one package/module for every entity.

Bad:

```text
user/
course/
course_section/
course_item/
lesson/
quiz/
question/
question_option/
enrollment/
...
```

Also avoid organizing the entire application globally like:

```text
controller/
service/
repository/
entity/
dto/
```

This causes business logic to become scattered across the entire application.

Prefer:

```text
identity/
courses/
learning/
assessment/
community/
platform/
shared/
```

Each module owns a coherent business capability.

---

# 2. Recommended Module Structure

The application should initially be organized approximately as:

```text
src/main/kotlin/com/<company>/elearning/

├── identity/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── courses/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── learning/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── assessment/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── community/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── platform/
│   ├── media/
│   ├── notifications/
│   └── ...
│
└── shared/
    ├── security/
    ├── database/
    ├── errors/
    ├── events/
    └── ...
```

This is a guideline, not a requirement to create every directory immediately.

**Do not create empty folders just to satisfy this structure.**

Only introduce subpackages when the module becomes large enough to justify them.

---

# 3. Module Responsibilities

## identity

Responsible for application-level identity concepts.

Examples:

```text
users
profiles
user preferences
application roles
```

Spring Security owns the security mechanisms.

Do not implement custom cryptographic or authentication mechanisms unless there is a concrete requirement.

---

## courses

Responsible for course authoring and course structure.

Examples:

```text
courses
sections
course items
instructors
categories
tags
prerequisites
```

The course module owns the course hierarchy.

Conceptually:

```text
Course
  └── Section
        └── CourseItem
```

---

## learning

Responsible for students consuming courses.

Examples:

```text
enrollments
learning progress
lessons
resources
certificates
```

The learning module should not contain quiz-specific business logic.

---

## assessment

Responsible for evaluation.

Examples:

```text
quizzes
questions
question options
quiz attempts
quiz responses

assignments
submissions
grading
```

Assessment logic should remain isolated from course authoring logic.

---

## community

Responsible for social/interpersonal features.

Examples:

```text
discussion threads
comments
reviews
```

---

## platform

Responsible for infrastructure-facing application capabilities that are shared by multiple business modules.

Examples:

```text
media
notifications
file storage
email
```

Platform code should provide abstractions to business modules instead of forcing them to know infrastructure implementation details.

---

# 4. Internal Module Organization

When a module is large enough, use:

```text
module/
├── api/
├── application/
├── domain/
└── infrastructure/
```

### api

Contains external API concerns:

```text
REST controllers
request DTOs
response DTOs
API mappers
```

The API layer should not contain business logic.

---

### application

Contains use cases/application services.

Examples:

```text
CreateCourse
PublishCourse
EnrollStudent
CompleteLesson
SubmitAssignment
StartQuizAttempt
```

Application services coordinate domain operations and infrastructure.

---

### domain

Contains business concepts and rules.

Examples:

```text
Course
CourseItem
Enrollment
LearningProgress
Quiz
Assignment
```

Domain code should contain business rules rather than HTTP or database concerns whenever practical.

---

### infrastructure

Contains technical implementations.

Examples:

```text
JPA repositories
database mappings
external service clients
storage implementations
messaging implementations
```

Infrastructure should implement interfaces defined by the appropriate application/domain layer where useful.

---

# 5. Do Not Over-Engineer the Module Structure

The structure should evolve with complexity.

For a small module, this is acceptable:

```text
courses/
├── Course.kt
├── CourseController.kt
├── CourseService.kt
└── CourseRepository.kt
```

As complexity grows:

```text
courses/
├── api/
├── application/
├── domain/
└── infrastructure/
```

Do not create five layers and twenty files for a CRUD operation that only needs three.

**Architecture should follow complexity.**

---

# 6. Database Is Not the Architecture

The database schema and application modules are related but are NOT the same thing.

A database may contain:

```text
courses
course_sections
course_items
course_instructors
course_categories
course_tags
...
```

This does NOT mean the application needs the same number of packages.

For example:

```text
courses/
```

can own:

```text
courses
course_sections
course_items
course_instructors
course_categories
course_tags
course_prerequisites
```

Group concepts according to business responsibility.

---

# 7. Avoid Generic "Utils"

Do not create a giant:

```text
utils/
helpers/
common/
misc/
```

package containing unrelated functionality.

Bad:

```text
shared/utils/
    StringUtils
    CourseUtils
    SecurityUtils
    FileUtils
    DateUtils
    ValidationUtils
```

Prefer domain-specific or technically specific abstractions.

Only put something in `shared` when it is genuinely shared by multiple modules and has no clear ownership elsewhere.

---

# 8. Dependency Direction

Modules should have clear dependencies.

Prefer:

```text
API
 ↓
Application
 ↓
Domain
```

Infrastructure implements technical concerns used by application/domain code.

Avoid:

```text
courses → assessment → learning → courses
```

Circular module dependencies should be treated as an architectural problem.

If two modules need to communicate, prefer:

1. application-level interfaces
2. domain events
3. shared contracts
4. explicit module dependency when genuinely appropriate

Do not make everything accessible to everything.

---

# 9. Use Libraries — Do Not Reinvent the Wheel

This is a fundamental project rule.

Before implementing a generic technical capability, check whether a mature library or Spring component already solves it.

The default attitude should be:

> **Use existing, maintained, well-tested libraries for generic problems. Build custom code for product-specific problems.**

---

# 10. Technology Responsibilities

## Spring Security

Use Spring Security for:

```text
authentication
authorization
password hashing
security filters
JWT
OAuth2/OIDC
method-level authorization
CSRF
CORS security
security context
```

Do NOT create:

```text
CustomPasswordHasher
CustomJwtValidator
CustomAuthenticationManager
CustomSecurityFilter
```

unless Spring Security genuinely cannot satisfy the requirement.

---

## Spring Data JPA / Hibernate

Use JPA/Hibernate for relational persistence.

Do not create a custom ORM or database abstraction.

Use:

```text
@Entity
@Repository
JpaRepository
@EntityManager
Specifications
```

where appropriate.

Do not force every query through generic repository abstractions.

A custom repository/query is preferable when the query is complex.

---

## Flyway

Database schema changes MUST be handled through Flyway migrations.

Example:

```text
db/migration/

V1__initial_schema.sql
V2__add_course_status.sql
V3__add_learning_progress.sql
```

Never manually modify production schema outside the migration process.

---

## PostgreSQL

PostgreSQL is the primary source of truth.

Use PostgreSQL capabilities where appropriate:

```text
JSONB
indexes
constraints
foreign keys
full-text search
transactions
```

Do not introduce another database simply because it is technically capable of solving a problem.

---

# 11. Authentication Architecture

For V1, authentication is based on Spring Security.

The application should separate:

```text
Authentication
    ↓
Who is the user?

Authorization
    ↓
What can the user do?

Business authorization
    ↓
Is this user allowed to perform THIS operation on THIS resource?
```

Example:

```text
JWT
 ↓
Spring Security
 ↓
Authenticated User
 ↓
CourseApplicationService
 ↓
Check course ownership/instructor role
 ↓
Allow / Reject
```

Do not rely solely on a role such as `INSTRUCTOR`.

For example:

```text
ROLE_INSTRUCTOR
```

does not automatically mean:

```text
can modify course 123
```

Resource-level authorization belongs to the application domain.

---

# 12. REST API

Use REST for the primary API.

Recommended base path:

```text
/api/v1
```

Examples:

```text
GET    /api/v1/courses
GET    /api/v1/courses/{id}

POST   /api/v1/courses

GET    /api/v1/courses/{id}/sections

POST   /api/v1/courses/{id}/enroll

GET    /api/v1/me/enrollments

GET    /api/v1/lessons/{id}

POST   /api/v1/lessons/{id}/progress
```

Keep API DTOs separate from persistence entities.

Do not expose JPA entities directly as API responses.

---

# 13. DTOs

Use DTOs at the API boundary.

Prefer:

```text
HTTP Request
    ↓
Request DTO
    ↓
Application Service
    ↓
Domain
    ↓
Response DTO
    ↓
HTTP Response
```

Avoid:

```text
Controller
    ↓
JPA Entity
    ↓
JSON
```

This prevents the database model from becoming the public API contract.

---

# 14. Transactions

Transactions should be defined around application use cases.

For example:

```kotlin
@Transactional
fun enrollStudent(...)
```

rather than arbitrarily placing transactions on every repository method.

The transaction boundary should correspond to a coherent business operation.

---

# 15. Error Handling

Use a centralized error handling mechanism.

Prefer:

```text
@RestControllerAdvice
```

with consistent API errors.

For example:

```json
{
  "code": "COURSE_NOT_FOUND",
  "message": "Course not found",
  "requestId": "..."
}
```

Do not return random error formats from individual controllers.

---

# 16. Validation

Use existing validation libraries.

Prefer:

```text
Jakarta Bean Validation
@Valid
@NotBlank
@NotNull
@Size
@Email
```

Do not write custom validation infrastructure for ordinary validation rules.

Business validation belongs in application/domain logic.

---

# 17. File and Object Storage

Do not store large binary files directly in PostgreSQL.

Use object storage:

```text
S3
MinIO
```

The database should store metadata and references.

Example:

```text
media_objects
    id
    bucket
    object_key
    mime_type
    size
    checksum
```

Application code should depend on a storage abstraction where useful:

```text
ObjectStorage
```

rather than directly coupling every module to MinIO/S3 SDK calls.

---

# 18. Video Processing

Do not implement video transcoding.

Use:

```text
FFmpeg
```

for:

```text
transcoding
thumbnails
metadata extraction
format conversion
```

Long-running video processing should not block an HTTP request.

---

# 19. Background Processing

When asynchronous processing is required, use a proper messaging/job solution such as:

```text
RabbitMQ
```

Do not implement an in-memory custom job queue.

Examples:

```text
VideoUploaded
    ↓
RabbitMQ
    ↓
VideoProcessingWorker
```

```text
AssignmentGraded
    ↓
RabbitMQ
    ↓
NotificationWorker
```

Do not introduce messaging for simple synchronous operations where it provides no benefit.

---

# 20. Caching

Use Redis when caching is actually required.

Possible use cases:

```text
frequently accessed course data
rate limiting
temporary state
distributed locks
short-lived data
```

Do not use Redis as the primary source of truth.

Do not cache everything by default.

First establish that caching solves an actual performance problem.

---

# 21. Search

Start with PostgreSQL search capabilities.

Do NOT introduce Elasticsearch/OpenSearch in V1 without a demonstrated requirement.

If search complexity eventually exceeds PostgreSQL's capabilities, introduce a dedicated search engine behind an application abstraction.

---

# 22. Email

Do not build an SMTP server.

Use an existing provider or SMTP service.

The application should expose a small abstraction such as:

```text
EmailSender
```

and the infrastructure implementation handles the provider.

---

# 23. Notifications

Notifications should be modeled as an application capability.

Possible channels:

```text
IN_APP
EMAIL
PUSH
```

Do not tightly couple business modules to Firebase, APNs, or an email provider.

Prefer:

```text
Business Event
      ↓
Notification Service
      ↓
Channel Provider
```

---

# 24. Events

Use domain/application events when they provide meaningful decoupling.

Example:

```text
CourseCompleted
AssignmentGraded
VideoUploaded
EnrollmentCreated
```

Do not create an event for every method call.

Events should represent meaningful business occurrences.

---

# 25. Logging

Use the existing Spring/JVM logging ecosystem:

```text
SLF4J
Logback
```

Do not create a custom logging abstraction unless there is a real need.

Logs should contain useful contextual information.

Never log:

```text
passwords
access tokens
refresh tokens
sensitive credentials
```

---

# 26. Testing

Use:

```text
JUnit
MockK
Spring Boot Test
Testcontainers
```

Test the application at multiple levels.

Prioritize:

```text
domain/business logic
application use cases
security/authorization
repository integration
API integration
```

Do not write tests that merely verify framework behavior.

For PostgreSQL integration tests, prefer Testcontainers over mocking the database.

---

# 27. API Documentation

Use OpenAPI/Springdoc.

The API contract should be understandable without reading implementation code.

Document:

```text
endpoints
request schemas
response schemas
authentication
errors
pagination
```

---

# 28. Pagination

Collection endpoints should use pagination when the result can grow significantly.

Example:

```text
GET /api/v1/courses?page=0&size=20
```

Do not load arbitrarily large datasets into memory.

---

# 29. Security Rules

Never:

```text
hardcode passwords
hardcode secrets
commit API keys
commit JWT secrets
commit database credentials
```

Use environment variables or a proper secret-management solution.

Never expose internal stack traces through the public API.

Validate authorization server-side even if the frontend already hides the operation.

The frontend is NOT a security boundary.

---

# 30. Configuration

Keep configuration externalized.

Use:

```text
application.yml
application-{profile}.yml
environment variables
```

Avoid scattering configuration values throughout the code.

Group configuration logically.

---

# 31. Dependencies

Before adding a dependency:

1. Check whether Spring Boot already provides the capability.
2. Check whether an existing dependency already provides it.
3. Check whether a mature open-source library exists.
4. Prefer actively maintained libraries.
5. Prefer small dependencies over large frameworks when the problem is small.
6. Avoid dependencies that duplicate existing infrastructure.

Do not add libraries simply because they are popular.

---

# 32. Avoid Premature Infrastructure

The initial system should remain simple.

Do NOT introduce these without a demonstrated requirement:

```text
Kafka
Elasticsearch
Cassandra
ClickHouse
Kubernetes
service mesh
microservices
distributed tracing infrastructure
multiple databases
```

The architecture should be capable of evolving toward these technologies later.

---

# 33. No Premature Microservices

The backend is intentionally a modular monolith.

Do not create:

```text
course-service
user-service
quiz-service
notification-service
```

as separate deployable applications.

Instead:

```text
Spring Boot
├── identity
├── courses
├── learning
├── assessment
├── community
└── platform
```

If a module eventually needs to become a service, its boundaries should make that extraction possible.

---

# 34. Module Boundaries

Each module should have a clear responsibility.

Before adding code, ask:

```text
Which business capability owns this?
```

If the answer is unclear, do not immediately place the code in `shared`.

`shared` is not a dumping ground.

---

# 35. Code Quality

Prefer:

```text
simple code
explicit dependencies
small classes
clear names
focused services
cohesive modules
immutable data where practical
constructor injection
```

Avoid:

```text
god classes
god services
deep inheritance hierarchies
static global state
reflection-heavy custom frameworks
unnecessary abstractions
premature generic frameworks
```

---

# 36. Kotlin Guidelines

Prefer idiomatic Kotlin.

Use:

```kotlin
data class
sealed class
enum class
value classes where appropriate
nullable types
extension functions when useful
constructor injection
```

Avoid Java-style Kotlin where it reduces clarity.

Do not overuse extension functions to hide important business behavior.

---

# 37. Spring Guidelines

Prefer constructor injection.

Example:

```kotlin
@Service
class CourseService(
    private val courseRepository: CourseRepository
)
```

Avoid field injection.

Prefer Spring's established mechanisms over custom infrastructure.

---

# 38. General Rule: Build Product, Reuse Infrastructure

The most important rule in this repository is:

```text
             GENERIC PROBLEM
                    │
                    ▼
       Does a mature solution exist?
              /             \
            YES              NO
             │                │
             ▼                ▼
       USE / ADAPT       BUILD IT
       THE LIBRARY       OURSELVES
```

Examples:

```text
Authentication       → Spring Security
Persistence           → Hibernate/JPA
Validation            → Jakarta Validation
Database migrations   → Flyway
Object storage        → S3/MinIO
Video processing      → FFmpeg
Caching               → Redis
Messaging             → RabbitMQ
API documentation     → OpenAPI
Testing               → JUnit/Testcontainers
Logging               → SLF4J/Logback
Metrics               → Micrometer
```

The custom code should primarily implement:

```text
E-learning business rules
```

not generic infrastructure.

---

# 39. Before Implementing a Feature

For every feature:

1. Identify the owning module.
2. Identify the business use case.
3. Identify required domain concepts.
4. Check whether existing Spring/library functionality solves part of the problem.
5. Define the application boundary.
6. Define persistence only if needed.
7. Define API contracts.
8. Implement business logic.
9. Add appropriate tests.
10. Avoid adding infrastructure unless required.

---

# 40. Final Architectural Goal

The target architecture is:

```text
                         CLIENTS
                    ┌──────┼──────┐
                    │      │      │
                   Web   Mobile  Admin
                    │      │      │
                    └──────┼──────┘
                           │
                           ▼
                    REST API / v1
                           │
                           ▼
                 ┌──────────────────┐
                 │   Spring Boot    │
                 │                  │
                 │ Modular Monolith │
                 │                  │
                 │ ┌──────────────┐ │
                 │ │ Identity     │ │
                 │ │ Courses      │ │
                 │ │ Learning     │ │
                 │ │ Assessment   │ │
                 │ │ Community    │ │
                 │ │ Platform     │ │
                 │ └──────────────┘ │
                 └────────┬─────────┘
                          │
             ┌────────────┼────────────┐
             ▼            ▼            ▼
        PostgreSQL      Redis       S3/MinIO
             │
             │
             ▼
         RabbitMQ
             │
             ▼
          Workers
```

The backend should remain a **single deployable application with strong internal module boundaries** until there is a concrete reason to split it.

The database schema, package structure, infrastructure, and API should all support this principle:

> **Keep the core focused on E-learning. Delegate generic technical problems to mature libraries and infrastructure.**
