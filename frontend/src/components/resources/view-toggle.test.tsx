import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ViewToggle } from "./view-toggle";
import { MapSearchProvider } from "@/lib/map/map-search-context";

jest.mock("next/navigation", () => ({
  usePathname: () => "/resources",
  useSearchParams: () => new URLSearchParams(),
}));

describe("ViewToggle", () => {
  beforeEach(() => {
    // jsdom's `window.location` persists across tests in a file; the
    // provider reads it on mount, so each test needs a clean starting URL.
    window.history.replaceState(null, "", "/resources");
  });

  it("renders List and Map controls with List selected by default", () => {
    render(
      <MapSearchProvider>
        <ViewToggle />
      </MapSearchProvider>,
    );

    expect(screen.getByRole("button", { name: "List" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Map" })).toHaveAttribute("aria-pressed", "false");
  });

  it("communicates the selected state through aria-pressed, not colour alone", async () => {
    const user = userEvent.setup();
    render(
      <MapSearchProvider>
        <ViewToggle />
      </MapSearchProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Map" }));

    expect(screen.getByRole("button", { name: "Map" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "List" })).toHaveAttribute("aria-pressed", "false");
  });

  it("is keyboard operable", async () => {
    const user = userEvent.setup();
    render(
      <MapSearchProvider>
        <ViewToggle />
      </MapSearchProvider>,
    );

    await user.tab();
    expect(screen.getByRole("button", { name: "List" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "Map" })).toHaveFocus();
    await user.keyboard("{Enter}");

    expect(screen.getByRole("button", { name: "Map" })).toHaveAttribute("aria-pressed", "true");
  });
});
