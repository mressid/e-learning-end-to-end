# E-Learning Platform — V1 Backend Data Model

## 1. Purpose

This document defines the first version of the relational data model for an E-Learning platform.

The model is designed around a few core principles:

- Courses contain ordered learning content.
- Students enroll in courses and generate learning activity.
- Different learning activities have specialized data models.
- Learning resources can exist independently from lessons and can be attached at different scopes.
- Large binary files are stored in object storage rather than directly in PostgreSQL.
- The schema should remain extensible without prematurely modeling every possible LMS feature.
- Organizations and multi-tenancy are intentionally excluded from V1.

The primary database is assumed to be **PostgreSQL**.

Object storage can be implemented using **MinIO, Amazon S3, Azure Blob Storage, or another S3-compatible provider**.

---

# 2. High-Level Domain Model

The platform can be divided into several domains:

```text
┌──────────────────────────────────────────────────────────────┐
│                         E-LEARNING                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Identity                                                    │
│    Users                                                     │
│    Profiles                                                  │
│                                                              │
│  Course Management                                           │
│    Courses                                                   │
│    Instructors                                               │
│    Sections                                                  │
│    Course Items                                              │
│                                                              │
│  Learning Content                                            │
│    Lessons                                                   │
│    Videos                                                    │
│    Articles                                                  │
│    Documents                                                 │
│    Resources                                                 │
│                                                              │
│  Assessment                                                  │
│    Quizzes                                                   │
│    Questions                                                 │
│    Attempts                                                  │
│    Assignments                                               │
│    Submissions                                               │
│                                                              │
│  Student Activity                                            │
│    Enrollments                                               │
│    Progress                                                  │
│    Certificates                                              │
│                                                              │
│  Community                                                   │
│    Discussions                                               │
│    Reviews                                                   │
│                                                              │
│  Classification                                              │
│    Categories                                                │
│    Tags                                                      │
│    Prerequisites                                             │
│                                                              │
│  Platform                                                    │
│    Media                                                     │
│    Notifications                                             │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

# 3. Core Course Hierarchy

The most important structural decision is that a course does not directly contain lessons, quizzes, or assignments.

Instead, the hierarchy is:

```text
Course
  │
  └── Section
        │
        └── CourseItem
              │
              ├── Lesson
              ├── Quiz
              └── Assignment
```

For example:

```text
Course: Python Fundamentals

├── Section: Python Basics
│
│   ├── CourseItem: Introduction
│   │      └── Lesson
│   │
│   ├── CourseItem: Variables
│   │      └── Lesson
│   │
│   └── CourseItem: Python Basics Quiz
│          └── Quiz
│
├── Section: Functions
│
│   ├── CourseItem: Functions
│   │      └── Lesson
│   │
│   └── CourseItem: Functions Assignment
│          └── Assignment
│
└── Section: Advanced Topics
```

This abstraction is important because it gives the course a single ordered learning sequence.

---

# 4. Users

## 4.1 `users`

The `users` table represents platform accounts.

```text
users
-----
id
email
username
password_hash
status
created_at
updated_at
last_login_at
```

### Responsibilities

The table contains authentication and account-level information.

It should not contain profile information such as biography or avatar.

### Important constraints

```text
email UNIQUE
username UNIQUE
```

### Possible statuses

```text
ACTIVE
SUSPENDED
DISABLED
PENDING
```

---

## 4.2 `user_profiles`

Profile information is separated from authentication.

```text
user_profiles
-------------
user_id
first_name
last_name
bio
avatar_media_id
timezone
language
```

Relationship:

```text
users 1 ─── 1 user_profiles
```

The profile can be extended later without making the authentication table unnecessarily large.

---

# 5. Courses

## 5.1 `courses`

Represents a complete course.

```text
courses
-------
id
owner_id
title
slug
description
short_description
status
level
language
thumbnail_media_id
created_at
updated_at
published_at
```

### Owner

`owner_id` references `users`.

The owner is the user responsible for the course.

```text
users 1 ─── N courses
```

### Status

Typical values:

```text
DRAFT
PUBLISHED
ARCHIVED
```

### Level

Typical values:

```text
BEGINNER
INTERMEDIATE
ADVANCED
ALL_LEVELS
```

The exact enum implementation can be decided at the application/database layer.

---

# 6. Course Instructors

## 6.1 `course_instructors`

A course can have multiple instructors.

```text
course_instructors
------------------
course_id
instructor_id
role
```

Composite primary key:

```text
(course_id, instructor_id)
```

Relationship:

```text
users M ─── N courses
```

through:

```text
course_instructors
```

Example:

```text
Course: Advanced Python

