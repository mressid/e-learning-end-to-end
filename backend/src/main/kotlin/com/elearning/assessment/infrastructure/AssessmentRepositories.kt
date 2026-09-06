package com.elearning.assessment.infrastructure

import com.elearning.assessment.domain.Question
import com.elearning.assessment.domain.QuestionOption
import com.elearning.assessment.domain.Quiz
import com.elearning.assessment.domain.QuizAttempt
import com.elearning.assessment.domain.QuizResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface QuizRepository : JpaRepository<Quiz, UUID>

interface QuestionRepository : JpaRepository<Question, UUID> {

    fun findByQuizIdOrderByPosition(quizId: UUID): List<Question>

    @Query("select coalesce(max(q.position), -1) from Question q where q.quizId = :quizId")
    fun maxPosition(@Param("quizId") quizId: UUID): Int
}

interface QuestionOptionRepository : JpaRepository<QuestionOption, UUID> {

    fun findByQuestionIdOrderByPosition(questionId: UUID): List<QuestionOption>

    fun findByQuestionIdIn(questionIds: Collection<UUID>): List<QuestionOption>
}

interface QuizAttemptRepository : JpaRepository<QuizAttempt, UUID> {

    /** Whether any of these items has been attempted, for the delete guard. */
    fun existsByQuizIdIn(quizIds: Collection<UUID>): Boolean

    fun countByQuizIdAndStudentId(quizId: UUID, studentId: UUID): Int

    fun findByQuizIdAndStudentIdOrderByAttemptNumberDesc(quizId: UUID, studentId: UUID): List<QuizAttempt>

    fun findByStatus(
        status: com.elearning.assessment.domain.AttemptStatus,
        pageable: org.springframework.data.domain.Pageable,
    ): List<QuizAttempt>
}

interface QuizResponseRepository : JpaRepository<QuizResponse, UUID> {

    fun findByAttemptId(attemptId: UUID): List<QuizResponse>

    fun findByAttemptIdIn(attemptIds: Collection<UUID>): List<QuizResponse>

    fun deleteByAttemptId(attemptId: UUID)
}
