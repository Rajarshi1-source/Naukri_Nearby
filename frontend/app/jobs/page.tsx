"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useState } from "react";

import JobCard from "@/components/JobCard";
import { api, ApiError } from "@/lib/api";
import { JOB_CATEGORIES } from "@/lib/types";
import { titleCase } from "@/lib/format";
import type { SearchResponse } from "@/lib/types";

const JobMap = dynamic(() => import("@/components/JobMap"), {
  ssr: false,
  loading: () => <div className="h-full w-full animate-pulse rounded-xl bg-slate-200" />,
});

// India centroid fallback when geolocation is denied/unavailable.
const DEFAULT_CENTER: [number, number] = [22.9734, 78.6569];

export default function JobsPage() {
  const [center, setCenter] = useState<[number, number]>(DEFAULT_CENTER);
  const [located, setLocated] = useState(false);
  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");
  const [radius, setRadius] = useState(10);
  const [data, setData] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCenter([pos.coords.latitude, pos.coords.longitude]);
        setLocated(true);
      },
      () => setLocated(false),
      { timeout: 8000 },
    );
  }, []);

  const search = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (q.trim()) params.set("q", q.trim());
      if (category) params.set("category", category);
      params.set("radius", String(radius));
      params.set("size", "20");
      if (located) {
        params.set("lat", String(center[0]));
        params.set("lng", String(center[1]));
      }
      const res = await api<SearchResponse>(`/api/jobs/search?${params.toString()}`, {
        auth: false,
      });
      setData(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Search failed");
    } finally {
      setLoading(false);
    }
  }, [q, category, radius, located, center]);

  useEffect(() => {
    void search();
    // Re-run when location is resolved; manual filters use the Search button / Enter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [located]);

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Jobs near you</h1>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void search();
        }}
        className="mt-4 grid gap-3 rounded-xl border border-slate-200 bg-white p-4 sm:grid-cols-[1fr_auto_auto]"
      >
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search title, skill (e.g. delivery, electrician)"
          className="rounded-lg border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
        />
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          className="rounded-lg border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
        >
          <option value="">All categories</option>
          {JOB_CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {titleCase(c)}
            </option>
          ))}
        </select>
        <button
          type="submit"
          className="rounded-lg bg-brand px-5 py-2 font-semibold text-white hover:bg-brand-dark"
        >
          Search
        </button>
        <div className="sm:col-span-3">
          <label className="flex items-center gap-3 text-sm text-slate-600">
            <span className="whitespace-nowrap">Radius: {radius} km</span>
            <input
              type="range"
              min={1}
              max={50}
              value={radius}
              onChange={(e) => setRadius(Number(e.target.value))}
              onMouseUp={() => void search()}
              onTouchEnd={() => void search()}
              className="w-full accent-brand"
            />
          </label>
        </div>
      </form>

      {!located && (
        <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700">
          Location not shared — showing results without distance. Allow location for hyperlocal
          ranking.
        </p>
      )}
      {data?.fellBackToPostgis && (
        <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700">
          Showing nearby results (search degraded — full-text temporarily unavailable).
        </p>
      )}
      {error && (
        <p className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
      )}

      <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_minmax(280px,420px)]">
        <div className="space-y-3">
          {loading && <p className="text-slate-500">Searching…</p>}
          {!loading && data && data.results.length === 0 && (
            <p className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-slate-500">
              No jobs found. Try a wider radius or a different category.
            </p>
          )}
          {data?.results.map((item) => (
            <JobCard key={item.id} item={item} />
          ))}
        </div>
        <div className="h-[360px] lg:sticky lg:top-20 lg:h-[70vh]">
          <JobMap center={center} radiusKm={radius} />
        </div>
      </div>
    </div>
  );
}
