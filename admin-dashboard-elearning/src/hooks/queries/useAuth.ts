/**
 * Session hooks live with the provider that owns them, in `@/lib/auth`. They
 * are re-exported here so the rest of the app can keep reaching for its data
 * hooks in one place.
 */
export {
  AuthProvider,
  useAuth,
  usePermissions,
  useAdminMeQuery,
  useChangePasswordMutation,
  hasAdminSession,
  LOGIN_PATH,
} from "@/lib/auth";
