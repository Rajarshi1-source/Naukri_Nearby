import Link from "next/link";

export default function HomePage() {
  return (
    <div className="flex flex-col items-center gap-10 py-10">
      <section className="max-w-2xl text-center">
        <h1 className="text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
          Jobs near you, in your language
        </h1>
        <p className="mt-4 text-lg text-slate-600">
          NaukriNearby connects workers in India&apos;s Tier-2/3 cities to nearby jobs. Search
          hyperlocally on a map, upload a resume our AI reads in Hindi or English, and apply in one tap.
        </p>
        <div className="mt-8 flex justify-center gap-4">
          <Link
            href="/jobs"
            className="rounded-lg bg-brand px-6 py-3 font-semibold text-white shadow hover:bg-brand-dark"
          >
            Find jobs near me
          </Link>
          <Link
            href="/login"
            className="rounded-lg border border-slate-300 bg-white px-6 py-3 font-semibold text-slate-700 hover:bg-slate-100"
          >
            Login / Register
          </Link>
        </div>
      </section>

      <section className="grid w-full max-w-4xl gap-4 sm:grid-cols-3">
        <Feature
          title="Hyperlocal search"
          body="Jobs within your chosen radius, ranked by distance, on an OpenStreetMap view."
        />
        <Feature
          title="AI resume parsing"
          body="Upload a PDF or a photo of your resume — we extract your skills and experience automatically."
        />
        <Feature
          title="WhatsApp alerts"
          body="Get notified about matching jobs in Hindi, Tamil, Telugu and more."
        />
      </section>
    </div>
  );
}

function Feature({ title, body }: { title: string; body: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-semibold text-brand">{title}</h3>
      <p className="mt-2 text-sm text-slate-600">{body}</p>
    </div>
  );
}
