/**
 * Reading the access token, without trusting it.
 *
 * Verification needs the signing key, which does not belong in a browser. That
 * is fine — nothing decoded here is trusted for anything. A forged token buys a
 * name in the corner of the screen and a 401 on every request behind it.
 */
export interface UserTokenClaims {
  sub: string;
  username?: string;
  /** `user` here, `admin` on the dashboard. The backend refuses the wrong kind. */
  typ?: string;
  exp?: number;
}

export function decodeToken(token: string | null): UserTokenClaims | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    // atob yields Latin-1; re-decode as UTF-8 so non-ASCII usernames survive.
    const text = new TextDecoder().decode(Uint8Array.from(json, (c) => c.charCodeAt(0)));
    return JSON.parse(text) as UserTokenClaims;
  } catch {
    return null;
  }
}

/** True while the token is present and not past its `exp`. */
export function isTokenLive(token: string | null): boolean {
  const claims = decodeToken(token);
  if (!claims?.exp) return false;
  return claims.exp * 1000 > Date.now();
}

/**
 * True if this is a platform token rather than an administrator's.
 *
 * The mirror of the dashboard's `isAdminToken`. Neither app can do anything
 * with the other's token, so recognising the wrong kind early is how the user
 * gets told to sign in rather than watching every request 401.
 */
export function isUserToken(token: string | null): boolean {
  return decodeToken(token)?.typ === "user";
}
