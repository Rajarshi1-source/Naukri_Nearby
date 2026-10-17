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
import { fmt } from "@/i18n";
import { useT } from "@/i18n/I18nProvider";

type Step = "phone" | "otp";

const HOME_BY_ROLE: Record<string, string> = {
  CANDIDATE: "/jobs",
  EMPLOYER: "/employer",
  ADMIN: "/admin/moderation",
};
type Role = "CANDIDATE" | "EMPLOYER";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { login } = useAuth();
  const t = useT();

  const [step, setStep] = useState<Step>("phone");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [role, setRole] = useState<Role>("CANDIDATE");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Only same-site paths: "/x" but never "//host" or "https://…" (open-redirect guard).
  const next = params.get("next");
  const safeNext = next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : null;

  async function sendOtp(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await authService.sendOtp(phone);
      setStep("otp");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t.login.sendFailed);
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
      router.push(safeNext ?? HOME_BY_ROLE[auth.role] ?? "/jobs");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t.login.invalid);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="mx-auto mt-10 max-w-md">
      <CardHeader>
        <CardTitle className="text-2xl">
          {step === "phone" ? t.login.titlePhone : t.login.titleOtp}
        </CardTitle>
        <CardDescription>
          {step === "phone" ? t.login.descPhone : fmt(t.login.descOtp, { phone })}
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
              <Label htmlFor="phone">{t.login.phone}</Label>
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
              {busy ? t.login.sending : t.login.send}
            </Button>
          </form>
        ) : (
          <form onSubmit={verifyOtp} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="code">{t.login.otp}</Label>
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
              {busy ? t.login.verifying : t.login.verify}
            </Button>
            <Button
              type="button"
              variant="ghost"
              className="w-full"
              onClick={() => setStep("phone")}
            >
              {t.login.changePhone}
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
  const t = useT();
  return (
    <div className="space-y-1.5">
      <Label>{t.login.iAm}</Label>
      <div className="grid grid-cols-2 gap-2">
        {(["CANDIDATE", "EMPLOYER"] as Role[]).map((r) => (
          <Button
            key={r}
            type="button"
            variant="outline"
            aria-pressed={role === r}
            onClick={() => setRole(r)}
            className={cn(
              role === r && "border-brand bg-brand-light text-brand-dark",
            )}
          >
            {r === "CANDIDATE" ? t.login.jobSeeker : t.login.employer}
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
