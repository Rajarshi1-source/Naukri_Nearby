export function formatSalary(
  min: number | null | undefined,
  max: number | null | undefined,
  type?: string | null,
): string {
  if (!min && !max) return "Salary not disclosed";
  const suffix = type ? `/${type.toLowerCase()}` : "";
  const fmt = (n: number) => `₹${n.toLocaleString("en-IN")}`;
  if (min && max) return `${fmt(min)} – ${fmt(max)}${suffix}`;
  return `${fmt((min ?? max) as number)}${suffix}`;
}

export function formatDistance(km: number | null | undefined): string | null {
  if (km == null) return null;
  if (km < 1) return `${Math.round(km * 1000)} m away`;
  return `${km.toFixed(1)} km away`;
}

export function titleCase(s: string | null | undefined): string {
  if (!s) return "";
  return s
    .toLowerCase()
    .split(/[\s_]+/)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
