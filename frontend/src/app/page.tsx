export default function Home() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center px-4 py-16 sm:px-6 sm:py-24">
      <p className="text-sm font-medium uppercase tracking-wide text-blue-700">
        Building in public
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">
        Discover trustworthy community resources around Halifax
      </h1>
      <p className="mt-4 max-w-2xl text-lg leading-relaxed text-slate-600">
        HFX Connect will bring together food assistance, study spaces, employment
        support, newcomer services, recreation programs, and community events into one
        searchable, verified directory &mdash; so you do not have to piece it together
        from a dozen different websites.
      </p>
      <p className="mt-6 max-w-2xl text-base leading-relaxed text-slate-500">
        This project is under active development. Search, the resource directory, and
        the map are not live yet. Follow progress in the{" "}
        <a
          href="https://github.com/beaprogram/hfx-connect"
          className="rounded-sm font-medium text-blue-700 underline underline-offset-2 hover:text-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          public GitHub repository
        </a>
        .
      </p>
    </div>
  );
}
