import type { components, paths } from "./openapi";

export type { paths };

export type Schemas = components["schemas"];

// Auth and the signed-in person
export type ApiError = Schemas["ApiError"];
export type ApiFieldError = Schemas["ApiFieldError"];
export type LoginRequest = Schemas["LoginRequest"];
export type TokenResponse = Schemas["TokenResponse"];
export type RefreshRequest = Schemas["RefreshRequest"];
export type UserResponse = Schemas["UserResponse"];
export type ProfileResponse = Schemas["ProfileResponse"];
export type UpdateProfileRequest = Schemas["UpdateProfileRequest"];

// Courses
export type CourseResponse = Schemas["CourseResponse"];
export type CreateCourseRequest = Schemas["CreateCourseRequest"];
export type UpdateCourseRequest = Schemas["UpdateCourseRequest"];
export type SectionResponse = Schemas["SectionResponse"];
export type CreateSectionRequest = Schemas["CreateSectionRequest"];
export type UpdateSectionRequest = Schemas["UpdateSectionRequest"];
export type CourseItemResponse = Schemas["CourseItemResponse"];
export type CreateCourseItemRequest = Schemas["CreateCourseItemRequest"];
export type UpdateCourseItemRequest = Schemas["UpdateCourseItemRequest"];
export type ReorderRequest = Schemas["ReorderRequest"];

// Lessons
export type LessonResponse = Schemas["LessonResponse"];
export type SaveLessonRequest = Schemas["SaveLessonRequest"];

// Resources — documents, links and inline notes
export type ResourceResponse = Schemas["ResourceResponse"];
export type CreateResourceRequest = Schemas["CreateResourceRequest"];
export type AttachResourceRequest = Schemas["AttachResourceRequest"];
export type AttachedResourceResponse = Schemas["AttachedResourceResponse"];

// Media
export type RequestUploadRequest = Schemas["RequestUploadRequest"];
export type UploadTicketResponse = Schemas["UploadTicketResponse"];
export type MediaObjectResponse = Schemas["MediaObjectResponse"];

/** What a resource is. `LINK` is "the link is the thing"; a PDF you link to is DOCUMENT + URL. */
export type ResourceType = NonNullable<CreateResourceRequest["resourceType"]>;
/** Where its content lives: an uploaded file, a URL, or text typed in place. */
export type SourceType = NonNullable<CreateResourceRequest["sourceType"]>;
/** What the resource is *to* the thing it hangs off. */
export type RelationshipType = NonNullable<AttachResourceRequest["relationshipType"]>;
/** Lessons, quizzes and assignments share one ordered sequence. */
export type CourseItemType = NonNullable<CreateCourseItemRequest["type"]>;
/** When a lesson counts as done. */
export type LessonCompletionRule = NonNullable<SaveLessonRequest["completionRule"]>;
/** How the text of an INLINE lesson is written. */
export type LessonContentFormat = NonNullable<SaveLessonRequest["contentFormat"]>;

/**
 * One page of anything.
 *
 * The generated document names each instantiation separately
 * (`PageResponseCourseResponse`, …), which is what makes the element type
 * visible at all. The envelope is identical across all of them, so this borrows
 * one concrete page's shape and swaps the element type in — pagination stays
 * derived from the spec rather than hand-copied.
 */
export type PageResponse<T = CourseResponse> = Omit<
  Schemas["PageResponseCourseResponse"],
  "content"
> & {
  content?: T[];
};
