"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";

export default function Header() {
  const { isAuthenticated, role, user, logout, loading } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <header className="sticky top-0 z-[1000] border-b bg-background/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
        <Link href="/" className="flex items-center gap-2 font-bold text-brand">
          <span className="text-lg">NaukriNearby</span>
          <span className="hidden text-xs font-normal text-muted-foreground sm:inline">
            Hyperlocal jobs for Bharat
          </span>
        </Link>
        <nav className="flex items-center gap-4 text-sm">
          <Link
            href="/jobs"
            className="text-foreground/80 transition-colors hover:text-brand"
          >
            Find jobs
          </Link>
          {!loading && isAuthenticated && role === "CANDIDATE" && (
            <Link
              href="/candidate/resume"
              className="text-foreground/80 transition-colors hover:text-brand"
            >
              My resume
            </Link>
          )}
          {!loading && isAuthenticated ? (
            <div className="flex items-center gap-3">
              <span className="hidden text-muted-foreground sm:inline">
                {user?.name || user?.phone}
              </span>
              <Button variant="outline" size="sm" onClick={handleLogout}>
                Logout
              </Button>
            </div>
          ) : (
            <Button asChild size="sm">
              <Link href="/login">Login</Link>
            </Button>
          )}
        </nav>
      </div>
    </header>
  );
}