John
  role = PRIMARY

Sarah
  role = ASSISTANT
```

This avoids restricting a course to a single instructor.

---

# 7. Course Sections

## 7.1 `course_sections`

Sections organize the content of a course.

```text
course_sections
---------------
id
course_id
title
description
position
```

Relationship:

```text
course 1 ─── N course_sections
```

`position` determines the order of sections.

Example:

```text
1 → Python Basics
2 → Functions
3 → Object-Oriented Programming
4 → Advanced Python
```

---

# 8. Course Items

## 8.1 `course_items`

`course_items` is the central abstraction for learning activities.

```text
course_items
------------
id
section_id
title
type
position
is_required
available_from
created_at
updated_at
```

Possible initial types:

```text
LESSON
QUIZ
ASSIGNMENT
```

Potential future types:

```text
EXAM
PROJECT
LIVE_SESSION
SURVEY
SCORM
```

### Why use CourseItem?

Without `course_items`, the system might have:

```text
section
 ├── lessons
 ├── quizzes
 └── assignments
```

This makes ordering across different content types complicated.

With `course_items`:

```text
section
 ├── lesson
 ├── lesson
 ├── quiz
 ├── lesson
 └── assignment
```

Everything participates in the same ordered sequence.

### Example

```text
Course Section

position 1 → LESSON
position 2 → LESSON
position 3 → QUIZ
position 4 → LESSON
position 5 → ASSIGNMENT
```

---

# 9. Lessons

## 9.1 `lessons`

A course item whose type is `LESSON` gets a corresponding record in `lessons`.

```text
lessons
-------
course_item_id
content_type
description
duration_seconds
completion_rule
```

The primary key is:

```text
course_item_id
```

which is also a foreign key to:

```text
course_items.id
```

Therefore:

```text
course_item 1 ─── 0..1 lesson
```

### Content types

Possible values:

```text
VIDEO
ARTICLE
DOCUMENT
AUDIO
EXTERNAL
```

---

# 10. Lesson Content

Lesson metadata and lesson content are separated.

This allows different content types to have different structures.

---

## 10.1 `video_contents`

```text
video_contents
--------------
lesson_id
media_id
thumbnail_media_id
hls_manifest_media_id
duration_seconds
```

A video can reference:

- Original video
- Thumbnail
- HLS manifest
- Other media representations

---

## 10.2 `article_contents`

```text
article_contents
----------------
lesson_id
content
```

The content may be stored as:

```text
Markdown
HTML
Rich Text
```

depending on the editor and rendering strategy.

---

## 10.3 `document_contents`

```text
document_contents
-----------------
lesson_id
media_id
```

The document itself is stored in object storage.

The database only stores the relationship to the media object.

---

# 11. Enrollments

## 11.1 `enrollments`

An enrollment represents a student's participation in a course.

```text
enrollments
-----------
id
student_id
course_id
status
enrolled_at
started_at
completed_at
expires_at
```

Relationship:

```text
users M ─── N courses
```

through:

```text
enrollments
```

### Possible statuses

```text
ACTIVE
COMPLETED
CANCELLED
EXPIRED
SUSPENDED
```

If V1 allows only one enrollment per student/course, use:

```text
UNIQUE(student_id, course_id)
```

---

# 12. Learning Progress

## 12.1 `learning_progress`

Tracks a student's progress through individual course items.

```text
learning_progress
-----------------
id
student_id
course_id
course_item_id
status
progress_percent
last_position_seconds
started_at
completed_at
updated_at
```

Example:

```text
Student: John

