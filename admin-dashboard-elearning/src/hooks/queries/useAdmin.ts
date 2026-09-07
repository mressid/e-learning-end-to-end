import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  adminAccessApi,
  adminCatalogApi,
  adminDirectoryApi,
  adminPlatformApi,
  certificatesApi,
  queryKeys,
  type AdminCourseListParams,
  type AdminSessionListParams,
  type AuditListParams,
  type CertificateListParams,
  type MediaListParams,
  type PageParams,
  type SessionListParams,
  type SubmissionListParams,
  type UserListParams,
  type SetUserStatusRequest,
  type SetAdminStatusRequest,
} from "@/api";

/**
 * The three account states the backend accepts. Taken from the request schema
 * rather than retyped, so a fourth added later is a compile error here instead
 * of a 400 at runtime.
 */
export type AccountStatus = NonNullable<SetUserStatusRequest["status"]>;
export type AdminAccountStatus = NonNullable<SetAdminStatusRequest["status"]>;

/*
 * Hooks over the administrative API.
 *
 * Every list here is paged and server-filtered — none of them fetch everything
 * and narrow it in the browser, because "everything" is unbounded for users,
 * audit entries and media alike.
 *
 * `enabled` is left to callers: a page that lacks the permission for an
 * endpoint should not call it at all rather than collect a 403.
 */

// --- Learners & instructors ------------------------------------------------

export function useAdminUsersQuery(params?: UserListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.users.list(params),
    queryFn: () => adminDirectoryApi.listUsers(params),
    enabled,
    // A directory changes slowly; a suspension invalidates it explicitly.
    staleTime: 30 * 1000,
  });
}

export function useAdminUserQuery(userId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.users.detail(userId),
    queryFn: () => adminDirectoryApi.getUser(userId),
    enabled: enabled && Boolean(userId),
  });
}

export function useSetUserStatusMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: AccountStatus }) =>
      adminDirectoryApi.setUserStatus(userId, { status }),
    onSuccess: (updated, { userId }) => {
      queryClient.setQueryData(queryKeys.admin.users.detail(userId), updated);
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.users.all });
    },
  });
}

export function useAdminInstructorsQuery(params?: PageParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.instructors(params),
    queryFn: () => adminDirectoryApi.listInstructors(params),
    enabled,
    staleTime: 60 * 1000,
  });
}

// --- Catalogue -------------------------------------------------------------

export function useAdminCoursesQuery(params?: AdminCourseListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.courses.list(params),
    queryFn: () => adminCatalogApi.listCourses(params),
    enabled,
    staleTime: 30 * 1000,
  });
}

export function useCourseStatsQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.courses.stats,
    queryFn: () => adminCatalogApi.courseStats(),
    enabled,
    staleTime: 60 * 1000,
  });
}

export function useAdminCertificatesQuery(params?: CertificateListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.certificates.list(params),
    queryFn: () => adminCatalogApi.listCertificates(params),
    enabled,
    staleTime: 30 * 1000,
  });
}

export function useAdminCertificateQuery(certificateId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.certificates.detail(certificateId),
    queryFn: () => adminCatalogApi.getCertificate(certificateId),
    enabled: enabled && Boolean(certificateId),
  });
}

export function useAdminSubmissionsQuery(params?: SubmissionListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.submissions(params),
    queryFn: () => adminCatalogApi.listSubmissions(params),
    enabled,
    staleTime: 30 * 1000,
  });
}

export function useAdminMediaQuery(params?: MediaListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.media.list(params),
    queryFn: () => adminCatalogApi.listMedia(params),
    enabled,
    staleTime: 30 * 1000,
  });
}

export function useDeleteMediaMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mediaId: string) => adminCatalogApi.deleteMedia(mediaId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.admin.media.all }),
  });
}

// --- Roles, permissions & administrators (super admin only) ----------------

export function usePermissionCatalogueQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.permissions,
    queryFn: () => adminAccessApi.listPermissions(),
    enabled,
    // The catalogue only changes when a migration adds a code.
    staleTime: 10 * 60 * 1000,
  });
}

