"use client";

import dynamic from "next/dynamic";
import { useMemo, useState } from "react";

import JobCard from "@/components/JobCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Slider } from "@/components/ui/slider";
import { useGeolocation } from "@/hooks/useGeolocation";
import { useJobSearch } from "@/hooks/useJobSearch";
import { titleCase } from "@/lib/format";
import { JOB_CATEGORIES } from "@/types";
import type { SearchParams } from "@/services/jobService";

const JobMap = dynamic(() => import("@/components/JobMap"), {
  loading: () => (
    <div className="h-full w-full animate-pulse rounded-xl bg-muted" />
  ),
});

// India centroid fallback when geolocation is denied/unavailable.
const DEFAULT_CENTER: [number, number] = [22.9734, 78.6569];
const ALL = "ALL";

interface Props {
  initialQuery: string;
  initialCategory: string;
  initialRadius: number;
}

interface Committed {
  q: string;
  category: string;
  radius: number;
}

export default function SearchClient({
  initialQuery,
  initialCategory,
  initialRadius,
}: Props) {
  const { coords, located } = useGeolocation();

  const [q, setQ] = useState(initialQuery);
  const [category, setCategory] = useState(initialCategory || ALL);
  const [radius, setRadius] = useState(initialRadius);
  const [committed, setCommitted] = useState<Committed>({
    q: initialQuery,
    category: initialCategory,
    radius: initialRadius,
  });

  const params = useMemo<SearchParams>(
    () => ({
      q: committed.q || undefined,
      category: committed.category || undefined,
      radius: committed.radius,
      size: 20,
      lat: located && coords ? coords[0] : undefined,
      lng: located && coords ? coords[1] : undefined,
    }),
    [committed, located, coords],
  );

  const { data, isFetching, isError, error } = useJobSearch(params);

  function commit(next?: Partial<Committed>) {
    setCommitted({
      q,
      category: category === ALL ? "" : category,
      radius,
      ...next,
    });
  }

  const center: [number, number] = located && coords ? coords : DEFAULT_CENTER;

  return (
    <div>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          commit();
        }}
        className="mt-4 grid gap-3 rounded-xl border bg-card p-4 sm:grid-cols-[1fr_auto_auto]"
      >
        <Input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search title, skill (e.g. delivery, electrician)"
        />
        <Select value={category} onValueChange={setCategory}>
          <SelectTrigger className="w-full sm:w-48">
            <SelectValue placeholder="All categories" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All categories</SelectItem>
            {JOB_CATEGORIES.map((c) => (
              <SelectItem key={c} value={c}>
                {titleCase(c)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button type="submit">Search</Button>
        <div className="sm:col-span-3">
          <Label className="flex items-center gap-3 text-sm text-muted-foreground">
            <span className="whitespace-nowrap">Radius: {radius} km</span>
            <Slider
              min={1}
              max={50}
              step={1}
              value={[radius]}
              onValueChange={(v) => setRadius(v[0] ?? radius)}
              onValueCommit={(v) => commit({ radius: v[0] ?? radius })}
              className="w-full"
            />
          </Label>
        </div>
      </form>

      {!located && (
        <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700">
          Location not shared — showing results without distance. Allow location
          for hyperlocal ranking.
        </p>
      )}
      {data?.fellBackToPostgis && (
        <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700">
          Showing nearby results (search degraded — full-text temporarily
          unavailable).
        </p>
      )}
      {isError && (
        <p className="mt-3 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error instanceof Error ? error.message : "Search failed"}
        </p>
      )}

      <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_minmax(280px,420px)]">
        <div className="space-y-3">
          {isFetching && <p className="text-muted-foreground">Searching…</p>}
          {!isFetching && data && data.results.length === 0 && (
            <p className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">
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
