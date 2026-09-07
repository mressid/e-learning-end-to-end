import { apiClient, parseApiError } from "../client";
import type {
  DirectoryUserResponse,
  InstructorRosterEntry,
  PageResponse,
  SetUserStatusRequest,
} from "../types";

export interface UserListParams {
  q?: string;
  status?: string;
  page?: number;
  size?: number;
}

export interface PageParams {
  page?: number;
  size?: number;
}

/**
 * Learners and instructors, as an administrator sees them.
 *
 * "Instructor" is not a role to enumerate — the backend derives it from owning
 * at least one course, so the roster is ordered busiest first and has no
 * membership to edit.
 */
export const adminDirectoryApi = {
  /** Requires `user.read`. */
  async listUsers(params?: UserListParams): Promise<PageResponse<DirectoryUserResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/users", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<DirectoryUserResponse>;
  },

  /** Requires `user.read`. */
  async getUser(userId: string): Promise<DirectoryUserResponse> {
    const { data, error } = await apiClient.GET("/api/v1/admin/users/{userId}", {
      params: { path: { userId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Requires `user.suspend`. Suspending does not sign the learner out on its
   *  own — end their sessions too if that is the intent. */
  async setUserStatus(userId: string, body: SetUserStatusRequest): Promise<DirectoryUserResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/users/{userId}/status", {
      params: { path: { userId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Requires `user.read`. */
  async listInstructors(params?: PageParams): Promise<PageResponse<InstructorRosterEntry>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/instructors", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<InstructorRosterEntry>;
  },
};
