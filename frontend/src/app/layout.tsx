import type { Metadata, Viewport } from "next";

import Header from "@/components/Header";
import OfflineBanner from "@/components/OfflineBanner";
import OfflineSync from "@/components/OfflineSync";
import ServiceWorkerRegistrar from "@/components/ServiceWorkerRegistrar";
import { Toaster } from "@/components/ui/sonner";
import { I18nProvider } from "@/i18n/I18nProvider";
import { getServerDictionary } from "@/i18n/server";
import { AuthProvider } from "@/lib/auth";
import { Providers } from "./providers";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const { t } = await getServerDictionary();
  return {
    title: {
      default: t.meta.title,
      template: "%s | NaukriNearby",
    },
    description: t.meta.description,
    applicationName: "NaukriNearby",
    appleWebApp: { capable: true, title: "NaukriNearby", statusBarStyle: "default" },
    icons: {
      icon: "/icons/icon-192.png",
      apple: "/icons/apple-touch-icon.png",
    },
  };
}

export const viewport: Viewport = {
  themeColor: "#2f7d6d",
  width: "device-width",
  initialScale: 1,
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { locale } = await getServerDictionary();
  return (
    <html lang={locale === "hi" ? "hi-IN" : "en-IN"}>
      <body>
        <I18nProvider locale={locale}>
          <Providers>
            <AuthProvider>
              <Header />
              <OfflineBanner />
              <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
              <Toaster richColors position="top-center" />
              <OfflineSync />
              <ServiceWorkerRegistrar />
            </AuthProvider>
          </Providers>
        </I18nProvider>
      </body>
    </html>
  );
}
