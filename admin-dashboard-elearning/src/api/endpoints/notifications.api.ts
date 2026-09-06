import { apiClient, parseApiError } from "../client";
import type { NotificationResponse, PageResponse, UnreadCountResponse } from "../types";

export const notificationsApi = {
  async getNotifications(params?: {
    page?: number;
    size?: number;
  }): Promise<PageResponse<NotificationResponse>> {
    const { data, error } = await apiClient.GET("/api/v1/me/notifications", {
      params: params ? { query: params } : {},
    });
    if (error || !data) throw parseApiError(error);
    return data as PageResponse<NotificationResponse>;
  },

  async getUnreadCount(): Promise<UnreadCountResponse> {
    const { data, error } = await apiClient.GET("/api/v1/me/notifications/unread-count");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async markRead(notificationId: string): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/notifications/{notificationId}/read", {
      params: { path: { notificationId } },
    });
    if (error) throw parseApiError(error);
  },

  async markAllRead(): Promise<void> {
    const { error } = await apiClient.POST("/api/v1/me/notifications/read-all");
    if (error) throw parseApiError(error);
  },
};
