import type { Metadata } from "next";

import Header from "@/components/Header";
import { Toaster } from "@/components/ui/sonner";
import { AuthProvider } from "@/lib/auth";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "NaukriNearby — Hyperlocal jobs for Bharat",
    template: "%s | NaukriNearby",
  },
  description:
    "Find jobs near you in India's Tier-2/3 cities. AI-parsed resumes, hyperlocal search, WhatsApp alerts in your language.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <AuthProvider>
            <Header />
            <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
            <Toaster richColors position="top-center" />
          </AuthProvider>
        </Providers>
      </body>
    </html>
  );
}
