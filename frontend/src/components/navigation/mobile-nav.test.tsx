import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { MobileNav } from "./mobile-nav";

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn() }),
}));

function renderMobileNav() {
  // AuthProvider reads useQueryClient() (Milestone 8A), so it needs a real
  // QueryClientProvider ancestor, same as any other TanStack-Query consumer.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MobileNav />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe("MobileNav", () => {
  it("is closed by default and the panel is not in the document", () => {
    renderMobileNav();

    expect(screen.getByRole("button", { name: "Open menu" })).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("navigation", { name: "Mobile" })).not.toBeInTheDocument();
  });

  it("opens the panel, moves focus to the first link, and shows Close menu", async () => {
    const user = userEvent.setup();
    renderMobileNav();

    await user.click(screen.getByRole("button", { name: "Open menu" }));

    const link = screen.getByRole("link", { name: "Browse resources" });
    expect(link).toBeInTheDocument();
    expect(link).toHaveFocus();
    expect(screen.getByRole("button", { name: "Close menu" })).toHaveAttribute("aria-expanded", "true");
  });

  it("closes on Escape and returns focus to the toggle button", async () => {
    const user = userEvent.setup();
    renderMobileNav();

    const toggle = screen.getByRole("button", { name: "Open menu" });
    await user.click(toggle);
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("link", { name: "Browse resources" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Open menu" })).toHaveFocus();
  });

  it("closes when a link inside the panel is clicked", async () => {
    const user = userEvent.setup();
    renderMobileNav();

    await user.click(screen.getByRole("button", { name: "Open menu" }));
    await user.click(screen.getByRole("link", { name: "Browse resources" }));

    expect(screen.queryByRole("link", { name: "Browse resources" })).not.toBeInTheDocument();
  });
});
