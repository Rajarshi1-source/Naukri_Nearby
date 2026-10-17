import Link from "next/link";
import { FileText, MapPin, MessageCircle, Phone, ShieldCheck, WifiOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getServerDictionary } from "@/i18n/server";

export default async function HomePage() {
  const { t } = await getServerDictionary();
  const features = [
    { icon: <MapPin className="size-5" />, title: t.home.searchTitle, body: t.home.searchBody },
    { icon: <FileText className="size-5" />, title: t.home.resumeTitle, body: t.home.resumeBody },
    { icon: <MessageCircle className="size-5" />, title: t.home.alertsTitle, body: t.home.alertsBody },
    { icon: <Phone className="size-5" />, title: t.home.noAppTitle, body: t.home.noAppBody },
    { icon: <ShieldCheck className="size-5" />, title: t.home.trustTitle, body: t.home.trustBody },
    { icon: <WifiOff className="size-5" />, title: t.home.offlineTitle, body: t.home.offlineBody },
  ];

  return (
    <div className="flex flex-col items-center gap-10 py-10">
      <section className="max-w-2xl text-center">
        <h1 className="text-4xl font-bold tracking-tight text-foreground sm:text-5xl">{t.home.heading}</h1>
        <p className="mt-4 text-lg leading-relaxed text-muted-foreground">{t.home.intro}</p>
        <div className="mt-8 flex flex-wrap justify-center gap-4">
          <Button asChild size="lg" className="h-auto min-h-10">
            <Link href="/jobs">{t.home.findJobs}</Link>
          </Button>
          <Button asChild size="lg" variant="outline" className="h-auto min-h-10">
            <Link href="/login">{t.home.login}</Link>
          </Button>
        </div>
      </section>

      <section className="grid w-full max-w-5xl gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {features.map((f) => (
          <Feature key={f.title} icon={f.icon} title={f.title} body={f.body} />
        ))}
      </section>
    </div>
  );
}

function Feature({ icon, title, body }: { icon: React.ReactNode; title: string; body: string }) {
  return (
    <Card>
      <CardHeader>
        <span className="flex size-9 items-center justify-center rounded-md bg-brand-light text-brand-dark" aria-hidden>
          {icon}
        </span>
        <CardTitle className="text-brand">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-sm leading-relaxed text-muted-foreground">{body}</p>
      </CardContent>
    </Card>
  );
}
