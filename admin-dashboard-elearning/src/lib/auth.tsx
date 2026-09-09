import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import {
  adminAuthApi,
  clearTokens,
  decodeToken,
  getAccessToken,
  getRefreshToken,
  isAdminToken,
  isTokenLive,
  permissionsOf,
  queryKeys,
  AUTH_EXPIRED_EVENT,
  SESSION_CHANGED_EVENT,
  type AdminLoginRequest,
  type AdminResponse,
  type AdminTokenClaims,
  type ChangePasswordRequest,
  type Permission,
} from "@/api";

export const LOGIN_PATH = "/login";

/**
 * Is there a session worth letting through the door?
 *
 * Deliberately not `isTokenLive` alone. The access token lasts fifteen minutes
 * and the refresh token thirty days, so an expired access token is the normal
 * state of anyone who left a tab open over lunch — the API client swaps it for
 * a fresh one on the first 401. Treating that as signed out would throw such a
 * user back to the sign-in form every quarter hour. What actually ends a
 * session is having no refresh token left to spend.
 *
 * A learner token is never a session here whatever localStorage holds: the
 * backend refuses `typ=user` on every /admin route.
 *
 * Plain function, not a hook, because the router's `beforeLoad` guard runs
 * outside React and needs the same answer the provider gives.
 */
export function hasAdminSession(): boolean {
  if (typeof window === "undefined") return false;
  const token = getAccessToken();
  if (!token || !isAdminToken(token)) return false;
  return isTokenLive(token) || Boolean(getRefreshToken());
}

interface AuthContextValue {
  /** The raw access token, or null when there is no session. */
  token: string | null;
  /** Its decoded (unverified) claims. Nothing here is trusted. */
  claims: AdminTokenClaims | null;
  /** The signed-in administrator from /me, once it has loaded. */
  admin: AdminResponse | null;
  /**
   * False on the server and during the first client render, when localStorage
   * has not been read yet. Callers that must not guess — a permission gate, a
   * redirect — wait for this rather than treating "unknown" as "no".
   */
  isReady: boolean;
  isAuthenticated: boolean;
  /** /me is in flight. Distinct from `isReady`, which is about the token. */
  isLoadingAdmin: boolean;
  permissions: ReadonlySet<string>;
  has: (permission: Permission) => boolean;
  hasAny: (...permissions: Permission[]) => boolean;
  login: (credentials: AdminLoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  isLoggingIn: boolean;
  isLoggingOut: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * One place that knows whether anyone is signed in, and who.
 *
 * Before this, every consumer of `usePermissions` kept its own copy of the
 * token and its own three window listeners, and each caller of `useAdminMeQuery`
 * decided independently whether to run. Reading localStorage during render also
 * made the server's markup disagree with the browser's first paint. Here the
 * token is read once, in an effect, and shared.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    const sync = () => {
      setToken(hasAdminSession() ? getAccessToken() : null);
      setIsReady(true);
    };
    sync();

    // `storage` covers the other tabs; SESSION_CHANGED covers this one, which
    // is where sign-in, sign-out and every token rotation actually happen.
    // `focus` is the backstop for anything that wrote tokens without saying so.
    window.addEventListener("storage", sync);
    window.addEventListener(SESSION_CHANGED_EVENT, sync);
    window.addEventListener(AUTH_EXPIRED_EVENT, sync);
    window.addEventListener("focus", sync);
    return () => {
      window.removeEventListener("storage", sync);
      window.removeEventListener(SESSION_CHANGED_EVENT, sync);
      window.removeEventListener(AUTH_EXPIRED_EVENT, sync);
      window.removeEventListener("focus", sync);
    };
  }, []);

  // A refresh can fail long after the last navigation, so the router guard
  // never sees it. Without this the user sits on a dashboard whose every
  // request is quietly 401ing.
  useEffect(() => {
    const onExpired = () => {
      if (window.location.pathname === LOGIN_PATH) return;
      // Path and query only: an absolute URL is refused by the sign-in page's
      // open-redirect guard, which would quietly lose where the user was.
      const from = `${window.location.pathname}${window.location.search}`;
      navigate({ to: LOGIN_PATH, search: { redirect: from } });
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
  }, [navigate]);

  const meQuery = useQuery({
    queryKey: queryKeys.auth.me,
    queryFn: () => adminAuthApi.getMe(),
    // Keyed off state rather than localStorage so the server and the first
    // client render agree, and so signing in starts the fetch on its own.
    enabled: isReady && Boolean(token),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const loginMutation = useMutation({
    mutationFn: (credentials: AdminLoginRequest) => adminAuthApi.login(credentials),
    onSuccess: () => {
      // The new token carries a different administrator's permissions, so
      // nothing cached under the previous session is ours to reuse.
      queryClient.clear();
    },
  });

  const logoutMutation = useMutation({
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

  // `mutateAsync` is the stable half of what useMutation returns; the result
  // object itself is new every render, and depending on it would make the
  // context value churn on every render of this provider.
  const { mutateAsync: loginAsync } = loginMutation;
  const { mutateAsync: logoutAsync } = logoutMutation;

  const login = useCallback(
    async (credentials: AdminLoginRequest) => {
      await loginAsync(credentials);
      // `adminAuthApi.login` has stored the tokens and announced the change,
      // but the effect that listens has not run yet. Seed the state now so a
      // caller navigating on the next line lands on a signed-in tree.
      setToken(getAccessToken());
    },
    [loginAsync],
  );

  const logout = useCallback(async () => {
    // Never rejects: the local session is gone either way, and staying on the
    // dashboard because the server was unreachable is the wrong way to fail.
    await logoutAsync().catch(() => undefined);
    setToken(null);
    navigate({ to: LOGIN_PATH });
  }, [logoutAsync, navigate]);

  const value = useMemo<AuthContextValue>(() => {
    const granted = permissionsOf(token);
    return {
      token,
      claims: decodeToken(token),
      admin: meQuery.data ?? null,
      isReady,
      isAuthenticated: Boolean(token),
      isLoadingAdmin: meQuery.isLoading,
      permissions: granted,
      has: (permission: Permission) => granted.has(permission),
      hasAny: (...permissions: Permission[]) => permissions.some((p) => granted.has(p)),
      login,
      logout,
      isLoggingIn: loginMutation.isPending,
      isLoggingOut: logoutMutation.isPending,
    };
  }, [
    token,
    isReady,
    meQuery.data,
    meQuery.isLoading,
    login,
    logout,
    loginMutation.isPending,
    logoutMutation.isPending,
  ]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

/**
 * What this session may do, read from the access token's `scope` claim.
 *
 * The same claim the backend reads, so the UI and the API cannot disagree, and
 * a permission granted or withdrawn takes effect on the next token refresh
 * without a separate fetch. Purely for deciding what to render; the backend
 * still enforces every call.
 */
export function usePermissions() {
  const { permissions, has, hasAny, isReady, isAuthenticated } = useAuth();
  return { permissions, has, hasAny, isReady, isAuthenticated };
}

/** The signed-in administrator, with their roles. */
export function useAdminMeQuery() {
  const { admin, isLoadingAdmin } = useAuth();
  return { data: admin ?? undefined, isLoading: isLoadingAdmin };
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
