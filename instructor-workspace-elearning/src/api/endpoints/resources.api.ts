import { apiClient, parseApiError } from "../client";
import type {
  AttachResourceRequest,
  AttachedResourceResponse,
  CreateResourceRequest,
  ResourceResponse,
  UpdateResourceRequest,
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
   * Editing a resource in place.
   *
   * A partial update: a field left out is left alone, which is the opposite of
   * the lesson endpoint where a missing field is a cleared one. Neither the
   * `sourceType` nor the bytes of a file can change here — both of those are a
   * different resource rather than an edit of this one.
   *
   * Refused with `RESOURCE_SHARED` when the material is also used by a course
   * you cannot edit. It is not an error to fix by retrying: the way to diverge
   * from something somebody else is teaching with is to take a copy.
   */
  async update(resourceId: string, body: UpdateResourceRequest): Promise<ResourceResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/resources/{resourceId}", {
      params: { path: { resourceId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Detaching, at whichever scope. Leaves the resource itself alone — it may
   * still be attached somewhere else, and it is a library material either way.
   */
  async detach(scope: ResourceScope, ownerId: string, resourceId: string): Promise<void> {
    if (scope === "course") {
      const { error } = await apiClient.DELETE(
        "/api/v1/courses/{courseId}/resources/{resourceId}",
        {
          params: { path: { courseId: ownerId, resourceId } },
        },
      );
      if (error) throw parseApiError(error);
      return;
    }
    if (scope === "section") {
      const { error } = await apiClient.DELETE(
        "/api/v1/sections/{sectionId}/resources/{resourceId}",
        { params: { path: { sectionId: ownerId, resourceId } } },
      );
      if (error) throw parseApiError(error);
      return;
    }
    const { error } = await apiClient.DELETE("/api/v1/items/{itemId}/resources/{resourceId}", {
      params: { path: { itemId: ownerId, resourceId } },
    });
    if (error) throw parseApiError(error);
  },

  async detachFromCourse(courseId: string, resourceId: string): Promise<void> {
    return resourcesApi.detach("course", courseId, resourceId);
  },

  /**
   * Sets the order of an item's blocks.
   *
   * The whole sequence goes, not one move. The endpoint rejects a partial
   * order, and `SortableList` already hands over the complete one, so nothing
   * in between has to translate a drag into a request.
   */
  async reorderItemResources(itemId: string, resourceIds: string[]): Promise<void> {
    const { error } = await apiClient.PUT("/api/v1/items/{itemId}/resources/order", {
      params: { path: { itemId } },
      body: { resourceIds },
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
