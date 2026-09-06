package com.elearning.admin.infrastructure

import com.elearning.admin.domain.AdminRefreshToken
import com.elearning.admin.domain.AdminUser
import com.elearning.admin.domain.AdminUserRole
import com.elearning.admin.domain.AdminUserRoleId
import com.elearning.admin.domain.Permission
import com.elearning.admin.domain.Role
import com.elearning.admin.domain.RolePermission
import com.elearning.admin.domain.RolePermissionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface AdminUserRepository : JpaRepository<AdminUser, UUID> {
    fun findByEmailIgnoreCase(email: String): Optional<AdminUser>
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AdminUser>
}

interface AdminRefreshTokenRepository : JpaRepository<AdminRefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): Optional<AdminRefreshToken>

    /** Live administrator sessions; see `RefreshTokenRepository.findLiveSessions`. */
    @Query(
        """
        select t.familyId as familyId, t.adminUserId as subjectId,
               min(t.issuedAt) as startedAt, max(t.issuedAt) as lastUsedAt,
               max(t.expiresAt) as expiresAt, count(t) as tokenCount
        from AdminRefreshToken t
        where :adminUserId is null or t.adminUserId = :adminUserId
        group by t.familyId, t.adminUserId
        having sum(case when t.revokedAt is null and t.expiresAt > :now then 1 else 0 end) > 0
        order by max(t.issuedAt) desc
        """,
        countQuery = """
        select count(distinct t.familyId) from AdminRefreshToken t
        where t.revokedAt is null and t.expiresAt > :now
          and (:adminUserId is null or t.adminUserId = :adminUserId)
        """,
    )
    fun findLiveSessions(
        @Param("now") now: Instant,
        @Param("adminUserId") adminUserId: UUID?,
        pageable: Pageable,
    ): Page<com.elearning.identity.infrastructure.SessionRow>
    fun findByFamilyId(familyId: UUID): List<AdminRefreshToken>
    fun findByAdminUserId(adminUserId: UUID): List<AdminRefreshToken>
}

interface PermissionRepository : JpaRepository<Permission, String> {
    fun findAllByOrderByCodeAsc(): List<Permission>
}

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findBySlug(slug: String): Optional<Role>
    fun existsBySlug(slug: String): Boolean
    fun findAllByOrderByNameAsc(): List<Role>

    /** How many admins still hold a super role - the lockout guard. */
    @Query(
        """
        select count(ur) from AdminUserRole ur
        where ur.id.roleId in (select r.id from Role r where r.isSuper = true)
        """,
    )
    fun countSuperAdminGrants(): Long
}

interface RolePermissionRepository : JpaRepository<RolePermission, RolePermissionId> {
    fun deleteByIdRoleId(roleId: UUID)

    @Query("select p.id.permissionCode from RolePermission p where p.id.roleId = :roleId")
    fun permissionCodesOf(@Param("roleId") roleId: UUID): List<String>
}

interface AdminUserRoleRepository : JpaRepository<AdminUserRole, AdminUserRoleId> {
    fun findByIdAdminUserId(adminUserId: UUID): List<AdminUserRole>
    fun findByIdRoleId(roleId: UUID): List<AdminUserRole>

    /**
     * Every permission code an admin holds, in one query.
     *
     * Resolved through the roles they hold; a super role short-circuits this in
     * the service and never reaches here.
     */
    @Query(
        """
        select rp.id.permissionCode
        from AdminUserRole ur, RolePermission rp
        where ur.id.adminUserId = :adminUserId and rp.id.roleId = ur.id.roleId
        """,
    )
    fun permissionCodesOf(@Param("adminUserId") adminUserId: UUID): List<String>

    @Query(
        """
        select case when count(ur) > 0 then true else false end
        from AdminUserRole ur
        where ur.id.adminUserId = :adminUserId
          and ur.id.roleId in (select r.id from Role r where r.isSuper = true)
        """,
    )
    fun isSuperAdmin(@Param("adminUserId") adminUserId: UUID): Boolean
}
