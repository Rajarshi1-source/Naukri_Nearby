import { api, serverGet } from "@/lib/api";
import {
  JobResponseSchema,
  SearchResponseSchema,
  type JobResponse,
  type SearchResponse,
} from "@/types";

export interface SearchParams {
  q?: string;
  category?: string;
  radius: number;
  size?: number;
  lat?: number;
  lng?: number;
}

function toQuery(params: SearchParams): string {
  const sp = new URLSearchParams();
  if (params.q?.trim()) sp.set("q", params.q.trim());
  if (params.category) sp.set("category", params.category);
  sp.set("radius", String(params.radius));
  sp.set("size", String(params.size ?? 20));
  if (params.lat != null && params.lng != null) {
    sp.set("lat", String(params.lat));
    sp.set("lng", String(params.lng));
  }
  return sp.toString();
}

export const jobService = {
  /** Public job search; parsed with Zod at the boundary. */
  async search(params: SearchParams): Promise<SearchResponse> {
    const raw = await api<unknown>(`/api/jobs/search?${toQuery(params)}`, {
      auth: false,
    });
    return SearchResponseSchema.parse(raw);
  },

  /** Server-safe single-job fetch for SSR. Returns null on 404. */
  async getBySlug(slug: string): Promise<JobResponse | null> {
    const raw = await serverGet<unknown>(
      `/api/jobs/${encodeURIComponent(slug)}`,
    );
    if (raw == null) return null;
    return JobResponseSchema.parse(raw);
  },
};
