import { apiClient, parseApiError } from "../client";
import type {
  AdminResponse,
  CreateAdminRequest,
  CreateRoleRequest,
  PageResponse,
  PermissionResponse,
  RoleResponse,
  SetAdminStatusRequest,
  SetRolePermissionsRequest,
} from "../types";
import type { PageParams } from "./admin-directory.api";

/**
 * Who may do what.
 *
 * Every call here is **super-admin only** — the backend answers
 * `403 {"code": "SUPER_ADMIN_ONLY"}` otherwise. That is not a permission the
 * super admin can grant away: the ability to hand out power is deliberately not
 * itself grantable, so no role can be configured into becoming one.
 */
export const adminAccessApi = {
  /** The full catalogue of permission codes, with descriptions. */
  async listPermissions(): Promise<PermissionResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/admin/permissions");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async listRoles(): Promise<RoleResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/admin/roles");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async createRole(body: CreateRoleRequest): Promise<RoleResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/roles", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Replaces the role's permissions wholesale; an empty list clears them. */
  async setRolePermissions(roleId: string, body: SetRolePermissionsRequest): Promise<RoleResponse> {
    const { data, error } = await apiClient.PUT("/api/v1/admin/roles/{roleId}/permissions", {
      params: { path: { roleId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** System roles cannot be deleted, and neither can one still in use. */
  async deleteRole(roleId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/admin/roles/{roleId}", {
      params: { path: { roleId } },
    });
    if (error) throw parseApiError(error);
  },

  async listAdmins(params?: PageParams): Promise<PageResponse<AdminResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/admin/admins", {
      params: { query: params ?? {} },
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<AdminResponse>;
  },

  async getAdmin(adminId: string): Promise<AdminResponse> {
    const { data, error } = await apiClient.GET("/api/v1/admin/admins/{adminId}", {
      params: { path: { adminId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async createAdmin(body: CreateAdminRequest): Promise<AdminResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/admins", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async setAdminStatus(adminId: string, body: SetAdminStatusRequest): Promise<AdminResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/admins/{adminId}/status", {
      params: { path: { adminId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Roles take effect on the administrator's next token refresh, not instantly —
   * their current access token already carries the old set.
   */
  async grantRole(adminId: string, roleId: string): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/admin/admins/{adminId}/roles/{roleId}", {
      params: { path: { adminId, roleId } },
    });
    if (error) throw parseApiError(error);
  },

  async revokeRole(adminId: string, roleId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/admin/admins/{adminId}/roles/{roleId}", {
      params: { path: { adminId, roleId } },
    });
    if (error) throw parseApiError(error);
  },
};
