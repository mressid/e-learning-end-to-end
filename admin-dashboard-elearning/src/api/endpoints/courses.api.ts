import { apiClient, parseApiError } from "../client";
import type {
  CourseResponse,
  CreateCourseRequest,
  UpdateCourseRequest,
  PageResponse,
  SectionResponse,
  CreateSectionRequest,
  CourseItemResponse,
  CreateCourseItemRequest,
  LessonResponse,
  SaveLessonRequest,
  QuizResponse,
  SaveQuizRequest,
  AddQuestionRequest,
  AuthorQuestionResponse,
  AssignmentResponse,
  SaveAssignmentRequest,
  InstructorResponse,
  AddInstructorRequest,
  ReviewResponse,
  RatingSummaryResponse,
  Term,
} from "../types";

export interface CourseListParams {
  page?: number;
  q?: string;
  size?: number;
}

export const coursesApi = {
  async listCourses(params?: CourseListParams): Promise<PageResponse<CourseResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/courses", {
      params: params ? { query: params } : {},
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<CourseResponse>;
  },

  async getCourse(id: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{id}", {
      params: { path: { id } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async createCourse(body: CreateCourseRequest): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async updateCourse(id: string, body: UpdateCourseRequest): Promise<CourseResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/courses/{id}", {
      params: { path: { id } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async publishCourse(id: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/publish", {
      params: { path: { id } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async unpublishCourse(id: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/unpublish", {
      params: { path: { id } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async archiveCourse(id: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/archive", {
      params: { path: { id } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async setCategories(id: string, categoryIds: string[]): Promise<Term[]> {
    const { data, error } = await apiClient.PUT("/api/v1/courses/{id}/categories", {
      params: { path: { id } },
      body: { categoryIds },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async setTags(id: string, tags: string[]): Promise<Term[]> {
    const { data, error } = await apiClient.PUT("/api/v1/courses/{id}/tags", {
      params: { path: { id } },
      body: { tags },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async setThumbnail(id: string, mediaId: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/thumbnail", {
      params: { path: { id } },
      body: { mediaId },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async getSections(courseId: string): Promise<SectionResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{id}/sections", {
      params: { path: { id: courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async createSection(courseId: string, body: CreateSectionRequest): Promise<SectionResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/sections", {
      params: { path: { id: courseId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async reorderSections(courseId: string, orderedIds: string[]): Promise<void> {
    const { error } = await apiClient.PUT("/api/v1/courses/{id}/sections/order", {
      params: { path: { id: courseId } },
      body: { orderedIds },
    });
    if (error) throw parseApiError(error);
  },

  async getItems(sectionId: string): Promise<CourseItemResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/sections/{sectionId}/items", {
      params: { path: { sectionId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async createItem(sectionId: string, body: CreateCourseItemRequest): Promise<CourseItemResponse> {
    const { data, error } = await apiClient.POST("/api/v1/sections/{sectionId}/items", {
      params: { path: { sectionId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async reorderItems(sectionId: string, orderedIds: string[]): Promise<void> {
    const { error } = await apiClient.PUT("/api/v1/sections/{sectionId}/items/order", {
      params: { path: { sectionId } },
      body: { orderedIds },
    });
    if (error) throw parseApiError(error);
  },

  async getLesson(itemId: string): Promise<LessonResponse> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/lesson", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async saveLesson(itemId: string, body: SaveLessonRequest): Promise<LessonResponse> {
    const { data, error } = await apiClient.PUT("/api/v1/items/{itemId}/lesson", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async saveQuiz(itemId: string, body: SaveQuizRequest): Promise<QuizResponse> {
    const { data, error } = await apiClient.PUT("/api/v1/items/{itemId}/quiz", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async getQuizQuestions(itemId: string): Promise<AuthorQuestionResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/quiz/questions", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async addQuizQuestion(itemId: string, body: AddQuestionRequest): Promise<AuthorQuestionResponse> {
    const { data, error } = await apiClient.POST("/api/v1/items/{itemId}/quiz/questions", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async getAssignment(itemId: string): Promise<AssignmentResponse> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/assignment", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async saveAssignment(itemId: string, body: SaveAssignmentRequest): Promise<AssignmentResponse> {
    const { data, error } = await apiClient.PUT("/api/v1/items/{itemId}/assignment", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async getInstructors(courseId: string): Promise<InstructorResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{courseId}/instructors", {
      params: { path: { courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async addInstructor(courseId: string, body: AddInstructorRequest): Promise<InstructorResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{courseId}/instructors", {
      params: { path: { courseId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async removeInstructor(courseId: string, instructorId: string): Promise<void> {
    const { error } = await apiClient.DELETE(
      "/api/v1/courses/{courseId}/instructors/{instructorId}",
      {
        params: { path: { courseId, instructorId } },
      },
    );
    if (error) throw parseApiError(error);
  },

  async getReviews(
    courseId: string,
    params?: { page?: number; size?: number },
  ): Promise<PageResponse<ReviewResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{courseId}/reviews", {
      params: {
        path: { courseId },
        ...(params && { query: params }),
      },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<ReviewResponse>;
  },

  async getReviewSummary(courseId: string): Promise<RatingSummaryResponse> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{courseId}/reviews/summary", {
      params: { path: { courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },
};
