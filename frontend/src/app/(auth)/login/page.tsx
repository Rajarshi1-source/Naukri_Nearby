"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { authService } from "@/services/authService";
import { cn } from "@/lib/utils";

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
      await authService.sendOtp(phone);
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
      const auth = await authService.verifyOtp(phone, code, role);
      await login(auth);
      router.push(redirectTo);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Invalid code");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="mx-auto mt-10 max-w-md">
      <CardHeader>
        <CardTitle className="text-2xl">
          {step === "phone" ? "Login or register" : "Enter the code"}
        </CardTitle>
        <CardDescription>
          {step === "phone"
            ? "We'll send a one-time password to your phone."
            : `Sent to ${phone}. (Dev: use 123456)`}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {error && (
          <div className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        )}

        {step === "phone" ? (
          <form onSubmit={sendOtp} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="phone">Phone number</Label>
              <Input
                id="phone"
                type="tel"
                required
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="9876543210"
              />
            </div>
            <RoleToggle role={role} setRole={setRole} />
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? "Sending…" : "Send OTP"}
            </Button>
          </form>
        ) : (
          <form onSubmit={verifyOtp} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="code">One-time password</Label>
              <Input
                id="code"
                inputMode="numeric"
                required
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="123456"
                className="tracking-widest"
              />
            </div>
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? "Verifying…" : "Verify & continue"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              className="w-full"
              onClick={() => setStep("phone")}
            >
              Change phone number
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

function RoleToggle({
  role,
  setRole,
}: {
  role: Role;
  setRole: (r: Role) => void;
}) {
  return (
    <div className="space-y-1.5">
      <Label>I am a</Label>
      <div className="grid grid-cols-2 gap-2">
        {(["CANDIDATE", "EMPLOYER"] as Role[]).map((r) => (
          <Button
            key={r}
            type="button"
            variant="outline"
            onClick={() => setRole(r)}
            className={cn(
              role === r && "border-brand bg-brand-light text-brand-dark",
            )}
          >
            {r === "CANDIDATE" ? "Job seeker" : "Employer"}
          </Button>
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
