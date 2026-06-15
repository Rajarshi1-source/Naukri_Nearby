import { api } from "@/lib/api";
import {
  CandidateProfileSchema,
  ResumeParseStatusSchema,
  ResumeUploadResponseSchema,
  type CandidateProfile,
  type ResumeParseStatus,
  type ResumeUploadResponse,
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

  async apply(jobId: number): Promise<void> {
    await api<unknown>("/api/applications", {
      method: "POST",
      body: { jobId },
    });
  },
};
