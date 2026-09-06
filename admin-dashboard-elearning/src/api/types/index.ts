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
export type PageResponse<T = CourseResponse> = Omit<Schemas["PageResponse"], "content"> & {
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
