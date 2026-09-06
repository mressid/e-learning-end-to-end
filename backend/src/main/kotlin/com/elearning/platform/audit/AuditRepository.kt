package com.elearning.platform.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditRepository : JpaRepository<AuditEntry, UUID> {

    fun findByAction(action: String, pageable: Pageable): Page<AuditEntry>

    fun findByActorId(actorId: UUID, pageable: Pageable): Page<AuditEntry>

    fun findByTargetTypeAndTargetId(
        targetType: String,
        targetId: UUID,
        pageable: Pageable,
    ): Page<AuditEntry>
}
