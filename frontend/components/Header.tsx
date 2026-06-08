"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { useAuth } from "@/lib/auth";

export default function Header() {
  const { isAuthenticated, role, user, logout, loading } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <header className="sticky top-0 z-[1000] border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
        <Link href="/" className="flex items-center gap-2 font-bold text-brand">
          <span className="text-lg">NaukriNearby</span>
          <span className="hidden text-xs font-normal text-slate-500 sm:inline">
            Hyperlocal jobs for Bharat
          </span>
        </Link>
        <nav className="flex items-center gap-4 text-sm">
          <Link href="/jobs" className="text-slate-700 hover:text-brand">
            Find jobs
          </Link>
          {!loading && isAuthenticated && role === "CANDIDATE" && (
            <Link href="/candidate/resume" className="text-slate-700 hover:text-brand">
              My resume
            </Link>
          )}
          {!loading && isAuthenticated ? (
            <div className="flex items-center gap-3">
              <span className="hidden text-slate-500 sm:inline">
                {user?.name || user?.phone}
              </span>
              <button
                onClick={handleLogout}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-100"
              >
                Logout
              </button>
            </div>
          ) : (
            <Link
              href="/login"
              className="rounded-md bg-brand px-3 py-1.5 font-medium text-white hover:bg-brand-dark"
            >
              Login
            </Link>
          )}
        </nav>
      </div>
    </header>
  );
}
