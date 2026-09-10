import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  coursesApi,
  mediaApi,
  queryKeys,
  type CreateCourseRequest,
  type UpdateCourseRequest,
} from "@/api";
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
 * Uploading an image and making it the course's thumbnail, as one action.
 *
 * Two calls, but never one without the other: an image uploaded and then not
 * attached is an orphan in the public bucket that nothing will ever point at
 * or clean up. Keeping them in a single mutation means a caller cannot do half
 * of it, and means the retry is of the whole thing.
 */
export function useSetCourseThumbnailMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      courseId,
      file,
      onProgress,
    }: {
      courseId: string;
      file: File;
      onProgress?: ((percent: number) => void) | undefined;
    }) => {
      // PUBLIC is not a choice here; the endpoint refuses anything else,
      // because a thumbnail behind an expiring URL is a broken image in the
      // catalogue an hour later.
      const media = await mediaApi.upload(file, {
        visibility: "PUBLIC",
        ...(onProgress
          ? {
              onProgress: ({ loaded, total }) =>
                onProgress(total > 0 ? Math.round((loaded / total) * 100) : 0),
            }
          : {}),
      });
      if (!media.id) throw new Error("The image uploaded but came back without an id.");
      return coursesApi.setThumbnail(courseId, media.id);
    },
    onSuccess: (updated, { courseId }) => {
      queryClient.setQueryData(queryKeys.courses.detail(courseId), updated);
      // The listing shows thumbnails too, so it is now stale.
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
