import { apiClient, parseApiError } from "../client";
import type { AssignmentResponse } from "../types";

/**
 * An assignment's brief.
 *
 * Read only, for now. Authoring an assignment has a full set of endpoints
 * behind it and no screen, and this exists because the course preview cannot
 * quietly skip an item the course actually contains.
 */
export const assignmentApi = {
  /** 404 until a brief has been written, the same as a lesson or a quiz. */
  async assignment(itemId: string): Promise<AssignmentResponse> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/assignment", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },
};
