import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationsApi, queryKeys, getAccessToken } from "@/api";

export function useNotificationsQuery(params?: { page?: number; size?: number }) {
  const hasToken = typeof window !== "undefined" && Boolean(getAccessToken());

  return useQuery({
    queryKey: queryKeys.notifications.list(params?.page, params?.size),
    queryFn: () => notificationsApi.getNotifications(params),
    enabled: hasToken,
    staleTime: 30 * 1000,
  });
}

export function useUnreadCountQuery() {
  const hasToken = typeof window !== "undefined" && Boolean(getAccessToken());

  return useQuery({
    queryKey: queryKeys.notifications.unreadCount,
    queryFn: () => notificationsApi.getUnreadCount(),
    enabled: hasToken,
    refetchInterval: 60 * 1000,
  });
}

export function useMarkNotificationReadMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notificationId: string) => notificationsApi.markRead(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    },
  });
}

export function useMarkAllNotificationsReadMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    },
  });
}