Python Course

Introduction       → COMPLETED
Variables          → COMPLETED
Functions          → IN_PROGRESS 65%
Quiz               → NOT_STARTED
Assignment         → NOT_STARTED
```

### Recommended constraint

```text
UNIQUE(student_id, course_item_id)
```

This ensures one progress record exists for a student/item pair.

---

# 13. Quizzes

## 13.1 `quizzes`

A quiz is a specialized `course_item`.

```text
quizzes
-------
course_item_id
title
instructions
passing_score
max_attempts
time_limit_seconds
randomize_questions
```

Relationship:

```text
course_item 1 ─── 0..1 quiz
```

---

# 14. Questions

## 14.1 `questions`

Questions belong to quizzes.

```text
questions
---------
id
quiz_id
type
text
points
position
```

Possible types:

```text
SINGLE_CHOICE
MULTIPLE_CHOICE
TRUE_FALSE
SHORT_TEXT
LONG_TEXT
```

Relationship:

```text
quiz 1 ─── N questions
```

`position` determines the order of questions.

---

# 15. Question Options

## 15.1 `question_options`

Choice-based questions can have options.

```text
question_options
----------------
id
question_id
text
is_correct
position
```

Example:

```text
Question:
What is 2 + 2?

1. 3
2. 4
3. 5
```

The correct answer is represented by:

```text
is_correct = true
```

---

# 16. Quiz Attempts

## 16.1 `quiz_attempts`

Every time a student takes a quiz, an attempt is created.

```text
quiz_attempts
-------------
id
quiz_id
student_id
attempt_number
status
score
started_at
submitted_at
graded_at
```

Example:

```text
Quiz: Python Basics

Student: John

Attempt 1 → 60%
Attempt 2 → 80%
Attempt 3 → 95%
```

This allows `max_attempts` to be enforced.

---

# 17. Quiz Responses

## 17.1 `quiz_responses`

Stores the answers submitted during an attempt.

```text
quiz_responses
--------------
id
attempt_id
question_id
selected_option_id
answer_text
is_correct
points_awarded
```

Two answer mechanisms are supported:

```text
selected_option_id
```

for choice questions, and:

```text
answer_text
```

for text-based questions.

---

# 18. Assignments

## 18.1 `assignments`

Assignments are another specialization of `course_items`.

```text
assignments
-----------
course_item_id
instructions
max_score
due_at
allow_late_submission
```

---

# 19. Assignment Submissions

## 19.1 `assignment_submissions`

A student can submit an assignment.

```text
assignment_submissions
----------------------
id
assignment_id
student_id
attempt_number
status
content
score
feedback
submitted_at
graded_at
graded_by
```

Possible statuses:

```text
DRAFT
SUBMITTED
GRADING
GRADED
RETURNED
```

`graded_by` references a user.

This allows the platform to record which instructor graded the submission.

---

# 20. Submission Media

## 20.1 `submission_media`

Assignments can contain uploaded files.

```text
submission_media
----------------
submission_id
media_id
```

Composite primary key:

```text
(submission_id, media_id)
```

Example:

```text
Assignment Submission

├── solution.pdf
├── source.zip
└── screenshot.png
```

---

# 21. Media Objects

## 21.1 `media_objects`

This table represents files stored in object storage.

```text
media_objects
-------------
id
bucket
object_key
original_filename
mime_type
size_bytes
checksum
status
created_at
```

The database does **not** store the actual binary data.

Instead:

```text
PostgreSQL
    │
    │ metadata
    ▼
media_objects
    │
    │ object_key
    ▼
Object Storage
    │
    ├── MinIO
    ├── Amazon S3
    └── Azure Blob
```

Example:

```text
bucket:
elearning

object_key:
courses/python/videos/introduction.mp4

