/** A small, section-scoped failure message — see docs/wireframes/states.md. Never renders technical detail. */
export function InlineError({ message }: { message: string }) {
  return (
    <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      {message}
    </p>
  );
}
