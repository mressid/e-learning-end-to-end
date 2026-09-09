import type { components, paths } from "./openapi";

export type { paths };

export type Schemas = components["schemas"];

// Auth & User
export type ApiError = Schemas["ApiError"];
export type ApiFieldError = Schemas["ApiFieldError"];
export type LoginRequest = Schemas["LoginRequest"];
export type RegisterRequest = Schemas["RegisterRequest"];
export type TokenResponse = Schemas["TokenResponse"];
export type RefreshRequest = Schemas["RefreshRequest"];
export type PasswordResetRequest = Schemas["PasswordResetRequest"];
export type VerifyEmailRequest = Schemas["VerifyEmailRequest"];
export type UserResponse = Schemas["UserResponse"];
export type ProfileResponse = Schemas["ProfileResponse"];
export type UpdateProfileRequest = Schemas["UpdateProfileRequest"];

// Courses
export type CourseResponse = Schemas["CourseResponse"];
export type CreateCourseRequest = Schemas["CreateCourseRequest"];
export type UpdateCourseRequest = Schemas["UpdateCourseRequest"];
export type SetCategoriesRequest = Schemas["SetCategoriesRequest"];
export type SetTagsRequest = Schemas["SetTagsRequest"];
export type SetThumbnailRequest = Schemas["SetThumbnailRequest"];
export type CourseProgressResponse = Schemas["CourseProgressResponse"];
/**
 * One page of anything.
 *
 * The generated document now names each instantiation separately
 * (`PageResponseCourseResponse`, `PageResponseSessionResponse`, …), which is
 * what makes every element type visible at all. The envelope is identical
 * across all of them, so this borrows one concrete page's shape and swaps the
 * element type in — pagination stays derived from the spec rather than
 * hand-copied, while callers keep writing `PageResponse<Whatever>`.
 */
export type PageResponse<T = CourseResponse> = Omit<
  Schemas["PageResponseCourseResponse"],
  "content"
> & {
  content?: T[];
};

// Curriculum: Sections & Items
export type SectionResponse = Schemas["SectionResponse"];
export type CreateSectionRequest = Schemas["CreateSectionRequest"];
export type CourseItemResponse = Schemas["CourseItemResponse"];
export type CreateCourseItemRequest = Schemas["CreateCourseItemRequest"];
export type ReorderRequest = Schemas["ReorderRequest"];

// Learning Units: Lessons, Quizzes, Assignments
export type LessonResponse = Schemas["LessonResponse"];
export type SaveLessonRequest = Schemas["SaveLessonRequest"];
export type QuizResponse = Schemas["QuizResponse"];
export type SaveQuizRequest = Schemas["SaveQuizRequest"];
export type AddQuestionRequest = Schemas["AddQuestionRequest"];
export type StudentQuestionResponse = Schemas["StudentQuestionResponse"];
export type AuthorQuestionResponse = Schemas["AuthorQuestionResponse"];
export type AssignmentResponse = Schemas["AssignmentResponse"];
export type SaveAssignmentRequest = Schemas["SaveAssignmentRequest"];
export type SubmissionResponse = Schemas["SubmissionResponse"];
export type SubmitAssignmentRequest = Schemas["SubmitAssignmentRequest"];
export type GradeSubmissionRequest = Schemas["GradeSubmissionRequest"];

// Taxonomy
export type CategoryResponse = Schemas["CategoryResponse"];
export type Term = Schemas["Term"];

// Instructors
export type InstructorResponse = Schemas["InstructorResponse"];
export type AddInstructorRequest = Schemas["AddInstructorRequest"];

// Reviews & Discussions
export type ReviewResponse = Schemas["ReviewResponse"];
export type RatingSummaryResponse = Schemas["RatingSummaryResponse"];
export type ModerateReviewRequest = Schemas["ModerateReviewRequest"];
export type ThreadResponse = Schemas["ThreadResponse"];
export type CommentResponse = Schemas["CommentResponse"];

// Certificates
export type CertificateResponse = Schemas["CertificateResponse"];
export type CertificateVerificationResponse = Schemas["CertificateVerificationResponse"];
export type CertificateDownloadUrlResponse = Schemas["CertificateDownloadUrlResponse"];

// Notifications
export type NotificationResponse = Schemas["NotificationResponse"];
export type UnreadCountResponse = Schemas["UnreadCountResponse"];

// Media
export type UploadTicketResponse = Schemas["UploadTicketResponse"];
export type RequestUploadRequest = Schemas["RequestUploadRequest"];
export type MediaObjectResponse = Schemas["MediaObjectResponse"];

// Resumable multipart upload. Large video goes straight to object storage in
// parts; the API only ever signs URLs, so bytes never pass through it.
export type BeginMultipartUploadRequest = Schemas["BeginMultipartUploadRequest"];
export type MultipartUploadTicket = Schemas["MultipartUploadTicket"];
export type UploadPartUrl = Schemas["UploadPartUrl"];
export type UploadedPartResponse = Schemas["UploadedPartResponse"];
export type PartRef = Schemas["PartRef"];
export type CompleteMultipartUploadRequest = Schemas["CompleteMultipartUploadRequest"];
export type LessonContentUrlResponse = Schemas["LessonContentUrlResponse"];
export type DownloadUrlResponse = Schemas["DownloadUrlResponse"];