mime_type:
video/mp4
```

This design is appropriate for large:

- Videos
- PDFs
- ZIP files
- Images
- Source archives
- Certificates

---

# 22. Resource System

The Resource system is separate from lessons.

A resource represents a reusable learning material.

Examples:

```text
Python Cheat Sheet
Git Commands
Course Syllabus
Official Python Documentation
Example Source Code
Exercise PDF
```

A resource can be attached to:

```text
Course
Section
Course Item
```

---

# 23. Resources

## 23.1 `resources`

```text
resources
---------
id
title
description
resource_type
source_type
created_by
created_at
updated_at
```

### Resource type

Defines what the resource represents.

```text
DOCUMENT
SOURCE_CODE
VIDEO
AUDIO
IMAGE
LINK
OTHER
```

### Source type

Defines where the resource content comes from.

```text
FILE
URL
INLINE
```

These are intentionally separate concepts.

For example:

```text
resource_type = DOCUMENT
source_type   = FILE
```

could represent a PDF.

While:

```text
resource_type = DOCUMENT
source_type   = URL
```

could represent a document hosted externally.

---

# 24. Resource Files

## 24.1 `resource_files`

Used when the resource is backed by a physical file.

```text
resource_files
--------------
resource_id
media_id
filename
mime_type
extension
size_bytes
```

The actual file is represented by:

```text
media_id
```

which points to `media_objects`.

---

# 25. Resource URLs

## 25.1 `resource_urls`

Used when the resource points to an external URL.

```text
resource_urls
-------------
resource_id
url
```

Example:

```text
Resource:
Official Python Documentation

resource_type:
LINK

source_type:
URL
```

---

# 26. Resource Contents

## 26.1 `resource_contents`

Used for inline resources.

```text
resource_contents
-----------------
resource_id
content_type
content
```

Possible content types:

```text
MARKDOWN
HTML
PLAIN_TEXT
```

Example:

```text
Resource:
Git Commands

resource_type:
DOCUMENT

source_type:
INLINE

content_type:
MARKDOWN
```

---

# 27. Resource Scope

Resources can be attached at three levels.

```text
                 Resource
                    │
          ┌─────────┼─────────┐
          │         │         │
       Course     Section   CourseItem
```

This allows different use cases.

### Course-level resource

```text
Course
 └── Course Syllabus
```

### Section-level resource

```text
Python Basics
 └── Python Cheat Sheet
```

### Item-level resource

```text
Lesson: Variables
 └── Variables Exercises
```

---

# 28. Course Resources

## 28.1 `course_resources`

```text
course_resources
----------------
course_id
resource_id
relationship_type
position
```

---

# 29. Section Resources

## 29.1 `section_resources`

```text
section_resources
-----------------
section_id
resource_id
relationship_type
position
```

---

# 30. Item Resources

## 30.1 `item_resources`

```text
item_resources
--------------
course_item_id
resource_id
relationship_type
position
```

---

# 31. Resource Relationship Types

The `relationship_type` describes why the resource is attached.

Possible values:

```text
RESOURCE
REFERENCE
ATTACHMENT
SUPPLEMENTARY
REQUIRED
SOLUTION
EXAMPLE
READING
```

For example:

```text
Lesson: REST APIs

Resource: HTTP Specification
relationship_type = REFERENCE
```

or:

```text
Assignment: Build REST API

