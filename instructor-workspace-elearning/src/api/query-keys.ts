/**
 * Every cache key in one place, so an invalidation can never miss a query
 * because two call sites spelled the same key differently.
 */
export const queryKeys = {
  me: ["me"] as const,
  courses: {
    all: ["courses"] as const,
    mine: (params?: unknown) => ["courses", "mine", params ?? {}] as const,
    detail: (courseId: string) => ["courses", "detail", courseId] as const,
  },
  /**
   * Curriculum keys all begin with the course, including the ones addressed by
   * a section or an item id.
   *
   * The id alone would identify them perfectly well — the point of the prefix
   * is `ofCourse`. A structural edit drops that one key and every list under
   * the course goes with it; keyed by their own ids instead, item lists sat
   * outside the prefix and survived the invalidation that was meant to clear
   * them, so a newly added item did not appear until something else refetched.
   */
  curriculum: {
    ofCourse: (courseId: string) => ["curriculum", courseId] as const,
    sections: (courseId: string) => ["curriculum", courseId, "sections"] as const,
    items: (courseId: string, sectionId: string) =>
      ["curriculum", courseId, "items", sectionId] as const,
    item: (courseId: string, itemId: string) => ["curriculum", courseId, "item", itemId] as const,
    lesson: (courseId: string, itemId: string) =>
      ["curriculum", courseId, "lesson", itemId] as const,
    quiz: (courseId: string, itemId: string) => ["curriculum", courseId, "quiz", itemId] as const,
    quizQuestions: (courseId: string, itemId: string) =>
      ["curriculum", courseId, "quiz", itemId, "questions"] as const,
  },
  resources: {
    all: ["resources"] as const,
    of: (scope: string, ownerId: string) => ["resources", scope, ownerId] as const,
  },
} as const;
