import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  assignmentApi,
  curriculumApi,
  queryKeys,
  type CourseItemResponse,
  type CreateCourseItemRequest,
  type CreateSectionRequest,
  type SaveLessonRequest,
  type SectionResponse,
  type UpdateCourseItemRequest,
  type UpdateSectionRequest,
} from "@/api";

/**
 * Structural edits drop the whole course's curriculum cache.
 *
 * Adding a section changes the sections list; deleting one takes its items with
 * it; reordering renumbers positions the item lists were keyed against. Trying
 * to invalidate precisely per operation means four rules that must stay in step
 * with the server's cascades — one key for the subtree is both simpler and
 * harder to get wrong.
 */
function useCurriculumInvalidator(courseId: string) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.curriculum.ofCourse(courseId) });
}

/**
 * The same rows, in the order `orderedIds` asks for.
 *
 * This is what lets a dragged row stay where it was dropped instead of snapping
 * back for the length of a round trip. If the two disagree about which rows
 * exist — something added in another tab, a stale list — the cache is left
 * alone and the refetch that follows settles it.
 */
function inOrder<T extends { id?: string }>(rows: T[], orderedIds: string[]): T[] {
  const byId = new Map(rows.map((row) => [row.id, row]));
  const next = orderedIds.flatMap((id) => {
    const row = byId.get(id);
    return row ? [row] : [];
  });
  return next.length === rows.length ? next : rows;
}

export function useSectionsQuery(courseId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.sections(courseId),
    queryFn: () => curriculumApi.sections(courseId),
    enabled: enabled && Boolean(courseId),
  });
}

/** One item, for a page that knows only its id. */
export function useItemQuery(courseId: string, itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.item(courseId, itemId),
    queryFn: () => curriculumApi.item(itemId),
    enabled: enabled && Boolean(itemId),
  });
}

export function useItemsQuery(courseId: string, sectionId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.items(courseId, sectionId),
    queryFn: () => curriculumApi.items(sectionId),
    enabled: enabled && Boolean(sectionId),
  });
}

export function useAddSectionMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: (body: CreateSectionRequest) => curriculumApi.addSection(courseId, body),
    onSuccess: invalidate,
  });
}

export function useUpdateSectionMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: ({ sectionId, ...body }: UpdateSectionRequest & { sectionId: string }) =>
      curriculumApi.updateSection(sectionId, body),
    onSuccess: invalidate,
  });
}

export function useDeleteSectionMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: (sectionId: string) => curriculumApi.deleteSection(sectionId),
    onSuccess: invalidate,
  });
}

/**
 * Reordering sections, shown before it is saved.
 *
 * A drag is a direct manipulation: the row is under the pointer, and the person
 * has already decided. Waiting for the server to answer before moving it makes
 * every drag flicker back to where it started, which reads as the drag having
 * failed. So the cache is rewritten on the spot and rolled back only if the
 * request is actually refused.
 */
export function useReorderSectionsMutation(courseId: string) {
  const queryClient = useQueryClient();
  const invalidate = useCurriculumInvalidator(courseId);
  const key = queryKeys.curriculum.sections(courseId);

  return useMutation({
    mutationFn: (orderedIds: string[]) => curriculumApi.reorderSections(courseId, orderedIds),
    onMutate: async (orderedIds) => {
      // An in-flight fetch would land after this and undo it.
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<SectionResponse[]>(key);
      if (previous) queryClient.setQueryData(key, inOrder(previous, orderedIds));
      return { previous };
    },
    onError: (_error, _orderedIds, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous);
    },
    onSettled: () => invalidate(),
  });
}

export function useAddItemMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: ({ sectionId, ...body }: CreateCourseItemRequest & { sectionId: string }) =>
      curriculumApi.addItem(sectionId, body),
    onSuccess: invalidate,
  });
}

export function useUpdateItemMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: ({ itemId, ...body }: UpdateCourseItemRequest & { itemId: string }) =>
      curriculumApi.updateItem(itemId, body),
    onSuccess: invalidate,
  });
}

export function useDeleteItemMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: (itemId: string) => curriculumApi.deleteItem(itemId),
    onSuccess: invalidate,
  });
}

/** As above, shown immediately and rolled back if the server refuses it. */
export function useReorderItemsMutation(courseId: string) {
  const queryClient = useQueryClient();
  const invalidate = useCurriculumInvalidator(courseId);

  return useMutation({
    mutationFn: ({ sectionId, orderedIds }: { sectionId: string; orderedIds: string[] }) =>
      curriculumApi.reorderItems(sectionId, orderedIds),
    onMutate: async ({ sectionId, orderedIds }) => {
      const key = queryKeys.curriculum.items(courseId, sectionId);
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<CourseItemResponse[]>(key);
      if (previous) queryClient.setQueryData(key, inOrder(previous, orderedIds));
      return { key, previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) queryClient.setQueryData(context.key, context.previous);
    },
    onSettled: () => invalidate(),
  });
}

/**
 * A lesson item's content.
 *
 * A 404 here is the ordinary state of an item nobody has written yet, not a
 * failure — so it is not retried, and the caller reads `isError` as "empty".
 */
export function useLessonQuery(courseId: string, itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.lesson(courseId, itemId),
    queryFn: () => curriculumApi.lesson(itemId),
    enabled: enabled && Boolean(itemId),
    retry: false,
  });
}

/**
 * An assignment's brief.
 *
 * Same bargain as the lesson above: a 404 is an item nobody has written yet,
 * so it is not retried and the caller reads `isError` as "empty".
 */
export function useAssignmentQuery(courseId: string, itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.assignment(courseId, itemId),
    queryFn: () => assignmentApi.assignment(itemId),
    enabled: enabled && Boolean(itemId),
    retry: false,
  });
}

export function useSaveLessonMutation(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, ...body }: SaveLessonRequest & { itemId: string }) =>
      curriculumApi.saveLesson(itemId, body),
    onSuccess: (lesson, { itemId }) => {
      queryClient.setQueryData(queryKeys.curriculum.lesson(courseId, itemId), lesson);
      // Publishing depends on a course having something to teach, so the course
      // itself can become publishable the moment a lesson is saved.
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.detail(courseId) });
    },
  });
}