Resource: Starter Project
relationship_type = ATTACHMENT
```

---

# 32. Why Explicit Resource Tables?

A tempting design would be:

```text
resources
---------
id
scope_type
scope_id
```

where:

```text
scope_type = COURSE
scope_type = SECTION
scope_type = ITEM
```

However, this creates a polymorphic foreign key.

PostgreSQL cannot normally enforce:

```text
scope_id -> courses.id
```

or:

```text
scope_id -> course_sections.id
```

or:

```text
scope_id -> course_items.id
```

depending on another column.

Instead, V1 uses:

```text
course_resources
section_resources
item_resources
```

This makes the relationships explicit and allows PostgreSQL to enforce real foreign keys.

---

# 33. Certificates

## 33.1 `certificates`

Represents a certificate issued to a student after completing a course.

```text
certificates
------------
id
student_id
course_id
media_id
certificate_number
verification_code
issued_at
revoked_at
```

Recommended constraints:

```text
certificate_number UNIQUE
verification_code UNIQUE
```

The certificate file itself can be stored in object storage through `media_objects`.

---

# 34. Discussions

## 34.1 `discussion_threads`

Represents a discussion topic.

```text
discussion_threads
------------------
id
course_id
lesson_id
author_id
title
body
status
created_at
updated_at
```

`lesson_id` is nullable.

Therefore discussions can be:

```text
Course-wide
```

or:

```text
Lesson-specific
```

---

# 35. Discussion Comments

## 35.1 `discussion_comments`

```text
discussion_comments
-------------------
id
thread_id
author_id
parent_id
body
created_at
updated_at
```

`parent_id` references another comment.

This supports nested replies:

```text
Thread
│
├── Comment
│    ├── Reply
│    │    └── Reply
│    │
│    └── Reply
│
└── Comment
```

---

# 36. Course Reviews

## 36.1 `course_reviews`

```text
course_reviews
--------------
id
course_id
student_id
rating
title
body
status
created_at
updated_at
```

Possible ratings:

```text
1
2
3
4
5
```

If the business rule is one review per student per course:

```text
UNIQUE(course_id, student_id)
```

should be added.

---

# 37. Categories

## 37.1 `categories`

Categories are hierarchical.

```text
categories
----------
id
parent_id
name
slug
```

Example:

```text
Programming
│
├── Backend
│   ├── Java
│   └── Python
│
└── Frontend
    ├── Angular
    └── React
```

The `parent_id` creates the hierarchy.

---

# 38. Course Categories

## 38.1 `course_categories`

Many-to-many relationship between courses and categories.

```text
course_categories
-----------------
course_id
category_id
```

Relationship:

```text
courses M ─── N categories
```

---

# 39. Tags

## 39.1 `tags`

Tags are flat labels.

```text
tags
----
id
name
slug
```

Examples:

```text
python
backend
django
rest-api
beginner
```

---

# 40. Course Tags

## 40.1 `course_tags`

```text
course_tags
-----------
course_id
tag_id
```

Relationship:

```text
courses M ─── N tags
```

---

# 41. Course Prerequisites

## 41.1 `course_prerequisites`

Courses can require other courses.

```text
course_prerequisites
--------------------
course_id
prerequisite_course_id
```

Example:

```text
Advanced Python
       │
       └── requires → Python Fundamentals
```

This is a self-referencing many-to-many relationship on `courses`.

---

# 42. Course Item Prerequisites

## 42.1 `item_prerequisites`

Individual learning items can also have prerequisites.

```text
item_prerequisites
------------------
item_id
prerequisite_item_id
```

Example:

```text
Final Quiz
   │
   ├── requires → Variables
   ├── requires → Functions
   └── requires → Classes
```

This is a self-referencing many-to-many relationship on `course_items`.

---

# 43. Notifications

## 43.1 `notifications`

Represents a logical notification sent to a user.

```text
notifications
-------------
id
user_id
type
title
body
data
read_at
created_at
```

`data` is JSONB and can contain contextual information.

Example:

```json
{
  "course_id": "uuid",
  "assignment_id": "uuid"
}
```

This allows notification types to carry additional information without changing the schema.

---

# 44. Notification Deliveries

## 44.1 `notification_deliveries`

Tracks how notifications are delivered.

```text
notification_deliveries
-----------------------
id
notification_id
channel
status
sent_at
failure_reason
```

Possible channels:

```text
IN_APP
EMAIL
PUSH
```

This separates:

```text
Notification
```

from:

```text
Delivery
```

For example, one notification can be delivered through:

```text
IN_APP → SENT
EMAIL  → SENT
PUSH   → FAILED
```

---

# 45. Complete Domain Flow

A typical student interaction looks like this:

```text
User
 │
 │ enrolls
 ▼
