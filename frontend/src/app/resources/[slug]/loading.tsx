export default function Loading() {
  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-12 sm:px-6" aria-hidden="true">
      <div className="h-4 w-40 rounded bg-slate-100" />
      <div className="mt-4 h-8 w-2/3 rounded bg-slate-200" />
      <div className="mt-3 flex gap-2">
        <div className="h-5 w-24 rounded-full bg-slate-100" />
        <div className="h-5 w-20 rounded-full bg-slate-100" />
      </div>
      <div className="mt-8 h-4 w-full rounded bg-slate-100" />
      <div className="mt-2 h-4 w-5/6 rounded bg-slate-100" />
      <div className="mt-2 h-4 w-2/3 rounded bg-slate-100" />
    </div>
  );
}
