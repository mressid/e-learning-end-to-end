package com.elearning.community.application

import com.elearning.community.domain.DiscussionComment
import com.elearning.community.domain.DiscussionThread
import com.elearning.community.domain.ThreadStatus
import com.elearning.community.infrastructure.DiscussionCommentRepository
import com.elearning.community.infrastructure.DiscussionThreadRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.security.PlatformAccess
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Course discussions.
 *
 * Participation is the access rule: you take part in a course's discussion
 * because you are studying it or teaching it, not because of a platform role.
 */
@Service
class DiscussionService(
    private val threads: DiscussionThreadRepository,
    private val comments: DiscussionCommentRepository,
    private val context: CommunityContext,
    private val platformAccess: PlatformAccess,
) {

    @Transactional
    fun createThread(courseId: UUID, command: CreateThreadCommand, authorId: UUID): DiscussionThread {
        requireParticipant(courseId, authorId)

        // A lesson-scoped thread must belong to the course it is filed under,
        // or a thread could be hidden inside an unrelated course's lesson.
        command.lessonId?.let { lessonId ->
            if (context.courseIdOfItem(lessonId) != courseId) {
                throw BusinessRuleException("LESSON_NOT_IN_COURSE", "That lesson is not part of this course")
            }
            // The foreign key points at `lessons`, so an item without content
            // would fail at the database rather than here.
            if (!context.lessonExists(lessonId)) {
                throw BusinessRuleException("LESSON_HAS_NO_CONTENT", "That lesson has no content yet")
            }
        }

        return threads.save(
            DiscussionThread(
                courseId = courseId,
                authorId = authorId,
                title = command.title,
                lessonId = command.lessonId,
                body = command.body,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun listThreads(courseId: UUID, lessonId: UUID?, viewerId: UUID, pageable: Pageable): Page<DiscussionThread> {
        requireParticipant(courseId, viewerId)
        // Hidden threads are excluded for everyone, moderators included: this is
        // the reading list, not the moderation queue.
        return if (lessonId == null) {
            threads.findByCourseIdAndStatusNot(courseId, ThreadStatus.HIDDEN, pageable)
        } else {
            threads.findByCourseIdAndLessonIdAndStatusNot(courseId, lessonId, ThreadStatus.HIDDEN, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun getThread(threadId: UUID, viewerId: UUID): ThreadWithComments {
        val thread = requireThread(threadId)
        requireParticipant(thread.courseId, viewerId)
        if (thread.status == ThreadStatus.HIDDEN && !context.canEditCourse(thread.courseId, viewerId)) {
            throw NotFoundException("THREAD_NOT_FOUND", "Thread not found")
        }
        return ThreadWithComments(thread, comments.findByThreadIdOrderByCreatedAt(threadId))
    }

    @Transactional
    fun addComment(threadId: UUID, body: String, parentId: UUID?, authorId: UUID): DiscussionComment {
        val thread = requireThread(threadId)
        requireParticipant(thread.courseId, authorId)

        if (!thread.acceptsComments) {
            throw BusinessRuleException("THREAD_CLOSED", "This thread is not accepting replies")
        }
        parentId?.let { parent ->
            val parentComment = comments.findById(parent)
                .orElseThrow { NotFoundException("COMMENT_NOT_FOUND", "Parent comment not found") }
            // Otherwise a reply could be grafted onto a thread the author cannot see.
            if (parentComment.threadId != threadId) {
                throw BusinessRuleException("PARENT_IN_OTHER_THREAD", "The parent comment is in another thread")
            }
        }

        thread.updatedAt = java.time.Instant.now()
        return comments.save(
            DiscussionComment(threadId = threadId, authorId = authorId, body = body, parentId = parentId),
        )
    }

    /**
     * Status changes are for the thread's author (marking their own question
     * answered, or closing it) and for course editors (moderating).
     */
    @Transactional
    fun changeStatus(threadId: UUID, status: ThreadStatus, userId: UUID): DiscussionThread {
        val thread = requireThread(threadId)
        // A platform moderator counts as staff for hiding, but not for the
        // authorship check below - owning a thread is not something a
        // permission confers.
        val isEditor = context.canEditCourse(thread.courseId, userId) ||
            platformAccess.has("discussion.moderate")
        if (thread.authorId != userId && !isEditor) {
            throw ForbiddenException("THREAD_ACCESS_DENIED", "This thread is not yours")
        }
        if (status == ThreadStatus.HIDDEN && !isEditor) {
            throw ForbiddenException("MODERATION_DENIED", "Only course staff can hide a thread")
        }
        thread.changeStatus(status)
        return thread
    }

    private fun requireThread(threadId: UUID): DiscussionThread = threads.findById(threadId)
        .orElseThrow { NotFoundException("THREAD_NOT_FOUND", "Thread not found") }

    /** Enrolled students and course staff; nobody else, published or not. */
    private fun requireParticipant(courseId: UUID, userId: UUID) {
        if (!context.courseExists(courseId)) {
            throw NotFoundException("COURSE_NOT_FOUND", "Course not found")
        }
        if (!context.isEnrolled(courseId, userId) && !context.canEditCourse(courseId, userId)) {
            throw ForbiddenException("NOT_A_PARTICIPANT", "You are not part of this course")
        }
    }
}

data class CreateThreadCommand(val title: String, val body: String?, val lessonId: UUID?)

data class ThreadWithComments(val thread: DiscussionThread, val comments: List<DiscussionComment>)
