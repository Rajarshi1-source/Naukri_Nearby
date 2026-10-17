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
  preferredLanguage: z.string().nullable().optional(),
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
  // Trust signals (public) and moderation data (owner/admin only) — ADR 0007.
  trustLevel: z.string().nullable().optional(),
  employerSince: z.string().nullable().optional(),
  moderationStatus: z.string().nullable().optional(),
  riskScore: z.number().nullable().optional(),
  riskReasons: z.array(z.string()).nullable().optional(),
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
  trustLevel: z.string().nullable().optional(),
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

export const TRUST_LEVELS = ["BUSINESS_VERIFIED", "PHONE_VERIFIED", "UNVERIFIED"] as const;
export type TrustLevel = (typeof TRUST_LEVELS)[number];

export const MODERATION_STATUSES = ["APPROVED", "PENDING_REVIEW", "REJECTED"] as const;
export type ModerationStatus = (typeof MODERATION_STATUSES)[number];

export const REPORT_REASONS = [
  "ASKS_FOR_PAYMENT",
  "FAKE_JOB",
  "WRONG_DETAILS",
  "OFFENSIVE",
  "OTHER",
] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];

export const PageSchema = <T extends z.ZodTypeAny>(item: T) =>
  z.object({
    content: z.array(item),
    number: z.number(),
    size: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
  });

export const ApplicationSchema = z.object({
  id: z.number(),
  jobId: z.number(),
  candidateId: z.number(),
  status: z.string(),
  coverNote: z.string().nullable(),
  appliedAt: z.string().nullable(),
  jobTitle: z.string().nullable().optional(),
  jobSlug: z.string().nullable().optional(),
  candidateName: z.string().nullable().optional(),
  candidatePhone: z.string().nullable().optional(),
});
export type Application = z.infer<typeof ApplicationSchema>;

export const JobMatchSchema = z.object({
  job: JobResponseSchema,
  similarity: z.number(),
  distanceKm: z.number().nullable(),
});
export type JobMatch = z.infer<typeof JobMatchSchema>;

export const NotificationPrefsSchema = z.object({
  channel: z.string().nullable(),
  language: z.string().nullable(),
  radiusKm: z.number().nullable(),
  categories: z.array(z.string()).default([]),
  frequency: z.string().nullable(),
  active: z.boolean().nullable(),
});
export type NotificationPrefs = z.infer<typeof NotificationPrefsSchema>;

export const ConsentSchema = z.object({
  purpose: z.enum(["RESUME_PROCESSING", "WHATSAPP_ALERTS"]),
  granted: z.boolean().nullable(),
  noticeVersion: z.string().nullable(),
  recordedAt: z.string().nullable(),
});
export type Consent = z.infer<typeof ConsentSchema>;

export const SkillGapSchema = z.object({
  jobId: z.number(),
  matched: z.array(z.string()),
  missing: z.array(z.string()),
  matchPercent: z.number(),
  suggestions: z.array(
    z.object({
      skill: z.string(),
      searchTerm: z.string(),
      portals: z.array(z.object({ id: z.string(), name: z.string(), url: z.string() })),
    }),
  ),
});
export type SkillGap = z.infer<typeof SkillGapSchema>;

export const EmployerProfileSchema = z.object({
  companyName: z.string(),
  companyType: z.string().nullable(),
  gstNumber: z.string().nullable(),
  gstVerified: z.boolean(),
  trustLevel: z.string(),
  address: z.string().nullable(),
  city: z.string().nullable(),
  state: z.string().nullable(),
  createdAt: z.string().nullable(),
  verifiedAt: z.string().nullable(),
});
export type EmployerProfile = z.infer<typeof EmployerProfileSchema>;

export const ModerationItemSchema = z.object({
  job: JobResponseSchema,
  reportCount: z.number(),
  moderationNote: z.string().nullable(),
  moderatedAt: z.string().nullable(),
});
export type ModerationItem = z.infer<typeof ModerationItemSchema>;

export const JobTranslationSchema = z.object({
  jobId: z.number(),
  language: z.string(),
  sourceLanguage: z.string(),
  title: z.string(),
  description: z.string().nullable(),
  translated: z.boolean(),
  provider: z.string(),
});
export type JobTranslation = z.infer<typeof JobTranslationSchema>;

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
