import { apiClient, parseApiError } from "../client";
import type {
  AuditEntryResponse,
  CategoryResponse,
  CreateCategoryRequest,
  PageResponse,
  RenameCategoryRequest,
  SessionResponse,
} from "../types";
import type { PageParams } from "./admin-directory.api";

export interface AuditListParams extends PageParams {
  action?: string;
  actorId?: string;
  targetType?: string;
  targetId?: string;
}

export interface SessionListParams extends PageParams {
  userId?: string;
}

export interface AdminSessionListParams extends PageParams {
  adminId?: string;
}

/**
 * Running the platform: who is signed in, what happened, and the taxonomy.
 *
 * The audit trail is read-only by design and has no write, edit or delete
 * endpoint — a log with a delete button is not evidence of anything. Do not add
 * one to the UI either.
 */
export const adminPlatformApi = {
  /** Requires `audit.read`. Newest first. */
  async listAudit(params?: AuditListParams): Promise<PageResponse<AuditEntryResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/audit", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<AuditEntryResponse>;
  },

  /** Requires `settings.manage`. Live learner sessions, most recently used first. */
  async listUserSessions(params?: SessionListParams): Promise<PageResponse<SessionResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/sessions", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<SessionResponse>;
  },

  /** Requires `settings.manage`. */
  async listAdminSessions(params?: AdminSessionListParams): Promise<PageResponse<SessionResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/sessions/admins", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<SessionResponse>;
  },

  /**
   * Ends one learner session. The access token already issued stays valid until
   * it expires — revoking stops the refresh, it does not reach back in time.
   */
  async revokeUserSession(sessionId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/admin/sessions/{sessionId}", {
      params: { path: { sessionId } },
    });
    if (error) throw parseApiError(error);
  },

  async revokeAdminSession(sessionId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/admin/sessions/admins/{sessionId}", {
      params: { path: { sessionId } },
    });
    if (error) throw parseApiError(error);
  },

  /** Signs a learner out everywhere at once. */
  async revokeAllUserSessions(userId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/admin/sessions/users/{userId}", {
      params: { path: { userId } },
    });
    if (error) throw parseApiError(error);
  },

  /** Requires `category.manage`. Omit `parentId` for a top-level category. */
  async createCategory(body: CreateCategoryRequest): Promise<CategoryResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/categories", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async renameCategory(categoryId: string, body: RenameCategoryRequest): Promise<CategoryResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/admin/categories/{categoryId}", {
      params: { path: { categoryId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Refused while the category still has children or courses. */
  async deleteCategory(categoryId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/admin/categories/{categoryId}", {
      params: { path: { categoryId } },
    });
    if (error) throw parseApiError(error);
  },
};
