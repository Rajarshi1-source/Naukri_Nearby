// Mirrors the backend DTOs (see naukrinearby controllers). Kept intentionally small for the MVP.

export type Role = "CANDIDATE" | "EMPLOYER" | "ADMIN";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  role: string;
}

export interface MeResponse {
  id: number;
  role: string;
  phone: string;
  name: string | null;
}

export interface JobResponse {
  id: number;
  title: string;
  slug: string;
  description: string | null;
  category: string | null;
  skillsRequired: string[];
  city: string | null;
  state: string | null;
  pincode: string | null;
  companyName: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryType: string | null;
  status: string | null;
  radiusKm: number | null;
  lat: number | null;
  lng: number | null;
  applicationCount: number | null;
  createdAt: string | null;
}

export interface SearchResultItem {
  id: number;
  title: string;
  slug: string;
  category: string | null;
  city: string | null;
  companyName: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  distanceKm: number | null;
}

export interface SearchResponse {
  results: SearchResultItem[];
  total: number;
  source: string;
  fellBackToPostgis: boolean;
}

export interface CandidateProfile {
  id: number;
  name: string | null;
  phone: string | null;
  email: string | null;
  city: string | null;
  state: string | null;
  skills: string[];
  totalExperienceMonths: number | null;
  profileCompleteness: number | null;
  resumeParsedAt: string | null;
}

export interface ResumeUploadResponse {
  resumeId: string;
  status: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export const JOB_CATEGORIES = [
  "DELIVERY",
  "DRIVER",
  "RETAIL",
  "ELECTRICAL",
  "PLUMBING",
  "CONSTRUCTION",
  "ACCOUNTING",
  "DATA_ENTRY",
  "SECURITY",
  "HOUSEKEEPING",
  "COOKING",
  "TAILORING",
  "SALES",
  "OTHER",
] as const;
