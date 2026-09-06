import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  authApi,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  queryKeys,
  type LoginRequest,
  type RegisterRequest,
  type UpdateProfileRequest,
} from "@/api";

export function useMeQuery() {
  const hasToken = typeof window !== "undefined" && Boolean(getAccessToken());

  return useQuery({
    queryKey: queryKeys.auth.me,
    queryFn: () => authApi.getMe(),
    enabled: hasToken,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}

export function useProfileQuery() {
  const hasToken = typeof window !== "undefined" && Boolean(getAccessToken());

  return useQuery({
    queryKey: queryKeys.auth.profile,
    queryFn: () => authApi.getProfile(),
    enabled: hasToken,
    staleTime: 5 * 60 * 1000,
  });
}

export function useLoginMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (credentials: LoginRequest) => authApi.login(credentials),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.auth.me });
      queryClient.invalidateQueries({ queryKey: queryKeys.auth.profile });
    },
  });
}

export function useRegisterMutation() {
  return useMutation({
    mutationFn: (data: RegisterRequest) => authApi.register(data),
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        await authApi.logout(refreshToken);
      } else {
        clearTokens();
      }
    },
    onSettled: () => {
      clearTokens();
      queryClient.clear();
    },
  });
}

export function useUpdateProfileMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: UpdateProfileRequest) => authApi.updateProfile(body),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.auth.profile, updated);
      queryClient.invalidateQueries({ queryKey: queryKeys.auth.profile });
    },
  });
}
