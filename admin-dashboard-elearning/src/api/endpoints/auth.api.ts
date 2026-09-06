import { apiClient, parseApiError, setTokens, clearTokens } from "../client";
import type {
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  UserResponse,
  ProfileResponse,
  UpdateProfileRequest,
  PasswordResetRequest,
  VerifyEmailRequest,
} from "../types";

export const authApi = {
  async login(body: LoginRequest): Promise<TokenResponse> {
    const { data, error } = await apiClient.POST("/api/v1/auth/login", { body });
    if (error || !data) throw parseApiError(error);
    if (data.accessToken) {
      setTokens({
        accessToken: data.accessToken,
        ...(data.refreshToken ? { refreshToken: data.refreshToken } : {}),
      });
    }
    return data;
  },

  async register(body: RegisterRequest): Promise<UserResponse> {
    const { data, error } = await apiClient.POST("/api/v1/auth/register", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async logout(refreshToken: string): Promise<void> {
    try {
      await apiClient.POST("/api/v1/auth/logout", { body: { refreshToken } });
    } finally {
      clearTokens();
    }
  },

  async getMe(): Promise<UserResponse> {
    const { data, error } = await apiClient.GET("/api/v1/me");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async getProfile(): Promise<ProfileResponse> {
    const { data, error } = await apiClient.GET("/api/v1/me/profile");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async updateProfile(body: UpdateProfileRequest): Promise<ProfileResponse> {
    const { data, error } = await apiClient.PATCH("/api/v1/me/profile", { body });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async requestPasswordReset(email: string): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/auth/password-reset", {
      body: { email },
    });
    if (error) throw parseApiError(error);
  },

  async confirmPasswordReset(body: PasswordResetRequest): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/auth/password-reset/confirm", {
      body,
    });
    if (error) throw parseApiError(error);
  },

  async verifyEmail(body: VerifyEmailRequest): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/auth/verify-email", { body });
    if (error) throw parseApiError(error);
  },
};
