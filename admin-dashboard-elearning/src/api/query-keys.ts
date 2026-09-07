export const queryKeys = {
  auth: {
    me: ["auth", "me"] as const,
    profile: ["auth", "profile"] as const,
  },
  courses: {
    all: ["courses"] as const,
    lists: () => [...queryKeys.courses.all, "list"] as const,
    list: (filters?: Record<string, unknown>) =>
      [...queryKeys.courses.lists(), filters ?? {}] as const,
    details: () => [...queryKeys.courses.all, "detail"] as const,
    detail: (id: string) => [...queryKeys.courses.details(), id] as const,
    sections: (courseId: string) => [...queryKeys.courses.detail(courseId), "sections"] as const,
    items: (sectionId: string) => ["sections", sectionId, "items"] as const,
    lesson: (itemId: string) => ["items", itemId, "lesson"] as const,
    quiz: (itemId: string) => ["items", itemId, "quiz"] as const,
    quizQuestions: (itemId: string) => ["items", itemId, "quiz", "questions"] as const,
    assignment: (itemId: string) => ["items", itemId, "assignment"] as const,
    assignmentSubmissions: (itemId: string) =>
      ["items", itemId, "assignment", "submissions"] as const,
    instructors: (courseId: string) =>
      [...queryKeys.courses.detail(courseId), "instructors"] as const,
    reviews: (courseId: string) => [...queryKeys.courses.detail(courseId), "reviews"] as const,
    reviewSummary: (courseId: string) =>
      [...queryKeys.courses.detail(courseId), "reviews", "summary"] as const,
    threads: (courseId: string) => [...queryKeys.courses.detail(courseId), "threads"] as const,
  },
  taxonomy: {
    categories: ["taxonomy", "categories"] as const,
    tags: ["taxonomy", "tags"] as const,
  },
  certificates: {
    all: ["certificates"] as const,
    mine: ["certificates", "mine"] as const,
    verify: (code: string) => ["certificates", "verify", code] as const,
  },
  notifications: {
    all: ["notifications"] as const,
    list: (page?: number, size?: number) =>
      [...queryKeys.notifications.all, "list", { page, size }] as const,
    unreadCount: ["notifications", "unread-count"] as const,
  },
  admin: {
    users: {
      all: ["admin", "users"] as const,
      list: (params?: object) => [...queryKeys.admin.users.all, "list", params ?? {}] as const,
      detail: (id: string) => [...queryKeys.admin.users.all, "detail", id] as const,
    },
    instructors: (params?: object) => ["admin", "instructors", params ?? {}] as const,
    courses: {
      all: ["admin", "courses"] as const,
      list: (params?: object) => [...queryKeys.admin.courses.all, "list", params ?? {}] as const,
      stats: ["admin", "courses", "stats"] as const,
    },
    certificates: {
      all: ["admin", "certificates"] as const,
      list: (params?: object) =>
        [...queryKeys.admin.certificates.all, "list", params ?? {}] as const,
      detail: (id: string) => [...queryKeys.admin.certificates.all, "detail", id] as const,
    },
    submissions: (params?: object) => ["admin", "submissions", params ?? {}] as const,
    media: {
      all: ["admin", "media"] as const,
      list: (params?: object) => [...queryKeys.admin.media.all, "list", params ?? {}] as const,
    },
    permissions: ["admin", "permissions"] as const,
    roles: ["admin", "roles"] as const,
    admins: {
      all: ["admin", "admins"] as const,
      list: (params?: object) => [...queryKeys.admin.admins.all, "list", params ?? {}] as const,
      detail: (id: string) => [...queryKeys.admin.admins.all, "detail", id] as const,
    },
    audit: (params?: object) => ["admin", "audit", params ?? {}] as const,
    sessions: {
      all: ["admin", "sessions"] as const,
      users: (params?: object) => [...queryKeys.admin.sessions.all, "users", params ?? {}] as const,
      admins: (params?: object) =>
        [...queryKeys.admin.sessions.all, "admins", params ?? {}] as const,
    },
  },
  learning: {
    myEnrollments: ["learning", "enrollments", "me"] as const,
    courseProgress: (courseId: string) => ["learning", "courses", courseId, "progress"] as const,
  },
};
