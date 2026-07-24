import type { ReactNode } from "react";

export type BadgeTone = "neutral" | "positive" | "info";

const TONE_CLASSES: Record<BadgeTone, string> = {
  neutral: "bg-slate-100 text-slate-700",
  positive: "bg-green-100 text-green-800",
  info: "bg-blue-100 text-blue-800",
};

/**
 * A small text-and-icon status label. Colour is always paired with a visible
 * text label (never colour alone) — see docs/wireframes/states.md.
 */
export function Badge({ children, tone }: { children: ReactNode; tone: BadgeTone }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}>
      {children}
    </span>
  );
}
