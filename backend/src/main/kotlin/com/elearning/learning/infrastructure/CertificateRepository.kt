package com.elearning.learning.infrastructure

import com.elearning.learning.domain.Certificate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface CertificateRepository : JpaRepository<Certificate, UUID> {

    fun findByVerificationCode(verificationCode: String): Optional<Certificate>

    fun existsByMediaId(mediaId: UUID): Boolean

    fun findByStudentIdAndCourseId(studentId: UUID, courseId: UUID): Optional<Certificate>

    fun findByStudentIdOrderByIssuedAtDesc(studentId: UUID): List<Certificate>

    fun findByCourseId(courseId: UUID, pageable: Pageable): Page<Certificate>

    fun findByCertificateNumberIgnoreCase(certificateNumber: String): Optional<Certificate>

    /** Revoked or still valid, for the dashboard's filter. */
    @Query(
        """
        select c from Certificate c
        where (:revoked = true and c.revokedAt is not null)
           or (:revoked = false and c.revokedAt is null)
        """,
    )
    fun findByRevoked(@Param("revoked") revoked: Boolean, pageable: Pageable): Page<Certificate>
}
