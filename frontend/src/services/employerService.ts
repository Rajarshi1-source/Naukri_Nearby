import { api } from "@/lib/api";
import {
  ApplicationSchema,
  EmployerProfileSchema,
  JobResponseSchema,
  PageSchema,
  type Application,
  type EmployerProfile,
  type JobResponse,
} from "@/types";

export interface NewJobInput {
  title: string;
  description: string;
  category: string;
  skillsRequired: string[];
  salaryMin?: number;
  salaryMax?: number;
  salaryType: "MONTHLY";
  experienceRequiredMonths: number;
  lat: number;
  lng: number;
  address?: string;
  city: string;
  state: string;
  pincode?: string;
  radiusKm: number;
}

export interface EmployerProfileInput {
  companyName: string;
  companyType?: string;
  gstNumber?: string;
  city?: string;
  state?: string;
}

export const employerService = {
  async profile(): Promise<EmployerProfile> {
    return EmployerProfileSchema.parse(await api<unknown>("/api/employer/profile"));
  },

  async updateProfile(input: EmployerProfileInput): Promise<EmployerProfile> {
    const raw = await api<unknown>("/api/employer/profile", { method: "PUT", body: input });
    return EmployerProfileSchema.parse(raw);
  },

  /** Returns the owner view, including moderationStatus and riskReasons (ADR 0007). */
  async postJob(input: NewJobInput): Promise<JobResponse> {
    return JobResponseSchema.parse(await api<unknown>("/api/jobs", { method: "POST", body: input }));
  },

  async myJobs(): Promise<JobResponse[]> {
    const raw = await api<unknown>("/api/employer/jobs?size=50");
    return PageSchema(JobResponseSchema).parse(raw).content;
  },

  async closeJob(jobId: number): Promise<JobResponse> {
    const raw = await api<unknown>(`/api/jobs/${jobId}/status`, {
      method: "PATCH",
      body: { status: "CLOSED" },
    });
    return JobResponseSchema.parse(raw);
  },

  async applicants(jobId: number): Promise<Application[]> {
    const raw = await api<unknown>(`/api/employer/jobs/${jobId}/applications?size=50`);
    return PageSchema(ApplicationSchema).parse(raw).content;
  },

  async setApplicationStatus(applicationId: number, status: string): Promise<Application> {
    const raw = await api<unknown>(`/api/applications/${applicationId}/status`, {
      method: "PATCH",
      body: { status },
    });
    return ApplicationSchema.parse(raw);
  },
};
