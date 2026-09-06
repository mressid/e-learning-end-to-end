package com.elearning.shared.events

import java.util.UUID

/**
 * Meaningful business occurrences, published with Spring's
 * `ApplicationEventPublisher` (§24).
 *
 * Business modules publish these and know nothing about who reacts: the
 * learning module does not depend on notifications or certificates, it simply
 * announces that something happened.
 */
data class CourseCompleted(
    val studentId: UUID,
    val courseId: UUID,
    val enrollmentId: UUID,
)

data class AssignmentGraded(
    val studentId: UUID,
    val courseItemId: UUID,
    val submissionId: UUID,
)

data class QuizGraded(
    val studentId: UUID,
    val courseItemId: UUID,
    val attemptId: UUID,
    val passed: Boolean,
)

data class CertificateIssued(
    val studentId: UUID,
    val courseId: UUID,
    val certificateId: UUID,
    val certificateNumber: String,
)