// Administration. A separate table from learners with its own sign-in — an
// admin token is rejected on learner routes and vice versa.
export type AdminLoginRequest = Schemas["AdminLoginRequest"];
export type AdminRefreshRequest = Schemas["AdminRefreshRequest"];
export type AdminTokenResponse = Schemas["AdminTokenResponse"];
export type AdminResponse = Schemas["AdminResponse"];
export type CreateAdminRequest = Schemas["CreateAdminRequest"];
export type SetAdminStatusRequest = Schemas["SetAdminStatusRequest"];
export type SetPasswordRequest = Schemas["SetPasswordRequest"];
export type ChangePasswordRequest = Schemas["ChangePasswordRequest"];

// Roles & permissions. Roles are configurable; the super admin holds every
// permission code implicitly rather than through a role.
export type PermissionResponse = Schemas["PermissionResponse"];
export type RoleResponse = Schemas["RoleResponse"];
export type RoleSummary = Schemas["RoleSummary"];
export type CreateRoleRequest = Schemas["CreateRoleRequest"];
export type SetRolePermissionsRequest = Schemas["SetRolePermissionsRequest"];

// User & content administration
export type DirectoryUserResponse = Schemas["DirectoryUserResponse"];
export type SetUserStatusRequest = Schemas["SetUserStatusRequest"];
export type SetUserPasswordRequest = Schemas["SetUserPasswordRequest"];
export type CreateUserRequest = Schemas["CreateUserRequest"];
export type UpdateUserRequest = Schemas["UpdateUserRequest"];
export type AdminCertificateResponse = Schemas["AdminCertificateResponse"];
export type CreateCategoryRequest = Schemas["CreateCategoryRequest"];
export type RenameCategoryRequest = Schemas["RenameCategoryRequest"];
export type EmailRequest = Schemas["EmailRequest"];

// Prerequisites. Course-level ones are free text — deliberately not a link to
// another course, so unpublishing cannot strand a learner mid-catalogue. Item
// level ones stay real references, being confined to one course.
export type CoursePrerequisite = Schemas["CoursePrerequisite"];
export type SetCoursePrerequisitesRequest = Schemas["SetCoursePrerequisitesRequest"];
export type PrerequisiteResponse = Schemas["PrerequisiteResponse"];
export type SetItemPrerequisitesRequest = Schemas["SetItemPrerequisitesRequest"];

// Quiz attempts & grading
export type AttemptResponse = Schemas["AttemptResponse"];
export type AttemptResultResponse = Schemas["AttemptResultResponse"];
export type AttemptGradingResponse = Schemas["AttemptGradingResponse"];
export type SubmitAttemptRequest = Schemas["SubmitAttemptRequest"];
export type AnswerRequest = Schemas["AnswerRequest"];
export type GradeAttemptRequest = Schemas["GradeAttemptRequest"];
export type ResponseGradeRequest = Schemas["ResponseGradeRequest"];
export type ResponseToGrade = Schemas["ResponseToGrade"];
export type QuestionOptionRequest = Schemas["QuestionOptionRequest"];
export type AuthorQuestionOption = Schemas["AuthorQuestionOption"];
export type StudentOptionResponse = Schemas["StudentOptionResponse"];

// Enrolment & progress
export type EnrollmentResponse = Schemas["EnrollmentResponse"];
export type ProgressResponse = Schemas["ProgressResponse"];
export type RecordProgressRequest = Schemas["RecordProgressRequest"];

// Resources attached to an item
export type ResourceResponse = Schemas["ResourceResponse"];
export type CreateResourceRequest = Schemas["CreateResourceRequest"];
export type AttachResourceRequest = Schemas["AttachResourceRequest"];
export type AttachedResourceResponse = Schemas["AttachedResourceResponse"];
export type ResourceDownloadUrlResponse = Schemas["ResourceDownloadUrlResponse"];

// Community: reviews & discussion
export type WriteReviewRequest = Schemas["WriteReviewRequest"];
export type CreateThreadRequest = Schemas["CreateThreadRequest"];
export type ThreadDetailResponse = Schemas["ThreadDetailResponse"];
export type ChangeThreadStatusRequest = Schemas["ChangeThreadStatusRequest"];
export type AddCommentRequest = Schemas["AddCommentRequest"];

// --- Administration: the directory ----------------------------------------
// These became expressible only once PageResponse stopped erasing its element
// type; before that they existed in the API but not in the document.
export type AdminCourseResponse = Schemas["AdminCourseResponse"];
export type InstructorRosterEntry = Schemas["InstructorRosterEntry"];
export type AdminMediaResponse = Schemas["AdminMediaResponse"];
export type AdminSubmissionResponse = Schemas["AdminSubmissionResponse"];

// --- Administration: accountability ---------------------------------------
/** One live session. `sessionId` is what ends it. */
export type SessionResponse = Schemas["SessionResponse"];
/** One audit record. Append-only: nothing edits or deletes these. */
export type AuditEntryResponse = Schemas["AuditEntryResponse"];
