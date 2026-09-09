import { apiClient, parseApiError } from "../client";
import type {
  AttachResourceRequest,
  AttachedResourceResponse,
  CreateResourceRequest,
  ResourceResponse,
} from "../types";

/** The three things a resource can hang off. */
export type ResourceScope = "course" | "section" | "item";

/**
 * Documents, links and notes — and the three places they attach.
 *
 * A resource exists on its own: it has no course id, and the same cheat sheet
 * can hang off a course, one of its sections and one of its lessons at once.
 * That is why attaching is a separate call from creating, and why the scope is
 * a parameter here rather than three near-identical modules.
 *
 * `resourceType` and `sourceType` are deliberately independent — a PDF and a
 * link to a PDF are the same kind of thing held two different ways.
 */
export const resourcesApi = {
  async create(body: CreateResourceRequest): Promise<ResourceResponse> {
    const { data, error } = await apiClient.POST("/api/v1/resources", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async list(scope: ResourceScope, ownerId: string): Promise<AttachedResourceResponse[]> {
    if (scope === "course") {
      const { data, error } = await apiClient.GET("/api/v1/courses/{courseId}/resources", {
        params: { path: { courseId: ownerId } },
      });
      if (error || !data) throw parseApiError(error);
      return data;
    }
    if (scope === "section") {
      const { data, error } = await apiClient.GET("/api/v1/sections/{sectionId}/resources", {
        params: { path: { sectionId: ownerId } },
      });
      if (error || !data) throw parseApiError(error);
      return data;
    }
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/resources", {
      params: { path: { itemId: ownerId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async attach(
    scope: ResourceScope,
    ownerId: string,
    body: AttachResourceRequest,
  ): Promise<AttachedResourceResponse> {
    if (scope === "course") {
      const { data, error } = await apiClient.POST("/api/v1/courses/{courseId}/resources", {
        params: { path: { courseId: ownerId } },
        body,
      });
      if (error || !data) throw parseApiError(error);
      return data;
    }
    if (scope === "section") {
      const { data, error } = await apiClient.POST("/api/v1/sections/{sectionId}/resources", {
        params: { path: { sectionId: ownerId } },
        body,
      });
      if (error || !data) throw parseApiError(error);
      return data;
    }
    const { data, error } = await apiClient.POST("/api/v1/items/{itemId}/resources", {
      params: { path: { itemId: ownerId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Detaching, which the API only offers at course scope.
   *
   * Section and item attachments have no delete endpoint yet, so the UI does not
   * offer a button that would 405. Detaching leaves the resource itself alone —
   * it may still be attached somewhere else.
   */
  async detachFromCourse(courseId: string, resourceId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/courses/{courseId}/resources/{resourceId}", {
      params: { path: { courseId, resourceId } },
    });
    if (error) throw parseApiError(error);
  },

  /**
   * A short-lived URL for a FILE resource. Meaningless for URL and INLINE ones.
   *
   * The field is `downloadUrl`, not `url` — reading the wrong one yields
   * undefined rather than an error, and opens a blank tab.
   */
  async downloadUrl(resourceId: string): Promise<string> {
    const { data, error } = await apiClient.GET("/api/v1/resources/{resourceId}/download-url", {
      params: { path: { resourceId } },
    });
    if (error || !data) throw parseApiError(error);
    return data.downloadUrl ?? "";
  },
};
