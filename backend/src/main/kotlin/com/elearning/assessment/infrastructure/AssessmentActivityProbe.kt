package com.elearning.assessment.infrastructure

import com.elearning.courses.application.ItemActivityProbe
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Assessment's answer: a quiz was attempted or an assignment handed in.
 *
 * Both tables hang off the item's own id - a quiz's primary key *is* its
 * course item id, and so is an assignment's - so the item ids can be used
 * directly rather than resolved through the quiz or assignment first.
 */
@Component
class AssessmentActivityProbe(
    private val attempts: QuizAttemptRepository,
    private val submissions: AssignmentSubmissionRepository,
) : ItemActivityProbe {

    override fun hasStudentActivity(itemIds: Collection<UUID>): Boolean {
        if (itemIds.isEmpty()) return false
        return attempts.existsByQuizIdIn(itemIds) || submissions.existsByAssignmentIdIn(itemIds)
    }

    override fun describe(): String = "quiz attempts or assignment submissions"
}
