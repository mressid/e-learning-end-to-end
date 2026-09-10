import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  resourcesApi,
  mediaApi,
  queryKeys,
  type AttachResourceRequest,
  type CreateResourceRequest,
  type ResourceScope,
} from "@/api";

export function useResourcesQuery(scope: ResourceScope, ownerId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.resources.of(scope, ownerId),
    queryFn: () => resourcesApi.list(scope, ownerId),
    enabled: enabled && Boolean(ownerId),
  });
}

/**
 * Creating a resource and attaching it, as one action.
 *
 * They are two calls because a resource genuinely is independent of any course —
 * but nobody creating a document from within a lesson wants a detached resource
 * if the second call fails. Doing both here keeps that pair in one place instead
 * of every caller remembering it.
 *
 * A `file` is uploaded first, and its media id becomes the resource's source.
 */
export function useAddResourceMutation(scope: ResourceScope, ownerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      file,
      onProgress,
      relationshipType,
      ...resource
    }: CreateResourceRequest & {
      file?: File | undefined;
      onProgress?: ((stage: string) => void) | undefined;
      relationshipType?: AttachResourceRequest["relationshipType"];
    }) => {
      const body: CreateResourceRequest = { ...resource };
      if (file) {
        const media = await mediaApi.upload(file, {
          // Attaching a document is a quick upload with nowhere to draw a bar,
          // so the byte counts are collapsed back to the phase label this
          // mutation has always handed its callers.
          ...(onProgress ? { onProgress: (progress) => onProgress(progress.stage) } : {}),
        });
        body.mediaId = media.id ?? null;
      }
      const created = await resourcesApi.create(body);
      return resourcesApi.attach(scope, ownerId, {
        resourceId: created.id!,
        ...(relationshipType ? { relationshipType } : {}),
      });
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.resources.of(scope, ownerId) }),
  });
}

/** Course scope only — the API offers no detach for sections or items. */
export function useDetachCourseResourceMutation(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (resourceId: string) => resourcesApi.detachFromCourse(courseId, resourceId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.resources.of("course", courseId) }),
  });
}