export function useRolesQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.roles,
    queryFn: () => adminAccessApi.listRoles(),
    enabled,
    staleTime: 60 * 1000,
  });
}

export function useCreateRoleMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminAccessApi.createRole,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.admin.roles }),
  });
}

export function useSetRolePermissionsMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ roleId, permissions }: { roleId: string; permissions: string[] }) =>
      adminAccessApi.setRolePermissions(roleId, { permissions }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.roles });
      // An administrator's effective permissions follow their roles.
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.admins.all });
    },
  });
}

export function useDeleteRoleMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminAccessApi.deleteRole,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.roles });
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.admins.all });
    },
  });
}

export function useAdminsQuery(params?: PageParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.admins.list(params),
    queryFn: () => adminAccessApi.listAdmins(params),
    enabled,
    staleTime: 30 * 1000,
  });
}

export function useAdminQuery(adminId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.admins.detail(adminId),
    queryFn: () => adminAccessApi.getAdmin(adminId),
    enabled: enabled && Boolean(adminId),
  });
}

export function useCreateAdminMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminAccessApi.createAdmin,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.admin.admins.all }),
  });
}

export function useSetAdminStatusMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ adminId, status }: { adminId: string; status: AdminAccountStatus }) =>
      adminAccessApi.setAdminStatus(adminId, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.admin.admins.all }),
  });
}

/**
 * Granting or revoking a role does not change what that administrator can do
 * *right now* — their access token already carries the old permission set and
 * keeps working until it is refreshed.
 */
export function useSetAdminRoleMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      adminId,
      roleId,
      granted,
    }: {
      adminId: string;
      roleId: string;
      granted: boolean;
    }) =>
      granted
        ? adminAccessApi.grantRole(adminId, roleId)
        : adminAccessApi.revokeRole(adminId, roleId),
    onSuccess: (_result, { adminId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.admins.detail(adminId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.admins.all });
    },
  });
}

/**
 * Revoking is deliberately *not* under `/admin`. It lives at
 * `POST /certificates/{id}/revoke`, guarded by `certificate.revoke`, because a
 * second path to the same act would be a second place for the rule to drift.
 */
export function useRevokeCertificateMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (certificateId: string) => certificatesApi.revokeCertificate(certificateId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.admin.certificates.all }),
  });
}

// --- Sessions & audit ------------------------------------------------------

export function useAuditQuery(params?: AuditListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.audit(params),
    queryFn: () => adminPlatformApi.listAudit(params),
    enabled,
    staleTime: 15 * 1000,
  });
}

export function useUserSessionsQuery(params?: SessionListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.sessions.users(params),
    queryFn: () => adminPlatformApi.listUserSessions(params),
    enabled,
    // Sessions are the one thing here that genuinely moves minute to minute.
    staleTime: 10 * 1000,
  });
}

export function useAdminSessionsQuery(params?: AdminSessionListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.sessions.admins(params),
    queryFn: () => adminPlatformApi.listAdminSessions(params),
    enabled,
    staleTime: 10 * 1000,
  });
}

export function useRevokeSessionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, kind }: { sessionId: string; kind: "user" | "admin" }) =>
      kind === "admin"
        ? adminPlatformApi.revokeAdminSession(sessionId)
        : adminPlatformApi.revokeUserSession(sessionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.admin.sessions.all }),
  });
}

export function useRevokeAllUserSessionsMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => adminPlatformApi.revokeAllUserSessions(userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.admin.sessions.all }),
  });
}

// --- Taxonomy --------------------------------------------------------------

export function useCreateCategoryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminPlatformApi.createCategory,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.taxonomy.categories }),
  });
}

export function useRenameCategoryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ categoryId, name }: { categoryId: string; name: string }) =>
      adminPlatformApi.renameCategory(categoryId, { name }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.taxonomy.categories }),
  });
}

export function useDeleteCategoryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminPlatformApi.deleteCategory,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.taxonomy.categories }),
  });
}
