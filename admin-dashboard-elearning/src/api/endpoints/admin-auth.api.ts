import { apiClient, parseApiError, setTokens, clearTokens } from "../client";
import type {
  AdminLoginRequest,
  AdminTokenResponse,
  AdminResponse,
  ChangePasswordRequest,
} from "../types";

/**
 * Dashboard sign-in.
 *
 * Administrators live in their own table with their own sign-in surface. This is
 * not a naming preference: tokens carry a `typ` claim, so a learner token is
 * refused on every `/admin/**` route (403) and an admin token is refused on
 * `/api/v1/me` (401). There is no single token that works on both.
 */
export const adminAuthApi = {
  async login(body: AdminLoginRequest): Promise<AdminTokenResponse> {
    const { data, error } = await apiClient.POST("/api/v1/admin/auth/login", { body });
    if (error || !data) throw parseApiError(error);
    if (data.accessToken) {
      setTokens({
        accessToken: data.accessToken,
        ...(data.refreshToken ? { refreshToken: data.refreshToken } : {}),
      });
    }
    return data;
  },

  async logout(refreshToken: string): Promise<void> {
    try {
      await apiClient.POST("/api/v1/admin/auth/logout", { body: { refreshToken } });
    } finally {
      // Even a failed logout ends the session locally. Leaving the token behind
      // because the server was unreachable is the wrong way to fail.
      clearTokens();
    }
  },

  /** The signed-in administrator, with their roles. */
  async getMe(): Promise<AdminResponse> {
    const { data, error } = await apiClient.GET("/api/v1/admin/auth/me");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Changing your own password ends every session, this one included, so the
   * caller must send the user back to sign-in afterwards.
   */
  async changePassword(body: ChangePasswordRequest): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/admin/auth/me/password", { body });
    if (error) throw parseApiError(error);
    clearTokens();
  },
};
