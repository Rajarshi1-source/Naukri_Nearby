import { z } from "zod";

import { api, API_URL, tokens } from "@/lib/api";
import {
  ApplicationSchema,
  CandidateProfileSchema,
  ConsentSchema,
  JobMatchSchema,
  JobResponseSchema,
  NotificationPrefsSchema,
  PageSchema,
  ResumeParseStatusSchema,
  ResumeUploadResponseSchema,
  SkillGapSchema,
  type Application,
  type CandidateProfile,
  type Consent,
  type JobMatch,
  type JobResponse,
  type NotificationPrefs,
  type ResumeParseStatus,
  type ResumeUploadResponse,
  type SkillGap,
} from "@/types";

export const candidateService = {
  async uploadResume(file: File): Promise<ResumeUploadResponse> {
    const form = new FormData();
    form.append("file", file);
    const raw = await api<unknown>("/api/resumes/upload", {
      method: "POST",
      body: form,
    });
    return ResumeUploadResponseSchema.parse(raw);
  },

  async parseStatus(resumeId: string): Promise<ResumeParseStatus> {
    const raw = await api<unknown>(
      `/api/resumes/${encodeURIComponent(resumeId)}/parse-status`,
    );
    return ResumeParseStatusSchema.parse(raw);
  },

  async getProfile(): Promise<CandidateProfile> {
    const raw = await api<unknown>("/api/candidate/profile");
    return CandidateProfileSchema.parse(raw);
  },

  async saveJob(jobId: number): Promise<void> {
    await api<void>(`/api/candidate/saved-jobs/${jobId}`, { method: "POST" });
  },

  async unsaveJob(jobId: number): Promise<void> {
    await api<void>(`/api/candidate/saved-jobs/${jobId}`, { method: "DELETE" });
  },

  async savedJobs(): Promise<JobResponse[]> {
    return z.array(JobResponseSchema).parse(await api<unknown>("/api/candidate/saved-jobs"));
  },

  /** Idempotent server-side: a second apply returns 409, which callers treat as "already applied". */
  async apply(jobId: number, coverNote?: string): Promise<void> {
    await api<unknown>("/api/applications", {
      method: "POST",
      body: coverNote ? { jobId, coverNote } : { jobId },
    });
  },

  async applications(): Promise<Application[]> {
    const raw = await api<unknown>("/api/candidate/applications?size=50");
    return PageSchema(ApplicationSchema).parse(raw).content;
  },

  async recommendations(): Promise<JobMatch[]> {
    return z.array(JobMatchSchema).parse(await api<unknown>("/api/candidate/recommendations"));
  },

  async preferences(): Promise<NotificationPrefs> {
    return NotificationPrefsSchema.parse(await api<unknown>("/api/candidate/notification-preferences"));
  },

  async updatePreferences(prefs: Partial<NotificationPrefs>): Promise<NotificationPrefs> {
    const raw = await api<unknown>("/api/candidate/notification-preferences", {
      method: "PUT",
      body: prefs,
    });
    return NotificationPrefsSchema.parse(raw);
  },

  async consents(): Promise<Consent[]> {
    return z.array(ConsentSchema).parse(await api<unknown>("/api/candidate/consents"));
  },

  async setConsent(purpose: Consent["purpose"], granted: boolean): Promise<Consent> {
    const raw = await api<unknown>("/api/candidate/consents", {
      method: "PUT",
      body: { purpose, granted },
    });
    return ConsentSchema.parse(raw);
  },

  async skillGap(jobId: number): Promise<SkillGap> {
    return SkillGapSchema.parse(await api<unknown>(`/api/candidate/jobs/${jobId}/skill-gap`));
  },

  /** DPDP right to access: downloads the JSON export as a file. */
  async downloadMyData(): Promise<void> {
    const res = await fetch(`${API_URL}/api/candidate/account/export`, {
      headers: tokens.access ? { Authorization: `Bearer ${tokens.access}` } : {},
    });
    if (!res.ok) throw new Error(`Export failed (${res.status})`);
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "naukrinearby-my-data.json";
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  },

  async deleteAccount(): Promise<void> {
    await api<void>("/api/candidate/account", { method: "DELETE" });
  },
};
