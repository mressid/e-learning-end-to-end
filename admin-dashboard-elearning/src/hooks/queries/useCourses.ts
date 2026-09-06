import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  coursesApi,
  queryKeys,
  type CourseListParams,
  type CreateCourseRequest,
  type UpdateCourseRequest,
  type CreateSectionRequest,
  type CreateCourseItemRequest,
  type SaveLessonRequest,
  type SaveQuizRequest,
  type AddQuestionRequest,
  type SaveAssignmentRequest,
} from "@/api";

export function useCoursesQuery(params?: CourseListParams) {
  return useQuery({
    queryKey: queryKeys.courses.list(params as Record<string, unknown>),
    queryFn: () => coursesApi.listCourses(params),
    staleTime: 60 * 1000,
  });
}

export function useCourseQuery(id: string) {
  return useQuery({
    queryKey: queryKeys.courses.detail(id),
    queryFn: () => coursesApi.getCourse(id),
    enabled: Boolean(id),
  });
}

export function useCreateCourseMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CreateCourseRequest) => coursesApi.createCourse(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.lists() });
    },
  });
}

export function useUpdateCourseMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateCourseRequest }) =>
      coursesApi.updateCourse(id, body),
    onSuccess: (data, variables) => {
      queryClient.setQueryData(queryKeys.courses.detail(variables.id), data);
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.lists() });
    },
  });
}

export function usePublishCourseMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => coursesApi.publishCourse(id),
    onSuccess: (data, id) => {
      queryClient.setQueryData(queryKeys.courses.detail(id), data);
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.lists() });
    },
  });
}

export function useUnpublishCourseMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => coursesApi.unpublishCourse(id),
    onSuccess: (data, id) => {
      queryClient.setQueryData(queryKeys.courses.detail(id), data);
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.lists() });
    },
  });
}

export function useArchiveCourseMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => coursesApi.archiveCourse(id),
    onSuccess: (data, id) => {
      queryClient.setQueryData(queryKeys.courses.detail(id), data);
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.lists() });
    },
  });
}

export function useSectionsQuery(courseId: string) {
  return useQuery({
    queryKey: queryKeys.courses.sections(courseId),
    queryFn: () => coursesApi.getSections(courseId),
    enabled: Boolean(courseId),
  });
}

export function useCreateSectionMutation(courseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CreateSectionRequest) => coursesApi.createSection(courseId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.sections(courseId) });
    },
  });
}

export function useItemsQuery(sectionId: string) {
  return useQuery({
    queryKey: queryKeys.courses.items(sectionId),
    queryFn: () => coursesApi.getItems(sectionId),
    enabled: Boolean(sectionId),
  });
}

export function useCreateItemMutation(sectionId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CreateCourseItemRequest) => coursesApi.createItem(sectionId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.items(sectionId) });
    },
  });
}

export function useLessonQuery(itemId: string) {
  return useQuery({
    queryKey: queryKeys.courses.lesson(itemId),
    queryFn: () => coursesApi.getLesson(itemId),
    enabled: Boolean(itemId),
  });
}

export function useSaveLessonMutation(itemId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: SaveLessonRequest) => coursesApi.saveLesson(itemId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.lesson(itemId) });
    },
  });
}

export function useSaveQuizMutation(itemId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: SaveQuizRequest) => coursesApi.saveQuiz(itemId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.quiz(itemId) });
    },
  });
}

export function useAddQuizQuestionMutation(itemId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: AddQuestionRequest) => coursesApi.addQuizQuestion(itemId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.quizQuestions(itemId) });
    },
  });
}

export function useSaveAssignmentMutation(itemId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: SaveAssignmentRequest) => coursesApi.saveAssignment(itemId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.assignment(itemId) });
    },
  });
}

export function useCourseInstructorsQuery(courseId: string) {
  return useQuery({
    queryKey: queryKeys.courses.instructors(courseId),
    queryFn: () => coursesApi.getInstructors(courseId),
    enabled: Boolean(courseId),
  });
}

export function useCourseReviewsQuery(
  courseId: string,
  params?: { page?: number; size?: number; sort?: string[] },
) {
  return useQuery({
    queryKey: [...queryKeys.courses.reviews(courseId), params ?? {}],
    queryFn: () => coursesApi.getReviews(courseId, params),
    enabled: Boolean(courseId),
  });
}

export function useCourseReviewSummaryQuery(courseId: string) {
  return useQuery({
    queryKey: queryKeys.courses.reviewSummary(courseId),
    queryFn: () => coursesApi.getReviewSummary(courseId),
    enabled: Boolean(courseId),
  });
}
