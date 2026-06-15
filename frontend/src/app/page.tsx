import Link from "next/link";
import { MapPin, FileText, MessageCircle } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function HomePage() {
  return (
    <div className="flex flex-col items-center gap-10 py-10">
      <section className="max-w-2xl text-center">
        <h1 className="text-4xl font-bold tracking-tight text-foreground sm:text-5xl">
          Jobs near you, in your language
        </h1>
        <p className="mt-4 text-lg text-muted-foreground">
          NaukriNearby connects workers in India&apos;s Tier-2/3 cities to
          nearby jobs. Search hyperlocally on a map, upload a resume our AI
          reads in Hindi or English, and apply in one tap.
        </p>
        <div className="mt-8 flex justify-center gap-4">
          <Button asChild size="lg">
            <Link href="/jobs">Find jobs near me</Link>
          </Button>
          <Button asChild size="lg" variant="outline">
            <Link href="/login">Login / Register</Link>
          </Button>
        </div>
      </section>

      <section className="grid w-full max-w-4xl gap-4 sm:grid-cols-3">
        <Feature
          icon={<MapPin className="size-5" />}
          title="Hyperlocal search"
          body="Jobs within your chosen radius, ranked by distance, on an OpenStreetMap view."
        />
        <Feature
          icon={<FileText className="size-5" />}
          title="AI resume parsing"
          body="Upload a PDF or a photo of your resume — we extract your skills and experience automatically."
        />
        <Feature
          icon={<MessageCircle className="size-5" />}
          title="WhatsApp alerts"
          body="Get notified about matching jobs in Hindi, Tamil, Telugu and more."
        />
      </section>
    </div>
  );
}

function Feature({
  icon,
  title,
  body,
}: {
  icon: React.ReactNode;
  title: string;
  body: string;
}) {
  return (
    <Card>
      <CardHeader>
        <span className="flex size-9 items-center justify-center rounded-md bg-brand-light text-brand-dark">
          {icon}
        </span>
        <CardTitle className="text-brand">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">{body}</p>
      </CardContent>
    </Card>
  );
}
