export function SiteFooter() {
  return (
    <footer className="border-t border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl flex-col gap-2 px-4 py-6 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <p>&copy; {new Date().getFullYear()} HFX Connect. A student portfolio project.</p>
        <a
          href="https://github.com/beaprogram/hfx-connect"
          className="rounded-sm font-medium text-slate-700 underline underline-offset-2 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          View source on GitHub
        </a>
      </div>
    </footer>
  );
}
