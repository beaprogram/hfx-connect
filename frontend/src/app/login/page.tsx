import { Suspense } from "react";
import type { Metadata } from "next";
import { LoginForm } from "@/components/auth/login-form";

export const metadata: Metadata = {
  title: "Log in — HFX Connect",
};

export default function LoginPage() {
  // LoginForm reads useSearchParams() (Milestone 8A's ?returnTo= — see
  // lib/auth/return-to.ts), which Next.js requires a Suspense boundary
  // around; this route has no meaningful fallback UI to show since the
  // form itself renders instantly regardless of the search params.
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
