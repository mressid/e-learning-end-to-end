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
  authApi,
  clearTokens,
  decodeToken,
  getAccessToken,
  getRefreshToken,
  isTokenLive,
  isUserToken,
  queryKeys,
  AUTH_EXPIRED_EVENT,
  SESSION_CHANGED_EVENT,
  type LoginRequest,
  type UserResponse,
  type UserTokenClaims,
} from "@/api";

export const LOGIN_PATH = "/login";

/**
 * Is there a session worth letting through the door?
 *
 * Deliberately not `isTokenLive` alone. The access token lasts fifteen minutes
 * and the refresh token far longer, so an expired access token is the normal
 * state of anyone who left a tab open over lunch — the API client swaps it for a
 * fresh one on the first 401. Treating that as signed out would throw such a
 * person back to the sign-in form every quarter hour. What actually ends a
 * session is having no refresh token left to spend.
 *
 * An administrator's token is never a session here whatever localStorage holds:
 * the backend refuses `typ=admin` on the platform routes, exactly as it refuses
 * `typ=user` on the dashboard's.
 *
 * Plain function, not a hook, because the router's `beforeLoad` guard runs
 * outside React and needs the same answer the provider gives.
 */
export function hasUserSession(): boolean {
  if (typeof window === "undefined") return false;
  const token = getAccessToken();
  if (!token || !isUserToken(token)) return false;
  return isTokenLive(token) || Boolean(getRefreshToken());
}

interface AuthContextValue {
  /** The raw access token, or null when there is no session. */
  token: string | null;
  /** Its decoded (unverified) claims. Nothing here is trusted. */
  claims: UserTokenClaims | null;
  /** The signed-in account from /me, once it has loaded. */
  user: UserResponse | null;
  /**
   * False on the server and during the first client render, when localStorage
   * has not been read yet. Callers that must not guess — a redirect, a guard —
   * wait for this rather than treating "unknown" as "no".
   */
  isReady: boolean;
  isAuthenticated: boolean;
  /** /me is in flight. Distinct from `isReady`, which is about the token. */
  isLoadingUser: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  isLoggingIn: boolean;
  isLoggingOut: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * One place that knows whether anyone is signed in, and who.
 *
 * The token is read once, in an effect, and shared — reading localStorage
 * during render would make the server's markup disagree with the browser's
 * first paint.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    const sync = () => {
      setToken(hasUserSession() ? getAccessToken() : null);
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
  // never sees it. Without this the user sits on a page whose every request is
  // quietly 401ing.
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
    queryKey: queryKeys.me,
    queryFn: () => authApi.me(),
    // Keyed off state rather than localStorage so the server and the first
    // client render agree, and so signing in starts the fetch on its own.
    enabled: isReady && Boolean(token),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const loginMutation = useMutation({
    mutationFn: (credentials: LoginRequest) => authApi.login(credentials),
    // The new token belongs to somebody else, so nothing cached under the
    // previous session is ours to reuse.
    onSuccess: () => queryClient.clear(),
  });

  const logoutMutation = useMutation({
    mutationFn: () => authApi.logout(),
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
    async (credentials: LoginRequest) => {
      await loginAsync(credentials);
      // `authApi.login` has stored the tokens and announced the change, but the
      // effect that listens has not run yet. Seed the state now so a caller
      // navigating on the next line lands on a signed-in tree.
      setToken(getAccessToken());
    },
    [loginAsync],
  );

  const logout = useCallback(async () => {
    // Never rejects: the local session is gone either way, and staying put
    // because the server was unreachable is the wrong way to fail.
    await logoutAsync().catch(() => undefined);
    setToken(null);
    navigate({ to: LOGIN_PATH });
  }, [logoutAsync, navigate]);

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      claims: decodeToken(token),
      user: meQuery.data ?? null,
      isReady,
      isAuthenticated: Boolean(token),
      isLoadingUser: meQuery.isLoading,
      login,
      logout,
      isLoggingIn: loginMutation.isPending,
      isLoggingOut: logoutMutation.isPending,
    }),
    [
      token,
      isReady,
      meQuery.data,
      meQuery.isLoading,
      login,
      logout,
      loginMutation.isPending,
      logoutMutation.isPending,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>");
  return context;
}
