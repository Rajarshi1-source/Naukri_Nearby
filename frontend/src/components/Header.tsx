"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import LanguageSwitcher from "@/components/LanguageSwitcher";
import { Button } from "@/components/ui/button";
import { useT } from "@/i18n/I18nProvider";
import { useAuth } from "@/lib/auth";

const DASHBOARD: Record<string, string> = {
  CANDIDATE: "/candidate",
  EMPLOYER: "/employer",
  ADMIN: "/admin/moderation",
};

export default function Header() {
  const { isAuthenticated, role, user, logout, loading } = useAuth();
  const t = useT();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  const dashboard = role ? DASHBOARD[role] : undefined;

  return (
    <header className="sticky top-0 z-[1000] border-b bg-background/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-2 px-4">
        <Link href="/" className="flex items-center gap-2 font-bold text-brand">
          <span className="text-lg">NaukriNearby</span>
          <span className="hidden text-xs font-normal text-muted-foreground md:inline">
            {t.nav.tagline}
          </span>
        </Link>
        <nav className="flex items-center gap-2 text-sm sm:gap-4">
          <Link href="/jobs" className="text-foreground/80 transition-colors hover:text-brand">
            {t.nav.findJobs}
          </Link>
          {!loading && isAuthenticated && dashboard && (
            <Link href={dashboard} className="text-foreground/80 transition-colors hover:text-brand">
              {role === "ADMIN" ? t.nav.moderation : t.nav.dashboard}
            </Link>
          )}
          <LanguageSwitcher />
          {!loading && isAuthenticated ? (
            <div className="flex items-center gap-3">
              <span className="hidden text-muted-foreground lg:inline">{user?.name || user?.phone}</span>
              <Button variant="outline" size="sm" onClick={handleLogout}>
                {t.nav.logout}
              </Button>
            </div>
          ) : (
            <Button asChild size="sm">
              <Link href="/login">{t.nav.login}</Link>
            </Button>
          )}
        </nav>
      </div>
    </header>
  );
}
