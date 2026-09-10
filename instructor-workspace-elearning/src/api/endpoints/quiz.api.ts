import { apiClient, parseApiError } from "../client";
import type {
  AddQuestionRequest,
  AuthorQuestionResponse,
  QuizResponse,
  SaveQuizRequest,
  UpdateQuestionRequest,
} from "../types";

/**
 * Authoring a quiz: its settings, and the ordered questions inside it.
 *
 * Everything hangs off the course item — a quiz has no id of its own, it *is*
 * the QUIZ item — which is why every path here starts at `/items/{itemId}` and
 * nothing takes a quiz id.
 *
 * The attempt and grading endpoints are deliberately absent. They belong to
 * whoever sits the quiz, and nothing in this workspace does.
 */
export const quizApi = {
  /**
   * A quiz's settings.
   *
   * 404 until one has been saved. A QUIZ item exists as a slot in the sequence
   * before it holds anything, exactly like a lesson, so the editor reads that
   * 404 as "new quiz" rather than as a failure.
   */
  async quiz(itemId: string): Promise<QuizResponse> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/quiz", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Creates the quiz or replaces it; there is no partial update.
   *
   * Every field is authoritative, so the caller has to send the settings it is
   * keeping as well as the ones it is changing. Omitting one clears it.
   */
  async saveQuiz(itemId: string, body: SaveQuizRequest): Promise<QuizResponse> {
    const { data, error } = await apiClient.PUT("/api/v1/items/{itemId}/quiz", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** The questions with their correct answers. Editors only; this is the answer key. */
  async questions(itemId: string): Promise<AuthorQuestionResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/items/{itemId}/quiz/questions", {
      params: { path: { itemId } },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Appended to the end. Moving it is a separate, whole-sequence operation.
   *
   * Refused with `QUIZ_HAS_ATTEMPTS` once a student has sat the quiz, since a
   * longer paper marks the next cohort out of more than the last.
   */
  async addQuestion(itemId: string, body: AddQuestionRequest): Promise<AuthorQuestionResponse> {
    const { data, error } = await apiClient.POST("/api/v1/items/{itemId}/quiz/questions", {
      params: { path: { itemId } },
      body,
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /**
   * Only the fields sent are changed.
   *
   * Wording and points are always editable. The type and the options are
   * refused with `QUIZ_HAS_ATTEMPTS` once a student has sat the quiz, since
   * both decide how answers already given were marked.
   */
  async updateQuestion(
    itemId: string,
    questionId: string,
    body: UpdateQuestionRequest,
  ): Promise<AuthorQuestionResponse> {
    const { data, error } = await apiClient.PATCH(
      "/api/v1/items/{itemId}/quiz/questions/{questionId}",
      { params: { path: { itemId, questionId } }, body },
    );
    if (error || !data) throw parseApiError(error);
    return data;
  },

  /** Refused with `QUIZ_HAS_ATTEMPTS` once a student has sat the quiz. */
  async deleteQuestion(itemId: string, questionId: string): Promise<void> {
    const { error } = await apiClient.DELETE("/api/v1/items/{itemId}/quiz/questions/{questionId}", {
      params: { path: { itemId, questionId } },
    });
    if (error) throw parseApiError(error);
  },

  /** Send every id, in the order you want. Partial orders are rejected. */
  async reorderQuestions(itemId: string, orderedIds: string[]): Promise<AuthorQuestionResponse[]> {
    const { data, error } = await apiClient.PUT("/api/v1/items/{itemId}/quiz/questions/order", {
      params: { path: { itemId } },
      body: { orderedIds },
    });
    if (error || !data) throw parseApiError(error);
    return data;
  },
};
