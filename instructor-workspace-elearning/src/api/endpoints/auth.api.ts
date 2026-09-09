import { apiClient, parseApiError, setTokens, clearTokens, getRefreshToken } from "../client";
import type { LoginRequest, TokenResponse, UserResponse } from "../types";

/**
 * Signing in to the platform, not the dashboard.
 *
 * `/api/v1/auth/**` issues a `typ=user` token; the administrator surface at
 * `/api/v1/admin/auth/**` issues `typ=admin`, and the backend refuses each on
 * the other's routes. An instructor account exists only on this side — which is
 * why signing in to the admin dashboard with one fails, and looks like a wrong
 * password because the address is not in that table at all.
 */
export const authApi = {
  async login(body: LoginRequest): Promise<TokenResponse> {
    const { data, error } = await apiClient.POST("/api/v1/auth/login", { body });
    if (error || !data) throw parseApiError(error);
    setTokens({ accessToken: data.accessToken!, refreshToken: data.refreshToken ?? null });
    return data;
  },

  /**
   * Ends the session server-side, then locally whatever the server said.
   *
   * Staying signed in because the backend was unreachable would be the wrong
   * way to fail: the person asked to leave.
   */
  async logout(): Promise<void> {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        await apiClient.POST("/api/v1/auth/logout", { body: { refreshToken } });
      } catch {
        // Deliberately swallowed; the local session goes either way.
      }
    }
    clearTokens();
  },

  /** The signed-in account. */
  async me(): Promise<UserResponse> {
    const { data, error } = await apiClient.GET("/api/v1/me", {});
    if (error || !data) throw parseApiError(error);
    return data;
  },
};