Enrollment
 │
 ▼
Course
 │
 ├── Section
 │     │
 │     ├── CourseItem
 │     │     └── Lesson
 │     │
 │     ├── CourseItem
 │     │     └── Lesson
 │     │
 │     └── CourseItem
 │           └── Quiz
 │                 │
 │                 ├── Questions
 │                 │     └── Options
 │                 │
 │                 └── Attempts
 │                       └── Responses
 │
 └── Resources
```

During learning:

```text
Student
   │
   ├── Enrollment
   │
   ├── LearningProgress
   │
   ├── QuizAttempt
   │
   ├── AssignmentSubmission
   │
   ├── DiscussionComment
   │
   ├── CourseReview
   │
   └── Certificate
```

---

# 46. Database Relationship Summary

## Identity

```text
USERS
  │
  └── USER_PROFILES
```

## Course management

```text
USERS
  │
  ├── COURSES
  │
  └── COURSE_INSTRUCTORS

COURSE
  │
  └── COURSE_SECTIONS
        │
        └── COURSE_ITEMS
```

## Lessons

```text
COURSE_ITEM
    │
    └── LESSON
          │
          ├── VIDEO_CONTENT
          ├── ARTICLE_CONTENT
          └── DOCUMENT_CONTENT
```

## Assessments

```text
QUIZ
 │
 ├── QUESTIONS
 │     └── QUESTION_OPTIONS
 │
 └── QUIZ_ATTEMPTS
       └── QUIZ_RESPONSES
```

```text
ASSIGNMENT
    │
    └── ASSIGNMENT_SUBMISSIONS
          │
          └── SUBMISSION_MEDIA
```

## Student activity

```text
USER
 │
 ├── ENROLLMENT
 ├── LEARNING_PROGRESS
 ├── QUIZ_ATTEMPT
 ├── ASSIGNMENT_SUBMISSION
 └── CERTIFICATE
```

## Resources

```text
RESOURCE
 │
 ├── RESOURCE_FILE
 │     └── MEDIA_OBJECT
 │
 ├── RESOURCE_URL
 │
 └── RESOURCE_CONTENT
```

```text
RESOURCE
 │
 ├── COURSE_RESOURCE
 ├── SECTION_RESOURCE
 └── ITEM_RESOURCE
```

---

# 47. Important Constraints

The following constraints are recommended for V1.

## Users

```text
users.email UNIQUE
users.username UNIQUE
```

## Enrollments

If one enrollment is allowed:

```text
UNIQUE(student_id, course_id)
```

## Progress

```text
UNIQUE(student_id, course_item_id)
```

## Reviews

If one review per student/course:

```text
UNIQUE(course_id, student_id)
```

## Categories

```text
categories.slug UNIQUE
```

## Tags

```text
tags.slug UNIQUE
```

## Certificates

```text
certificates.certificate_number UNIQUE
certificates.verification_code UNIQUE
```

## Resource relationships

Each junction table should have a composite primary key:

```text
course_resources
PK(course_id, resource_id)

section_resources
PK(section_id, resource_id)

item_resources
PK(course_item_id, resource_id)
```

---

# 48. Ordering

Ordering is handled using integer `position` fields.

Relevant tables:

```text
course_sections.position
course_items.position
questions.position
question_options.position
course_resources.position
section_resources.position
item_resources.position
```

Example:

```text
Section
 ├── position 1
 ├── position 2
 ├── position 3
 └── position 4
```

This makes reordering straightforward.

---

# 49. Object Storage Strategy

The database should not contain large binary files.

Instead:

```text
                    ┌───────────────┐
                    │   PostgreSQL  │
                    │               │
                    │ media_objects │
                    └───────┬───────┘
                            │
                       object_key
                            │
                            ▼
                    ┌───────────────┐
                    │ Object Storage│
                    │               │
                    │    MinIO      │
                    │      or       │
                    │      S3       │
                    └───────────────┘
