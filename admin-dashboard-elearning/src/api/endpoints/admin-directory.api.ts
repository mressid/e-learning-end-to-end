import { apiClient, parseApiError } from "../client";
import type {
  CreateUserRequest,
  DirectoryUserResponse,
  InstructorRosterEntry,
  PageResponse,
  SetUserStatusRequest,
  SetUserPasswordRequest,
  UpdateUserRequest,
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

export interface InstructorListParams extends PageParams {
  q?: string;
}

/**
 * Learners and instructors, as an administrator sees them.
 *
 * They are two kinds of account, not one account with a flag: `listUsers` never
 * returns an instructor and `listInstructors` never returns a learner. Which
 * kind an account is, is chosen by `createUser` and cannot be changed — there is
 * no promote or demote, here or on the server.
 *
 * Being an instructor is what allows authoring. It is *not* authority over any
 * particular course — that is still ownership or co-instructorship — so it only
 * answers "may this person start one", which no relationship can express.
 *
 * Creating and editing goes through `/admin/users`, because the row belongs to
 * identity; the roster is served from `/admin/instructors`, because the course
 * counts on it belong to courses.
 */
export const adminDirectoryApi = {
  /** Requires `user.read`. Learners only — instructors are their own roster. */
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

  /** Requires `user.write`. `type` decides learner or instructor and is
   *  permanent. The account is ACTIVE at once — there is no confirmation email,
   *  so hand the starting password over deliberately. */
  async createUser(body: CreateUserRequest): Promise<DirectoryUserResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/users", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Requires `user.write`. Only the fields sent change. The kind of account is
   *  not among them: it is fixed when the account is created. */
  async updateUser(userId: string, body: UpdateUserRequest): Promise<DirectoryUserResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/admin/users/{userId}", {
      params: { path: { userId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Requires `user.write`. For when reset-by-email cannot work — an instructor
   * whose address was never real, or a learner who no longer has the inbox.
   * Their sessions end, so a password that reached the wrong person stops
   * working the moment it is replaced.
   */
  async setUserPassword(
    userId: string,
    body: SetUserPasswordRequest,
  ): Promise<DirectoryUserResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/users/{userId}/password", {
      params: { path: { userId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Requires `user.read`. Includes instructors with no courses yet. */
  async listInstructors(
    params?: InstructorListParams,
  ): Promise<PageResponse<InstructorRosterEntry>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/instructors", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<InstructorRosterEntry>;
  },
};
