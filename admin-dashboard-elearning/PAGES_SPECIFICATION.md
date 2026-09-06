# Platform Pages and View Architecture Specification

This document details all existing, configured, and planned pages for the Lernova E-Learning Admin Dashboard. In alignment with our platform architectural rules, all detail views, inspection panels, filters, and creation forms follow the **Floating Sidebar / Slide-Over Drawer** pattern rather than centered modal popups.

---

## 1. High-Level Page Map

```text
Lernova Admin Platform
│
├── Public & Auth
│   ├── /login                     (Sign in with email/password, SSO, Remember Me)
│   ├── /forgot-password           (Password reset request)
│   └── /reset-password            (Token validation and new password submission)
│
├── Dashboard & Intelligence
│   ├── /                          (Executive overview, revenue metrics, top courses, device stats)
│   └── /analytics                 (Deep-dive metrics: completions, retention, watch time, cohorts)
│
├── Course Management
│   ├── /courses                   (Catalog grid/table, status filters, bulk actions)
│   ├── /courses/[courseId]        (Curriculum builder, sections, lessons, quizzes, assignments)
│   └── /categories                (Taxonomy, tags, prerequisites)
│
├── Users & Instructors
│   ├── /students                  (Learner directory, enrollments, activity status, progress)
│   └── /instructors               (Instructor roster, course ownership, bios, performance)
│
├── Academic & Assessment
│   ├── /certificates              (Issued credentials, verification codes, revocation, templates)
│   ├── /submissions               (Student assignment grading queue and feedback)
│   └── /schedule                  (Live sessions, webinars, cohort office hours, calendar)
│
├── Communication & Community
│   ├── /messages                  (Direct messages, support threads, announcements)
│   ├── /reviews                   (Student ratings, moderation, approval queue)
│   └── /support                   (Helpdesk tickets, system health, documentation)
│
└── System & Configuration
    ├── /settings                  (Theme, multi-language, security, notifications, webhooks)
    ├── /media                     (Central asset library, video uploads, HLS status)
    └── /audit-logs                (System audit trail, access logs, sensitive operations)
```

---

## 2. Page Specifications

### 2.1. Authentication and Access

#### Page: Login (`/login`)

- **Status**: Implemented.
- **Purpose**: Secure administrator and staff sign-in.
- **Layout**: Split-screen design with hero promotional branding on one side and the clean authentication form on the other. Includes quick demo credentials, theme toggle, and text-only language selector.
- **Key Features**:
  - Email and password input with show/hide password toggle.
  - "Remember me" session persistence.
  - Single Sign-On (SSO) shortcuts (Google, GitHub).
  - CSRF protection and rate limiting headers.
- **Drawers & Sidebars**: None (stand-alone full-screen layout bypassing dashboard chrome).

---

### 2.2. Executive Dashboard & Analytics

#### Page: Executive Overview (`/`)

- **Status**: Implemented.
- **Purpose**: Real-time operational summary of platform health, revenues, and active learners.
- **Layout**: High-density grid with top metric cards, revenue area charts, peak daily activity bar charts, device usage donut charts, top courses table, and upcoming live session widgets.
- **Floating Sidebar / Drawer Elements**:
  - **Course Detail Inspector**: Clicking any row in the "Top Courses" table slides open `FloatingDetailSheet` from the trailing edge (right in LTR, left in RTL) showing student volume, watch hours, satisfaction meter, syllabus outline, and action triggers without navigating away.
- **Backend Entities**: `COURSES`, `ENROLLMENTS`, `USERS`, `ORDERS`.

#### Page: Analytics (`/analytics`)

- **Status**: Configured route (Placeholder).
- **Purpose**: Deep analytical intelligence across engagement, retention, and content effectiveness.
- **Key Views**:
  - **Enrollment Velocity**: Weekly and monthly growth trends.
  - **Drop-off Funnel**: Video drop-off points (second-by-second analytics) and lesson abandon rates.
  - **Quiz Performance**: Pass/fail distributions, hardest question analysis.
  - **Cohort Retention**: Matrix showing week-over-week learner return rates.
- **Floating Sidebar / Drawer Elements**:
  - **Cohort Inspector Drawer**: Inspects detailed student lists belonging to a specific retention cohort.
  - **Date Range & Filter Sheet**: Advanced filters for instructor, category, and date boundaries.

---

### 2.3. Course Catalog and Curriculum Management

#### Page: Course Catalog (`/courses`)

