import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  resourcesApi,
  mediaApi,
  queryKeys,
  type AttachResourceRequest,
  type AttachedResourceResponse,
  type CreateResourceRequest,
  type ResourceScope,
  type UpdateResourceRequest,
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

export function useDetachResourceMutation(scope: ResourceScope, ownerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (resourceId: string) => resourcesApi.detach(scope, ownerId, resourceId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.resources.of(scope, ownerId) }),
  });
}

/** Kept for the course panel, which had this before the other scopes could detach. */
export function useDetachCourseResourceMutation(courseId: string) {
  return useDetachResourceMutation("course", courseId);
}

/**
 * Editing one block in place.
 *
 * Refused with `RESOURCE_SHARED` when the material is also used by a course
 * the editor cannot reach. That is not a failure to retry, so callers should
 * show what the server said rather than a generic message.
 */
export function useUpdateResourceMutation(scope: ResourceScope, ownerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ resourceId, ...body }: UpdateResourceRequest & { resourceId: string }) =>
      resourcesApi.update(resourceId, body),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.resources.of(scope, ownerId) }),
  });
}

/**
 * Reordering an item's blocks.
 *
 * The new order is written into the cache before the request goes, because a
 * dragged row that springs back to where it was while the network thinks about
 * it reads as a failed drag. The refetch on settle is what corrects it if the
 * server disagreed.
 */
export function useReorderItemResourcesMutation(itemId: string) {
  const queryClient = useQueryClient();
  const key = queryKeys.resources.of("item", itemId);

  return useMutation({
    mutationFn: (resourceIds: string[]) => resourcesApi.reorderItemResources(itemId, resourceIds),
    onMutate: async (resourceIds) => {
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<AttachedResourceResponse[]>(key);
      if (previous) {
        const byId = new Map(previous.map((row) => [row.resource?.id, row]));
        queryClient.setQueryData(
          key,
          resourceIds
            .map((id) => byId.get(id))
            .filter(Boolean as unknown as (r: unknown) => boolean),
        );
      }
      return { previous };
    },
    onError: (_error, _ids, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: key }),
  });
}
