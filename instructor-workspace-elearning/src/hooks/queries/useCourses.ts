import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { coursesApi, queryKeys, type CreateCourseRequest, type UpdateCourseRequest } from "@/api";
import type { MineParams } from "@/api";

/** The courses this instructor owns or co-instructs, drafts included. */
export function useMyCoursesQuery(params?: MineParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.courses.mine(params),
    queryFn: () => coursesApi.mine(params),
    enabled,
  });
}

export function useCourseQuery(courseId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.courses.detail(courseId),
    queryFn: () => coursesApi.get(courseId),
    enabled: enabled && Boolean(courseId),
  });
}

export function useCreateCourseMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateCourseRequest) => coursesApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.courses.all }),
  });
}

export function useUpdateCourseMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ courseId, ...body }: UpdateCourseRequest & { courseId: string }) =>
      coursesApi.update(courseId, body),
    onSuccess: (updated, { courseId }) => {
      queryClient.setQueryData(queryKeys.courses.detail(courseId), updated);
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.all });
    },
  });
}

/**
 * Publishing and returning to draft.
 *
 * One hook for both directions: they are the same decision read two ways, and
 * splitting them would mean two call sites that must stay in step about which
 * caches to drop.
 */
export function useSetCoursePublishedMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ courseId, published }: { courseId: string; published: boolean }) =>
      published ? coursesApi.publish(courseId) : coursesApi.unpublish(courseId),
    onSuccess: (updated, { courseId }) => {
      queryClient.setQueryData(queryKeys.courses.detail(courseId), updated);
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.all });
    },
  });
}
