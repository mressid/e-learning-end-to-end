import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";
import {
  adminAuthApi,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  permissionsOf,
  queryKeys,
  AUTH_EXPIRED_EVENT,
  type AdminLoginRequest,
  type ChangePasswordRequest,
  type Permission,
} from "@/api";

/** The signed-in administrator, with their roles. */
export function useAdminMeQuery() {
  const hasToken = typeof window !== "undefined" && Boolean(getAccessToken());

  return useQuery({
    queryKey: queryKeys.auth.me,
    queryFn: () => adminAuthApi.getMe(),
    enabled: hasToken,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}

export function useLoginMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (credentials: AdminLoginRequest) => adminAuthApi.login(credentials),
    onSuccess: () => {
      // The new token carries a different administrator's permissions, so
      // anything cached under the previous session is not ours to reuse.
      queryClient.clear();
    },
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        await adminAuthApi.logout(refreshToken);
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

/**
 * Changing your own password revokes every session including this one, so the
 * cache is dropped and the caller must navigate to sign-in.
 */
export function useChangePasswordMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: ChangePasswordRequest) => adminAuthApi.changePassword(body),
    onSettled: () => queryClient.clear(),
  });
}

/**
 * What this session may do, read from the access token's `scope` claim.
 *
 * Re-read whenever the token changes — a refresh reissues it with permissions as
 * they stand now, which is how a role change reaches a signed-in administrator.
 * Purely for deciding what to render; the backend still enforces every call.
 */
export function usePermissions() {
  // Starts null on purpose, on the server *and* on the first client render.
  // The token lives in localStorage, which the server cannot see: reading it
  // during render would make the markup disagree with the server's and trip a
  // hydration mismatch, and callers would flash "you may not do this" before
  // correcting themselves. `isReady` says which of those two states this is —
  // "no permissions" or "not known yet" — so callers can wait rather than
  // guess.
  const [token, setToken] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") return;

    const sync = () => {
      setToken(getAccessToken());
      setIsReady(true);
    };
    sync();
    // `storage` covers other tabs; the auth-expired event covers this one.
    window.addEventListener("storage", sync);
    window.addEventListener(AUTH_EXPIRED_EVENT, sync);
    // A refresh mid-session rewrites the token without firing either, so catch
    // up on focus as well.
    window.addEventListener("focus", sync);
    return () => {
      window.removeEventListener("storage", sync);
      window.removeEventListener(AUTH_EXPIRED_EVENT, sync);
      window.removeEventListener("focus", sync);
    };
  }, []);

  const granted = permissionsOf(token);

  const has = useCallback(
    (permission: Permission) => granted.has(permission),
    // `granted` is rebuilt each render from `token`; keying on the token keeps
    // the identity stable for consumers that depend on it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [token],
  );

  const hasAny = useCallback(
    (...permissions: Permission[]) => permissions.some((p) => granted.has(p)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [token],
  );

  return { permissions: granted, has, hasAny, isReady, isAuthenticated: Boolean(token) };
}
