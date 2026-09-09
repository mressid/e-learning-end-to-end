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
  curriculum: {
    /** Everything under one course, so a structural edit can drop it wholesale. */
    ofCourse: (courseId: string) => ["curriculum", courseId] as const,
    sections: (courseId: string) => ["curriculum", courseId, "sections"] as const,
    items: (sectionId: string) => ["curriculum", "items", sectionId] as const,
    item: (itemId: string) => ["curriculum", "item", itemId] as const,
    lesson: (itemId: string) => ["curriculum", "lesson", itemId] as const,
  },
  resources: {
    all: ["resources"] as const,
    of: (scope: string, ownerId: string) => ["resources", scope, ownerId] as const,
  },
} as const;
