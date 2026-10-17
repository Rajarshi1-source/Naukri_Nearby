import { api } from "@/lib/api";
import {
  JobResponseSchema,
  ModerationItemSchema,
  PageSchema,
  type JobResponse,
  type ModerationItem,
  type ReportReason,
} from "@/types";

export const trustService = {
  /** Any signed-in user. 409 = already reported. */
  async report(jobId: number, reason: ReportReason, details?: string): Promise<{ underReview: boolean }> {
    const raw = await api<{ reported: boolean; underReview: boolean }>(`/api/jobs/${jobId}/report`, {
      method: "POST",
      body: details ? { reason, details } : { reason },
    });
    return { underReview: Boolean(raw?.underReview) };
  },

  /** Admin only (ROLE_ADMIN). */
  async queue(): Promise<ModerationItem[]> {
    const raw = await api<unknown>("/api/admin/moderation/jobs?size=50");
    return PageSchema(ModerationItemSchema).parse(raw).content;
  },

  async decide(jobId: number, decision: "APPROVED" | "REJECTED", note?: string): Promise<JobResponse> {
    const raw = await api<unknown>(`/api/admin/moderation/jobs/${jobId}/decision`, {
      method: "POST",
      body: note ? { decision, note } : { decision },
    });
    return JobResponseSchema.parse(raw);
  },

  async setLanguage(language: string): Promise<void> {
    await api<void>("/api/auth/me/language", { method: "PUT", body: { language } });
  },
};
