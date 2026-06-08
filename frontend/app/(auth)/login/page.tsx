"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";

import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { AuthResponse } from "@/lib/types";

type Step = "phone" | "otp";
type Role = "CANDIDATE" | "EMPLOYER";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { login } = useAuth();

  const [step, setStep] = useState<Step>("phone");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [role, setRole] = useState<Role>("CANDIDATE");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const redirectTo = params.get("next") || "/jobs";

  async function sendOtp(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api<void>("/api/auth/send-otp", {
        method: "POST",
        auth: false,
        body: { phone: phone.trim() },
      });
      setStep("otp");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not send OTP");
    } finally {
      setBusy(false);
    }
  }

  async function verifyOtp(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const auth = await api<AuthResponse>("/api/auth/verify-otp", {
        method: "POST",
        auth: false,
        body: { phone: phone.trim(), code: code.trim(), role },
      });
      await login(auth);
      router.push(redirectTo);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Invalid code");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto mt-10 max-w-md rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold text-slate-900">
        {step === "phone" ? "Login or register" : "Enter the code"}
      </h1>
      <p className="mt-1 text-sm text-slate-500">
        {step === "phone"
          ? "We'll send a one-time password to your phone."
          : `Sent to ${phone}. (Dev: use 123456)`}
      </p>

      {error && (
        <div className="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}

      {step === "phone" ? (
        <form onSubmit={sendOtp} className="mt-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700">Phone number</label>
            <input
              type="tel"
              required
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="9876543210"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
            />
          </div>
          <RoleToggle role={role} setRole={setRole} />
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-lg bg-brand px-4 py-2.5 font-semibold text-white hover:bg-brand-dark disabled:opacity-60"
          >
            {busy ? "Sending…" : "Send OTP"}
          </button>
        </form>
      ) : (
        <form onSubmit={verifyOtp} className="mt-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700">One-time password</label>
            <input
              inputMode="numeric"
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="123456"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 tracking-widest focus:border-brand focus:outline-none"
            />
          </div>
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-lg bg-brand px-4 py-2.5 font-semibold text-white hover:bg-brand-dark disabled:opacity-60"
          >
            {busy ? "Verifying…" : "Verify & continue"}
          </button>
          <button
            type="button"
            onClick={() => setStep("phone")}
            className="w-full text-sm text-slate-500 hover:text-slate-700"
          >
            Change phone number
          </button>
        </form>
      )}
    </div>
  );
}

function RoleToggle({ role, setRole }: { role: Role; setRole: (r: Role) => void }) {
  return (
    <div>
      <label className="block text-sm font-medium text-slate-700">I am a</label>
      <div className="mt-1 grid grid-cols-2 gap-2">
        {(["CANDIDATE", "EMPLOYER"] as Role[]).map((r) => (
          <button
            key={r}
            type="button"
            onClick={() => setRole(r)}
            className={`rounded-lg border px-3 py-2 text-sm font-medium ${
              role === r
                ? "border-brand bg-brand-light text-brand-dark"
                : "border-slate-300 text-slate-600 hover:bg-slate-50"
            }`}
          >
            {r === "CANDIDATE" ? "Job seeker" : "Employer"}
          </button>
        ))}
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}
