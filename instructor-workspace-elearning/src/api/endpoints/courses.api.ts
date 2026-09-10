import { apiClient, parseApiError } from "../client";
import type {
  CourseResponse,
  CreateCourseRequest,
  PageResponse,
  SectionResponse,
  UpdateCourseRequest,
} from "../types";

export interface MineParams {
  page?: number;
  size?: number;
}

/**
 * The courses this instructor is responsible for.
 *
 * `/courses/mine` rather than `/courses`: the public listing is published-only,
 * so an author's own unfinished work — the reason they opened the app — would
 * be missing from it entirely.
 */
export const coursesApi = {
  async mine(params?: MineParams): Promise<PageResponse<CourseResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/courses/mine", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<CourseResponse>;
  },

  async get(courseId: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{id}", {
      params: { path: { id: courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** The caller becomes the owner. Starts as a draft. */
  async create(body: CreateCourseRequest): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async update(courseId: string, body: UpdateCourseRequest): Promise<CourseResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/courses/{id}", {
      params: { path: { id: courseId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Points the course at an already-uploaded image.
   *
   * The media has to have been uploaded with `PUBLIC` visibility. A thumbnail
   * is shown in catalogue listings to people who have not enrolled and may not
   * even be signed in, so it needs a stable URL rather than one that expires —
   * which is the one case on this platform where the public bucket is the
   * right answer rather than a shortcut around the enrolment check.
   */
  async setThumbnail(courseId: string, mediaId: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/thumbnail", {
      params: { path: { id: courseId } },
      body: { mediaId },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Refused unless the course has at least one item to teach. */
  async publish(courseId: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/publish", {
      params: { path: { id: courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async unpublish(courseId: string): Promise<CourseResponse> {
    const { data, error } = await apiClient.POST("/api/v1/courses/{id}/unpublish", {
      params: { path: { id: courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async sections(courseId: string): Promise<SectionResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/courses/{id}/sections", {
      params: { path: { id: courseId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },
};
