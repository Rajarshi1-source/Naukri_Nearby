"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { jobService, type SearchParams } from "@/services/jobService";

/**
 * Runs a job search for the given committed params. The caller controls when params change
 * (submit / radius release / location resolved); React Query handles caching + de-duplication.
 */
export function useJobSearch(params: SearchParams, enabled = true) {
  return useQuery({
    queryKey: ["job-search", params],
    queryFn: () => jobService.search(params),
    enabled,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}
