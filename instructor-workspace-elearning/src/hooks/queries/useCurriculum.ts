import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  curriculumApi,
  queryKeys,
  type CreateCourseItemRequest,
  type CreateSectionRequest,
  type SaveLessonRequest,
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

export function useSectionsQuery(courseId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.sections(courseId),
    queryFn: () => curriculumApi.sections(courseId),
    enabled: enabled && Boolean(courseId),
  });
}

/** One item, for a page that knows only its id. */
export function useItemQuery(itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.item(itemId),
    queryFn: () => curriculumApi.item(itemId),
    enabled: enabled && Boolean(itemId),
  });
}

export function useItemsQuery(sectionId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.items(sectionId),
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

export function useReorderSectionsMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: (orderedIds: string[]) => curriculumApi.reorderSections(courseId, orderedIds),
    onSuccess: invalidate,
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

export function useReorderItemsMutation(courseId: string) {
  const invalidate = useCurriculumInvalidator(courseId);
  return useMutation({
    mutationFn: ({ sectionId, orderedIds }: { sectionId: string; orderedIds: string[] }) =>
      curriculumApi.reorderItems(sectionId, orderedIds),
    onSuccess: invalidate,
  });
}

/**
 * A lesson item's content.
 *
 * A 404 here is the ordinary state of an item nobody has written yet, not a
 * failure — so it is not retried, and the caller reads `isError` as "empty".
 */
export function useLessonQuery(itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.lesson(itemId),
    queryFn: () => curriculumApi.lesson(itemId),
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
      queryClient.setQueryData(queryKeys.curriculum.lesson(itemId), lesson);
      // Publishing depends on a course having something to teach, so the course
      // itself can become publishable the moment a lesson is saved.
      queryClient.invalidateQueries({ queryKey: queryKeys.courses.detail(courseId) });
    },
  });
}
