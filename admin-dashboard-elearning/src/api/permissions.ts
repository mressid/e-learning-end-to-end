/**
 * What the signed-in administrator may do.
 *
 * The authority is the access token's `scope` claim — the same claim the backend
 * reads to allow or refuse a request. Reading it here means the UI and the API
 * can never disagree about what this session holds, and a permission granted or
 * withdrawn takes effect on the next token refresh without a separate fetch.
 *
 * A super admin's scope is expanded to every code server-side, so there is no
 * "is super" special case to get wrong on this side.
 *
 * None of this is a security boundary. It decides what to *show*; the backend
 * decides what to *allow*, and still returns 403 if these two ever drift.
 */

/** The permission catalogue, mirroring the seeded set the backend enforces. */
export const PERMISSIONS = {
  COURSE_READ: "course.read",
  COURSE_WRITE: "course.write",
  COURSE_PUBLISH: "course.publish",
  COURSE_DELETE: "course.delete",
  CATEGORY_MANAGE: "category.manage",
  USER_READ: "user.read",
  USER_WRITE: "user.write",
  USER_SUSPEND: "user.suspend",
  CERTIFICATE_READ: "certificate.read",
  CERTIFICATE_REVOKE: "certificate.revoke",
  SUBMISSION_READ: "submission.read",
  REVIEW_MODERATE: "review.moderate",
  DISCUSSION_MODERATE: "discussion.moderate",
  MEDIA_READ: "media.read",
  MEDIA_DELETE: "media.delete",
  SETTINGS_MANAGE: "settings.manage",
  AUDIT_READ: "audit.read",
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

export interface AdminTokenClaims {
  sub: string;
  username?: string;
  /** `user` or `admin`. The backend refuses a token of the wrong kind. */
  typ?: string;
  /** Space-separated permission codes. */
  scope?: string;
  exp?: number;
}

/**
 * Reads a JWT payload without verifying it.
 *
 * Verification would need the signing key, which does not belong in a browser.
 * That is fine: nothing here is trusted. A forged token buys a prettier menu and
 * a 403 on every request behind it.
 */
export function decodeToken(token: string | null): AdminTokenClaims | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    // atob yields Latin-1; re-decode as UTF-8 so non-ASCII usernames survive.
    const text = new TextDecoder().decode(Uint8Array.from(json, (c) => c.charCodeAt(0)));
    return JSON.parse(text) as AdminTokenClaims;
  } catch {
    return null;
  }
}

/** The permissions carried by a token, or an empty set if it carries none. */
export function permissionsOf(token: string | null): ReadonlySet<string> {
  const scope = decodeToken(token)?.scope;
  if (!scope) return new Set();
  return new Set(scope.split(" ").filter(Boolean));
}

/** True while the token is present and not past its `exp`. */
export function isTokenLive(token: string | null): boolean {
  const claims = decodeToken(token);
  if (!claims?.exp) return false;
  return claims.exp * 1000 > Date.now();
}

/** True if this is an administrator's token rather than a learner's. */
export function isAdminToken(token: string | null): boolean {
  return decodeToken(token)?.typ === "admin";
}