- **Status**: Configured route (Placeholder).
- **Purpose**: Central index for publishing, categorizing, and auditing all platform courses.
- **Key Views**:
  - Multi-view layout: Data table with sorting or visual card grid.
  - Status indicators: Draft, In Review, Published, Archived.
  - Bulk actions: Change status, assign category, export CSV.
- **Floating Sidebar / Drawer Elements**:
  - **Create Course Drawer**: Full-height slide-over form for basic info (title, slug, level, language, category).
  - **Course Preview Inspector**: Slide-over panel displaying thumbnail, instructor, curriculum count, price, and publication history.
  - **Filter & Facet Sheet**: Filter by level (Beginner, Intermediate, Advanced), price, status, or instructor.
- **Backend Entities**: `COURSES`, `COURSE_INSTRUCTORS`, `COURSE_SECTIONS`, `CATEGORIES`.

#### Page: Curriculum & Lesson Builder (`/courses/[courseId]`)

- **Status**: Planned subroute.
- **Purpose**: Interactive tree builder for constructing sections, lessons, quizzes, and resources.
- **Key Views**:
  - Drag-and-drop section reordering.
  - Course item hierarchy (Sections -> Lessons / Quizzes / Assignments).
  - Video upload status (HLS encoding, thumbnail extraction, duration).
- **Floating Sidebar / Drawer Elements**:
  - **Lesson Editor Sheet**: Rich text editor for articles, video URL/uploader for video lessons, attachment manager for downloadable resources.
  - **Quiz & Question Builder Sheet**: Drawer for creating questions (multiple choice, single choice, code snippet) and setting passing thresholds.

---

### 2.4. Users, Students, and Instructors

#### Page: Student Directory (`/students`)

- **Status**: Configured route (Placeholder).
- **Purpose**: Administration of enrolled learners, enrollment status, and progress metrics.
- **Key Views**:
  - Searchable data grid with avatar, name, email, enrolled courses, last active timestamp, and account status.
  - Quick filters: Active, Inactive, Suspended, Completed at least one course.
- **Floating Sidebar / Drawer Elements**:
  - **Student Dossier Sheet**: Slides from trailing edge to display full student profile, enrolled course list with individual progress percentages, quiz scores, and payment history.
  - **Enroll Student Drawer**: Slide-over form to manually enroll a student into courses or grant scholarship access.
  - **Account Status Action Sheet**: Form to modify account status or reset multi-factor authentication.
- **Backend Entities**: `USERS`, `USER_PROFILES`, `ENROLLMENTS`, `COURSE_ITEM_PROGRESS`.

#### Page: Instructors (`/instructors`)

- **Status**: Configured route (Placeholder).
- **Purpose**: Management of teaching staff, revenue share allocations, and course assignments.
- **Key Views**:
  - Instructor cards with total courses taught, active students, average rating, and payout status.
- **Floating Sidebar / Drawer Elements**:
  - **Instructor Profile Inspector**: Displays teaching history, bio, linked courses, student feedback, and payout records.
  - **Add / Invite Instructor Drawer**: Slide-over invitation form with role assignments and commission rates.
- **Backend Entities**: `USERS`, `COURSE_INSTRUCTORS`, `COURSES`.

---

### 2.5. Assessments, Submissions, and Credentials

#### Page: Certificates (`/certificates`)

- **Status**: Configured route (Placeholder).
- **Purpose**: Management of accredited completion certificates, verification codes, and revocation.
- **Key Views**:
  - Ledger of issued certificates with unique verification UUIDs, recipient names, course titles, and issue dates.
  - Public verification URL generator.
- **Floating Sidebar / Drawer Elements**:
  - **Certificate Preview Drawer**: Renders a live preview of the generated SVG/PDF certificate along with cryptographic hash and issue metadata.
  - **Manual Issue Drawer**: Form to manually grant a certificate for external or exceptional completions.
  - **Revocation Alert**: Uses `AlertDialog` only for the irreversible action of revoking an issued credential.
- **Backend Entities**: `CERTIFICATES`, `ENROLLMENTS`, `COURSES`.

#### Page: Schedule and Live Sessions (`/schedule`)

- **Status**: Configured route (Placeholder).
- **Purpose**: Scheduling and monitoring live webinars, cohort office hours, and interactive Q&A workshops.
- **Key Views**:
  - Monthly calendar view and chronological agenda list.
  - Live session cards showing room status (Upcoming, Broadcasting, Concluded), instructor, and attendee RSVPs.
