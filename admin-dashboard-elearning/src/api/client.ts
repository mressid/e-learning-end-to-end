import createFetchClient, { type Middleware } from "openapi-fetch";
import type { paths, ApiError } from "./types";
import { STORAGE_KEY as I18N_STORAGE_KEY } from "@/lib/i18n";

export const API_BASE_URL =
  (typeof import.meta !== "undefined" && import.meta.env?.["VITE_API_BASE_URL"]) ||
  "http://localhost:8081";

export const ACCESS_TOKEN_KEY = "lernova_access_token";
export const REFRESH_TOKEN_KEY = "lernova_refresh_token";

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setTokens(tokens: { accessToken: string; refreshToken?: string | null }) {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    if (tokens.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    }
  } catch {
    // Ignore storage errors in restricted contexts
  }
}

export function clearTokens() {
  if (typeof window === "undefined") return;
  try {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
    // Ignore
  }
}

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      clearTokens();
      return null;
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) {
        clearTokens();
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("lernova:auth-expired"));
        }
        return null;
      }

      const data = await response.json();
      if (data?.accessToken) {
        setTokens({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
        });
        return data.accessToken as string;
      }

      clearTokens();
      return null;
    } catch {
      clearTokens();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

const authAndI18nMiddleware: Middleware = {
  async onRequest({ request }) {
    const token = getAccessToken();
    if (token) {
      request.headers.set("Authorization", `Bearer ${token}`);
    }

    let lang = "en";
    if (typeof window !== "undefined") {
      try {
        lang = localStorage.getItem(I18N_STORAGE_KEY) || "en";
      } catch {
        lang = "en";
      }
    }
    request.headers.set("Accept-Language", lang);

    return request;
  },

  async onResponse({ request, response }) {
    if (response.status === 401 && !request.url.includes("/api/v1/auth/")) {
      const newAccessToken = await refreshAccessToken();
      if (newAccessToken) {
        const retryRequest = new Request(request.url, {
          ...request,
          headers: new Headers(request.headers),
        });
        retryRequest.headers.set("Authorization", `Bearer ${newAccessToken}`);
        return fetch(retryRequest);
      }
    }
    return response;
  },
};

export const apiClient = createFetchClient<paths>({
  baseUrl: API_BASE_URL,
});

apiClient.use(authAndI18nMiddleware);

/**
 * Normalizes backend error objects or network exceptions into standard ApiError format
 */
export function parseApiError(error: unknown): ApiError {
  if (typeof error === "object" && error !== null) {
    const err = error as Record<string, unknown>;
    const message = err["message"];
    if (typeof message === "string") {
      const code = err["code"];
      const errors = err["errors"];
      const requestId = err["requestId"];

      const result: ApiError = {
        message,
      };
      if (typeof code === "string") {
        result.code = code;
      }
      if (Array.isArray(errors)) {
        result.errors = errors as NonNullable<ApiError["errors"]>;
      }
      if (typeof requestId === "string" || requestId === null) {
        result.requestId = requestId;
      }
      return result;
    }
  }
  return {
    code: "NETWORK_ERROR",
    message: error instanceof Error ? error.message : "An unexpected network error occurred.",
  };
}
