package com.elearning.assessment.infrastructure

import com.elearning.assessment.domain.Assignment
import com.elearning.assessment.domain.AssignmentSubmission
import com.elearning.assessment.domain.SubmissionStatus
import com.elearning.assessment.domain.SubmissionMedia
import com.elearning.assessment.domain.SubmissionMediaId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AssignmentRepository : JpaRepository<Assignment, UUID>

interface AssignmentSubmissionRepository : JpaRepository<AssignmentSubmission, UUID> {

    /** Whether any of these items has been handed in against, for the delete guard. */
    fun existsByAssignmentIdIn(assignmentIds: Collection<UUID>): Boolean

    /** The dashboard's grading queue, across every course. */
    fun findByStatus(status: SubmissionStatus, pageable: Pageable): Page<AssignmentSubmission>

    fun countByAssignmentIdAndStudentId(assignmentId: UUID, studentId: UUID): Int

    fun findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(
        assignmentId: UUID,
        studentId: UUID,
    ): List<AssignmentSubmission>

    fun findByAssignmentId(assignmentId: UUID, pageable: Pageable): Page<AssignmentSubmission>
}

interface SubmissionMediaRepository : JpaRepository<SubmissionMedia, SubmissionMediaId> {

    fun findByIdSubmissionId(submissionId: UUID): List<SubmissionMedia>

    fun existsByIdMediaId(mediaId: UUID): Boolean
}
