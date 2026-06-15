import { api } from "@/lib/api";
import { AuthResponseSchema, type AuthResponse } from "@/types";

export const authService = {
  async sendOtp(phone: string): Promise<void> {
    await api<void>("/api/auth/send-otp", {
      method: "POST",
      auth: false,
      body: { phone: phone.trim() },
    });
  },

  async verifyOtp(
    phone: string,
    code: string,
    role: string,
  ): Promise<AuthResponse> {
    const raw = await api<unknown>("/api/auth/verify-otp", {
      method: "POST",
      auth: false,
      body: { phone: phone.trim(), code: code.trim(), role },
    });
    return AuthResponseSchema.parse(raw);
  },
};
