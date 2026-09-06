import { apiClient, parseApiError } from "../client";
import type { CategoryResponse, Term } from "../types";

export const taxonomyApi = {
  async getCategories(): Promise<CategoryResponse[]> {
    const { data, error } = await apiClient.GET("/api/v1/categories");
    if (error || !data) throw parseApiError(error);
    return data;
  },

  async getTags(): Promise<Term[]> {
    const { data, error } = await apiClient.GET("/api/v1/tags");
    if (error || !data) throw parseApiError(error);
    return data;
  },
};
