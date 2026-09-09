package com.elearning.learning.infrastructure

import com.elearning.learning.domain.Lesson
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LessonRepository : JpaRepository<Lesson, UUID>
