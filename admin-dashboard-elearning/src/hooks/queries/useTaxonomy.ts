import { useQuery } from "@tanstack/react-query";
import { taxonomyApi, queryKeys } from "@/api";

export function useCategoriesQuery() {
  return useQuery({
    queryKey: queryKeys.taxonomy.categories,
    queryFn: () => taxonomyApi.getCategories(),
    staleTime: 5 * 60 * 1000,
  });
}

export function useTagsQuery() {
  return useQuery({
    queryKey: queryKeys.taxonomy.tags,
    queryFn: () => taxonomyApi.getTags(),
    staleTime: 5 * 60 * 1000,
  });
}