```

The application uses the database to know:

```text
what the file is
where it is
what type it is
how large it is
```

while object storage handles:

```text
the actual bytes
```

---

# 50. Why This Model Is Extensible

The architecture intentionally separates:

```text
Structure
```

from:

```text
Content
```

and:

```text
Content
```

from:

```text
Storage
```

For example:

```text
CourseItem
    │
    └── Lesson
          │
          └── VideoContent
                  │
                  └── MediaObject
                          │
                          └── Object Storage
```

Each layer has a distinct responsibility.

This means changing storage providers does not require redesigning the lesson model.

Similarly, adding a new learning activity does not require redesigning courses:

```text
CourseItem
    │
    ├── Lesson
    ├── Quiz
    ├── Assignment
    ├── Exam          ← future
    ├── Project       ← future
    └── LiveSession   ← future
```

---

# 51. V1 Scope

The first version includes:

### Identity

- Users
- User profiles

### Course management

- Courses
- Instructors
- Sections
- Course items

### Learning content

- Lessons
- Video lessons
- Article lessons
- Document lessons
- Generic resources
- Media objects

### Assessment

- Quizzes
- Questions
- Question options
- Quiz attempts
- Quiz responses
- Assignments
- Assignment submissions
- Submission files

### Student activity

- Enrollments
- Learning progress
- Certificates

### Community

- Discussion threads
- Discussion comments
- Course reviews

### Discovery

- Categories
- Tags
- Course prerequisites
- Item prerequisites

### Platform

- Notifications
- Notification deliveries

---

# 52. Explicitly Excluded From V1

The following are intentionally not modeled yet:

```text
Organizations
Tenants
Teams
Subscriptions
Payments
Orders
Invoices
Coupons
Affiliate systems
Instructor payouts
Live video infrastructure
Advanced analytics
SCORM runtime
LTI
Proctoring
Gamification
Badges
Learning paths
Cohorts
Attendance
Calendar
```

These can be introduced later when their business requirements are known.

---

# 53. Core Design Philosophy

The V1 model should remain relatively simple.

The most important abstractions are:

```text
User
Course
Section
CourseItem
Lesson
Quiz
Assignment
Enrollment
Progress
Resource
MediaObject
```

Everything else supports these concepts.

The central learning model is:

```text
                    COURSE
                       │
                    SECTION
                       │
                   COURSE ITEM
                 /      |       \
             LESSON    QUIZ   ASSIGNMENT
               │        │         │
            CONTENT   QUESTIONS  SUBMISSIONS
               │        │
             MEDIA    ATTEMPTS
```

The central resource model is:

```text
                  RESOURCE
                 /    |     \
              FILE   URL   INLINE
               │
          MEDIA_OBJECT
               │
        OBJECT STORAGE
```

And resources can be attached to:

```text
COURSE
SECTION
COURSE ITEM
```

This provides a clean foundation for the first backend implementation while leaving room for the platform to evolve without forcing premature complexity.



| Backend domain | Database entities                                                                                                                                                                                                                                        |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `identity`     | `users`, `user_profiles`                                                                                                                                                                                                                                 |
| `courses`      | `courses`, `course_instructors`, `course_sections`, `course_items`, `categories`, `course_categories`, `tags`, `course_tags`, `course_prerequisites`, `item_prerequisites`                                                                               |
| `learning`     | `lessons`, `video_contents`, `article_contents`, `document_contents`, `resources`, `resource_files`, `resource_urls`, `resource_contents`, `course_resources`, `section_resources`, `item_resources`, `enrollments`, `learning_progress`, `certificates` |
| `assessment`   | `quizzes`, `questions`, `question_options`, `quiz_attempts`, `quiz_responses`, `assignments`, `assignment_submissions`, `submission_media`                                                                                                               |
| `community`    | `discussion_threads`, `discussion_comments`, `course_reviews`                                                                                                                                                                                            |
| `platform`     | `media_objects`, `notifications`, `notification_deliveries`                                                                                                                                                                                              |

