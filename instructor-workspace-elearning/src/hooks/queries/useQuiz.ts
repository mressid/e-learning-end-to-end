import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  parseApiError,
  queryKeys,
  quizApi,
  type AddQuestionRequest,
  type AuthorQuestionResponse,
  type SaveQuizRequest,
  type UpdateQuestionRequest,
} from "@/api";

/**
 * Everything about one quiz hangs off the item, so every mutation here drops
 * both of its keys: adding a question changes the list, and deleting one can
 * change nothing else but is not worth a second rule to say so.
 */
function useQuizInvalidator(courseId: string, itemId: string) {
  const queryClient = useQueryClient();
  return () =>
    queryClient.invalidateQueries({ queryKey: queryKeys.curriculum.quiz(courseId, itemId) });
}

/**
 * A quiz's settings, or `undefined` when the item has none yet.
 *
 * The 404 that says "not written yet" is a normal state of a course being
 * built, not a failure to report, so it is folded into an absent value here
 * and every other error is left to surface.
 */
export function useQuizQuery(courseId: string, itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.quiz(courseId, itemId),
    queryFn: async () => {
      try {
        return await quizApi.quiz(itemId);
      } catch (err) {
        if (parseApiError(err).code === "QUIZ_NOT_FOUND") return null;
        throw err;
      }
    },
    enabled: enabled && Boolean(itemId),
  });
}

export function useQuizQuestionsQuery(courseId: string, itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.curriculum.quizQuestions(courseId, itemId),
    queryFn: () => quizApi.questions(itemId),
    enabled: enabled && Boolean(itemId),
  });
}

/** Creates the quiz or replaces it. Send the settings you are keeping too. */
export function useSaveQuizMutation(courseId: string, itemId: string) {
  const invalidate = useQuizInvalidator(courseId, itemId);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SaveQuizRequest) => quizApi.saveQuiz(itemId, body),
    onSuccess: () => {
      invalidate();
      // The item's own title is drawn from the course structure, and the rail
      // beside this editor renders it, so a renamed quiz has to reach there too.
      queryClient.invalidateQueries({ queryKey: queryKeys.curriculum.ofCourse(courseId) });
    },
  });
}

export function useAddQuestionMutation(courseId: string, itemId: string) {
  const invalidate = useQuizInvalidator(courseId, itemId);
  return useMutation({
    mutationFn: (body: AddQuestionRequest) => quizApi.addQuestion(itemId, body),
    onSuccess: invalidate,
  });
}

export function useUpdateQuestionMutation(courseId: string, itemId: string) {
  const invalidate = useQuizInvalidator(courseId, itemId);
  return useMutation({
    mutationFn: ({ questionId, ...body }: UpdateQuestionRequest & { questionId: string }) =>
      quizApi.updateQuestion(itemId, questionId, body),
    onSuccess: invalidate,
  });
}

export function useDeleteQuestionMutation(courseId: string, itemId: string) {
  const invalidate = useQuizInvalidator(courseId, itemId);
  return useMutation({
    mutationFn: (questionId: string) => quizApi.deleteQuestion(itemId, questionId),
    onSuccess: invalidate,
  });
}

/**
 * Reordering, with the new order shown before the server has agreed to it.
 *
 * Same bargain as the curriculum lists: a dragged row that snaps back for the
 * length of a round trip reads as a failed drag. If the cache and the request
 * disagree about which questions exist, the optimistic write is skipped and the
 * refetch settles it.
 */
export function useReorderQuestionsMutation(courseId: string, itemId: string) {
  const queryClient = useQueryClient();
  const key = queryKeys.curriculum.quizQuestions(courseId, itemId);
  return useMutation({
    mutationFn: (orderedIds: string[]) => quizApi.reorderQuestions(itemId, orderedIds),
    onMutate: (orderedIds) => {
      const rows = queryClient.getQueryData<AuthorQuestionResponse[]>(key);
      if (!rows) return;
      const byId = new Map(rows.map((row) => [row.id, row]));
      const next = orderedIds.flatMap((id) => {
        const row = byId.get(id);
        return row ? [row] : [];
      });
      if (next.length === rows.length) queryClient.setQueryData(key, next);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: key }),
  });
}