- **Floating Sidebar / Drawer Elements**:
  - **Session Details Inspector**: Slide-over drawer with meeting URL, host details, registered student roster, and recording playback link.
  - **Create Live Event Drawer**: Slide-over form to schedule new workshops, set attendee caps, and select integrated streaming platforms.

---

### 2.6. Community, Communication, and Support

#### Page: Messages and Communications (`/messages`)

- **Status**: Configured route (Placeholder).
- **Purpose**: Direct messaging between administrators, instructors, and student inquiries.
- **Key Views**:
  - Master-detail conversational interface: thread list on the left, active chat stream in the center.
- **Floating Sidebar / Drawer Elements**:
  - **Conversation Info Inspector**: Slides over to reveal participant profile, course enrollment context, and account flags without leaving the chat thread.

#### Page: Support & Helpdesk (`/support`)

- **Status**: Configured route (Placeholder).
- **Purpose**: System help center, ticket resolution, documentation, and operational platform status.
- **Key Views**:
  - Open support tickets categorized by billing, content access, and technical bugs.
  - System status summary (API, Database, CDN, Video Encoding pipeline).

---

### 2.7. System and Platform Configuration

#### Page: Settings (`/settings`)

- **Status**: Implemented.
- **Purpose**: Administrative preferences, display customizations, and operational parameters.
- **Sections**:
  - **Appearance & Theme**: Light, Dark, and System preference selection.
  - **Language & Localization**: Text-only selector supporting English, French, Spanish, and Arabic with automatic layout mirroring.
  - **Security & Sessions**: Active session manager and token lifecycle settings.
  - **Webhooks & API Keys**: Management of API keys and webhook endpoints for LMS integrations.

#### Page: Media Library (`/media`)

- **Status**: Planned route.
- **Purpose**: Centralized storage manager for video assets, lecture PDFs, image thumbnails, and attachments.
- **Key Views**:
  - Grid and list view of S3/MinIO assets with mime-type icons, file sizes, and usage references.
- **Floating Sidebar / Drawer Elements**:
  - **Asset Inspector Drawer**: File preview, direct CDN URL copy, transcoding resolution options, and list of courses currently embedding this media asset.
  - **Upload Slide-Over**: Multi-file chunked uploader drawer with real-time progress bars.

---

## 3. UI Component Matrix for All Views

| Page Route      | Primary Content Layout        | Slide-Over Drawer Use Cases                     | Modals (`AlertDialog`)        |
| :-------------- | :---------------------------- | :---------------------------------------------- | :---------------------------- |
| `/`             | Multi-widget KPI Dashboard    | Course details, Live event breakdown            | None                          |
| `/login`        | Split Hero + Form             | None (Self-contained)                           | None                          |
| `/courses`      | Filterable Data Grid / Cards  | New course form, Course quick preview, Filters  | Delete course confirmation    |
| `/courses/[id]` | Section & Item Drag Tree      | Lesson editor, Quiz question builder            | Delete section/item           |
| `/students`     | User Data Table               | Student dossier, Manual enrollment form         | Ban/delete user account       |
| `/instructors`  | Instructor Grid               | Instructor profile, Add instructor form         | Remove instructor from course |
| `/certificates` | Issuance Ledger               | Certificate preview, Manual grant drawer        | Revoke certificate            |
| `/schedule`     | Calendar + Agenda List        | Event inspector, Schedule new session           | Cancel live broadcast         |
| `/messages`     | Split Thread + Chat Stream    | Participant info, Student course history        | Archive thread                |
| `/analytics`    | Time-series Charts & Funnels  | Cohort drill-down, Custom date filter drawer    | None                          |
| `/settings`     | Grouped Settings Sections     | Webhook generator, API token display            | Regenerate secret key         |
| `/support`      | Ticket Table + Knowledge Base | Ticket response drawer, System diagnostic info  | Close ticket                  |
| `/media`        | Asset Grid with Thumbnails    | Media metadata inspector, Chunk uploader drawer | Permanent asset deletion      |

---

## 4. Architectural Rules Summary

1. **Floating Sidebar First**: Never open a centered modal to display entity details or edit records. Use the trailing-edge `FloatingDetailSheet`.
2. **Directional Awareness**: All slide-over drawers automatically invert placement depending on the active locale (`right` for LTR, `left` for RTL).
3. **Strictly Zero Emojis**: Use typographic tags, Lucide icons, or badges across all current and future page templates.
4. **Typographic Language Selector**: Represent languages with clear text labels (`English`, `Français`, `Español`, `العربية`) without flag imagery.
