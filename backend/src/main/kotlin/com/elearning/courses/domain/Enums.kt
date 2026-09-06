package com.elearning.courses.domain

/** Mirrors `courses.status`. */
enum class CourseStatus { DRAFT, PUBLISHED, ARCHIVED }

/** Mirrors `courses.level`. */
enum class CourseLevel { BEGINNER, INTERMEDIATE, ADVANCED, ALL_LEVELS }

/** Mirrors `course_items.type`. */
enum class CourseItemType { LESSON, QUIZ, ASSIGNMENT }

/** Mirrors `course_instructors.role`. */
enum class InstructorRole { PRIMARY, ASSISTANT, GUEST }
