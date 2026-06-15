import { z } from "zod";

// Zod schemas mirror the backend DTOs and are parsed at the API boundary (see src/services).
// Inferred types replace the previously hand-written interfaces.

export const RoleSchema = z.enum(["CANDIDATE", "EMPLOYER", "ADMIN"]);
export type Role = z.infer<typeof RoleSchema>;

export const AuthResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  role: z.string(),
});
export type AuthResponse = z.infer<typeof AuthResponseSchema>;

export const MeResponseSchema = z.object({
  id: z.number(),
  role: z.string(),
  phone: z.string(),
  name: z.string().nullable(),
});
export type MeResponse = z.infer<typeof MeResponseSchema>;

export const JobResponseSchema = z.object({
  id: z.number(),
  title: z.string(),
  slug: z.string(),
  description: z.string().nullable(),
  category: z.string().nullable(),
  skillsRequired: z.array(z.string()).default([]),
  city: z.string().nullable(),
  state: z.string().nullable(),
  pincode: z.string().nullable(),
  companyName: z.string().nullable(),
  salaryMin: z.number().nullable(),
  salaryMax: z.number().nullable(),
  salaryType: z.string().nullable(),
  status: z.string().nullable(),
  radiusKm: z.number().nullable(),
  lat: z.number().nullable(),
  lng: z.number().nullable(),
  applicationCount: z.number().nullable(),
  createdAt: z.string().nullable(),
});
export type JobResponse = z.infer<typeof JobResponseSchema>;

export const SearchResultItemSchema = z.object({
  id: z.number(),
  title: z.string(),
  slug: z.string(),
  category: z.string().nullable(),
  city: z.string().nullable(),
  companyName: z.string().nullable(),
  salaryMin: z.number().nullable(),
  salaryMax: z.number().nullable(),
  distanceKm: z.number().nullable(),
});
export type SearchResultItem = z.infer<typeof SearchResultItemSchema>;

export const SearchResponseSchema = z.object({
  results: z.array(SearchResultItemSchema).default([]),
  total: z.number(),
  source: z.string(),
  fellBackToPostgis: z.boolean(),
});
export type SearchResponse = z.infer<typeof SearchResponseSchema>;

export const CandidateProfileSchema = z.object({
  id: z.number(),
  name: z.string().nullable(),
  phone: z.string().nullable(),
  email: z.string().nullable(),
  city: z.string().nullable(),
  state: z.string().nullable(),
  skills: z.array(z.string()).default([]),
  totalExperienceMonths: z.number().nullable(),
  profileCompleteness: z.number().nullable(),
  resumeParsedAt: z.string().nullable(),
});
export type CandidateProfile = z.infer<typeof CandidateProfileSchema>;

export const ResumeUploadResponseSchema = z.object({
  resumeId: z.string(),
  status: z.string(),
});
export type ResumeUploadResponse = z.infer<typeof ResumeUploadResponseSchema>;

export const ResumeParseStatusSchema = z.object({
  resumeId: z.string(),
  status: z.string(),
});
export type ResumeParseStatus = z.infer<typeof ResumeParseStatusSchema>;

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
