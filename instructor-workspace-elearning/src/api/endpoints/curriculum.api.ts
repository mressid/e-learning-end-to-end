import { apiClient, parseApiError } from "../client";
import type {
  CourseItemResponse,
  CreateCourseItemRequest,
  CreateSectionRequest,
  LessonResponse,
  SaveLessonRequest,
  SectionResponse,
  UpdateCourseItemRequest,
  UpdateSectionRequest,
} from "../types";

/**
 * The shape of a course: sections, the items inside them, and what a lesson
 * item actually contains.
 *
 * Sections are addressed under the course only to create and reorder them —
 * everything else hangs off `/sections/{id}` and `/items/{id}` directly, so the
 * same item is never reachable by two paths.
 */
export const curriculumApi = {
  async sections(courseId: string): Promise<SectionResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{id}/sections", {
      params: { path: { id: courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Appended to the end. Moving it is a separate, whole-sequence operation. */
  async addSection(courseId: string, body: CreateSectionRequest): Promise<SectionResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/sections", {
      params: { path: { id: courseId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async updateSection(sectionId: string, body: UpdateSectionRequest): Promise<SectionResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/sections/{sectionId}", {
      params: { path: { sectionId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Refused with 422 if any item under it has student work behind it. */
  async deleteSection(sectionId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/sections/{sectionId}", {
      params: { path: { sectionId } },
    });
    if (error) throw parseApiError(error);
  },

  /** Send every id, in the order you want. Partial orders are rejected. */
  async reorderSections(courseId: string, orderedIds: string[]): Promise<SectionResponse[]> {
    const { data, error } = await apiClient.PUT("/api/v1/courses/{id}/sections/order", {
      params: { path: { id: courseId } },
      body: { orderedIds },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async items(sectionId: string): Promise<CourseItemResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/sections/{sectionId}/items", {
      params: { path: { sectionId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** One item by its own id — what a page addressed by that id has to work from. */
  async item(itemId: string): Promise<CourseItemResponse> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async addItem(sectionId: string, body: CreateCourseItemRequest): Promise<CourseItemResponse> {
    const { data, error } = await apiClient.POST("/api/v1/sections/{sectionId}/items", {
      params: { path: { sectionId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async updateItem(itemId: string, body: UpdateCourseItemRequest): Promise<CourseItemResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/items/{itemId}", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Refused with 422 once a student has worked on it. */
  async deleteItem(itemId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/items/{itemId}", {
      params: { path: { itemId } },
    });
    if (error) throw parseApiError(error);
  },

  async reorderItems(sectionId: string, orderedIds: string[]): Promise<CourseItemResponse[]> {
    const { data, error } = await apiClient.PUT("/api/v1/sections/{sectionId}/items/order", {
      params: { path: { sectionId } },
      body: { orderedIds },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * A lesson item's content.
   *
   * 404 until one has been saved — a LESSON item exists as a slot in the
   * sequence before it has anything in it, which is the normal state of a course
   * being written.
   */
  async lesson(itemId: string): Promise<LessonResponse> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/lesson", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Creates the lesson or replaces it; there is no partial update. */
  async saveLesson(itemId: string, body: SaveLessonRequest): Promise<LessonResponse> {
    const { data, error } = await apiClient.PUT("/api/v1/items/{itemId}/lesson", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },
};
