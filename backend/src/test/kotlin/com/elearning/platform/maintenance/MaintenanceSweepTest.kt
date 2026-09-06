package com.elearning.platform.maintenance

import com.elearning.learning.application.EnrollmentExpirySweeper
import com.elearning.learning.domain.Enrollment
import com.elearning.learning.domain.EnrollmentStatus
import com.elearning.courses.domain.Course
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.learning.infrastructure.EnrollmentRepository
import com.elearning.platform.media.MediaObjectRepository
import com.elearning.platform.media.MediaService
import com.elearning.platform.media.MediaStatus
import com.elearning.platform.media.RequestUploadCommand
import com.elearning.shared.testing.IntegrationTest
import org.springframework.transaction.annotation.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The housekeeping sweeps.
 *
 * Both are invoked directly rather than waited on: a test that sleeps until a
 * cron fires is slow and flaky, and the schedule is Spring's job to get right,
 * not something worth re-testing.
 */
@SpringBootTest
@ActiveProfiles("test")
class MaintenanceSweepTest(
    @Autowired val sweeper: PendingUploadSweeper,
    @Autowired val expirySweeper: EnrollmentExpirySweeper,
    @Autowired val mediaService: MediaService,
    @Autowired val media: MediaObjectRepository,
    @Autowired val enrollments: EnrollmentRepository,
    @Autowired val users: UserRepository,
    @Autowired val courses: CourseRepository,
    @Autowired val lock: SchedulerLock,
    @Autowired val dataSource: javax.sql.DataSource,
    @Autowired val attemptSweeper: com.elearning.assessment.application.QuizAttemptExpirySweeper,
    @Autowired val quizzes: com.elearning.assessment.infrastructure.QuizRepository,
    @Autowired val quizAttempts: com.elearning.assessment.infrastructure.QuizAttemptRepository,
    @Autowired val sections: com.elearning.courses.infrastructure.CourseSectionRepository,
    @Autowired val items: com.elearning.courses.infrastructure.CourseItemRepository,
) : IntegrationTest() {

    /** A QUIZ course item with a quiz row, built straight through the repositories. */
    private fun newTimedQuiz(timeLimitSeconds: Int?): UUID {
        val owner = newUser()
        val courseId = newCourse(owner)
        val section = sections.save(
            com.elearning.courses.domain.CourseSection(courseId = courseId, title = "S", position = 0),
        )
        val item = items.save(
            com.elearning.courses.domain.CourseItem(
                sectionId = requireNotNull(section.id),
                title = "Q",
                type = com.elearning.courses.domain.CourseItemType.QUIZ,
                position = 0,
            ),
        )
        val quiz = quizzes.save(
            com.elearning.assessment.domain.Quiz(
                courseItemId = requireNotNull(item.id),
                title = "Timed",
                timeLimitSeconds = timeLimitSeconds,
            ),
        )
        return quiz.courseItemId
    }

    @Test
    fun `an abandoned timed attempt is expired`() {
        val quizId = newTimedQuiz(timeLimitSeconds = 60)
        val attempt = quizAttempts.save(
            com.elearning.assessment.domain.QuizAttempt(quizId, newUser(), 1),
        )

        // An hour later the 60-second window is long gone.
        val expired = attemptSweeper.sweep(Instant.now().plus(Duration.ofHours(1)))

        assertThat(expired).isGreaterThanOrEqualTo(1)
        assertThat(quizAttempts.findById(requireNotNull(attempt.id)).orElseThrow().status)
            .isEqualTo(com.elearning.assessment.domain.AttemptStatus.EXPIRED)
    }

    @Test
    fun `an untimed attempt never expires`() {
        val quizId = newTimedQuiz(timeLimitSeconds = null)
        val attempt = quizAttempts.save(
            com.elearning.assessment.domain.QuizAttempt(quizId, newUser(), 1),
        )

        attemptSweeper.sweep(Instant.now().plus(Duration.ofDays(365)))

        // No time limit means no deadline to miss.
        assertThat(quizAttempts.findById(requireNotNull(attempt.id)).orElseThrow().status)
            .isEqualTo(com.elearning.assessment.domain.AttemptStatus.IN_PROGRESS)
    }

    /**
     * A second connection stands in for a second application instance:
     * `pg_advisory_lock` is session-scoped, so it is genuinely held there while
     * this instance tries to take it.
     */
    @Test
    fun `a job already running elsewhere is skipped rather than failed`() {
        val key = lock.keyFor("contended-job")

        dataSource.connection.use { otherInstance ->
            otherInstance.autoCommit = true
            otherInstance.createStatement().use { it.execute("select pg_advisory_lock($key)") }
            try {
                var ran = false
                val result = lock.ifNotRunningElsewhere("contended-job") { ran = true; "ran" }

                // Skipping is the correct outcome, not an error: the other
                // instance is doing the work.
                assertThat(result).isNull()
                assertThat(ran).isFalse()
            } finally {
                otherInstance.createStatement().use { it.execute("select pg_advisory_unlock($key)") }
            }
        }

        // Once released, this instance can take it.
        assertThat(lock.ifNotRunningElsewhere("contended-job") { "ran" }).isEqualTo("ran")
    }

    @Test
    fun `different jobs do not block each other`() {
        val key = lock.keyFor("job-a")
        dataSource.connection.use { other ->
            other.autoCommit = true
            other.createStatement().use { it.execute("select pg_advisory_lock($key)") }
            try {
                assertThat(lock.ifNotRunningElsewhere("job-a") { "a" }).isNull()
                // A different name hashes to a different key.
                assertThat(lock.ifNotRunningElsewhere("job-b") { "b" }).isEqualTo("b")
            } finally {
                other.createStatement().use { it.execute("select pg_advisory_unlock($key)") }
            }
        }
    }

    @Test
    @Transactional
    fun `the scheduler lock admits one holder and is released with the transaction`() {
        // Held for the life of this transaction.
        val first = lock.ifNotRunningElsewhere("test-job") { "ran" }
        assertThat(first).isEqualTo("ran")

        // Re-entrant within the same transaction: the same holder, not a second one.
        assertThat(lock.ifNotRunningElsewhere("test-job") { "again" }).isEqualTo("again")

        // A different job name is a different lock and is unaffected.
        assertThat(lock.ifNotRunningElsewhere("other-job") { "other" }).isEqualTo("other")
    }

    private fun newUser(): UUID {
        val unique = System.nanoTime()
        return requireNotNull(
            users.save(
                User(
                    email = "sweep-$unique@example.com",
                    username = "sweep-$unique",
                    passwordHash = "{noop}irrelevant",
                    status = UserStatus.ACTIVE,
                ),
            ).id,
        )
    }

    private fun newCourse(ownerId: UUID): UUID {
        val unique = System.nanoTime()
        return requireNotNull(
            courses.save(
                Course(ownerId = ownerId, title = "Sweep $unique", slug = "sweep-$unique"),
            ).id,
        )
    }

    private fun requestUpload(): Pair<UUID, String> {
        val ticket = mediaService.requestUpload(
            RequestUploadCommand(filename = "abandoned.txt", contentType = "text/plain"),
            uploaderId = newUser(),
        )
        return requireNotNull(ticket.media.id) to ticket.uploadUrl.toString()
    }

    private fun putBytes(url: String, body: String) {
        (URI.create(url).toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "text/plain")
            outputStream.use { it.write(body.toByteArray()) }
            check(responseCode == 200) { "upload failed: $responseCode" }
            disconnect()
        }
    }

    @Test
    fun `an upload that never happened is marked failed`() {
        val (mediaId, _) = requestUpload()

        // Sweeping from far enough in the future that the grace period has passed.
        val result = sweeper.sweep(Instant.now().plus(Duration.ofDays(2)))

        assertThat(result.failed).isGreaterThanOrEqualTo(1)
        assertThat(media.findById(mediaId).orElseThrow().status).isEqualTo(MediaStatus.FAILED)
    }

    @Test
    fun `an upload that finished but was never confirmed is recovered, not discarded`() {
        val (mediaId, url) = requestUpload()
        putBytes(url, "the client uploaded this and then crashed")

        val result = sweeper.sweep(Instant.now().plus(Duration.ofDays(2)))

        assertThat(result.recovered).isGreaterThanOrEqualTo(1)
        val record = media.findById(mediaId).orElseThrow()
        assertThat(record.status).isEqualTo(MediaStatus.AVAILABLE)
        // Size read from storage, so the recovered row is as good as a confirmed one.
        assertThat(record.sizeBytes).isEqualTo("the client uploaded this and then crashed".length.toLong())
    }

    @Test
    fun `an upload still inside the grace period is left alone`() {
        val (mediaId, _) = requestUpload()

        // Sweeping at "now": the row is seconds old, well inside the grace window.
        sweeper.sweep(Instant.now())

        assertThat(media.findById(mediaId).orElseThrow().status).isEqualTo(MediaStatus.PENDING)
    }

    @Test
    fun `an enrolment past its access window becomes expired`() {
        val student = newUser()
        val enrollment = enrollments.save(
            Enrollment(studentId = student, courseId = newCourse(student)).apply {
                expiresAt = Instant.now().minus(Duration.ofDays(1))
            },
        )

        val expired = expirySweeper.sweep()

        assertThat(expired).isGreaterThanOrEqualTo(1)
        assertThat(enrollments.findById(requireNotNull(enrollment.id)).orElseThrow().status)
            .isEqualTo(EnrollmentStatus.EXPIRED)
    }

    @Test
    fun `an enrolment with no expiry, or one still in the future, is untouched`() {
        val first = newUser()
        val openEnded = enrollments.save(Enrollment(first, newCourse(first)))
        val second = newUser()
        val future = enrollments.save(
            Enrollment(studentId = second, courseId = newCourse(second)).apply {
                expiresAt = Instant.now().plus(Duration.ofDays(30))
            },
        )

        expirySweeper.sweep()

        assertThat(enrollments.findById(requireNotNull(openEnded.id)).orElseThrow().status)
            .isEqualTo(EnrollmentStatus.ACTIVE)
        assertThat(enrollments.findById(requireNotNull(future.id)).orElseThrow().status)
            .isEqualTo(EnrollmentStatus.ACTIVE)
    }
}
