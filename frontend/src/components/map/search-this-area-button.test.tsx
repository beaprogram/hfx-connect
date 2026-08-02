import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SearchThisAreaButton } from "./search-this-area-button";
import { MapSearchProvider, useMapSearch } from "@/lib/map/map-search-context";

jest.mock("next/navigation", () => ({
  usePathname: () => "/resources",
  useSearchParams: () => new URLSearchParams(),
}));

/** Test-only harness exposing the context actions a real map drag/"Use my location" would call. */
function Harness() {
  const { setView, setPendingCentre, centre } = useMapSearch();
  return (
    <>
      <button onClick={() => setView("map")}>activate-map</button>
      <button
        onClick={() => centre && setPendingCentre({ latitude: centre.latitude + 0.01, longitude: centre.longitude })}
      >
        simulate-real-move
      </button>
      <button onClick={() => centre && setPendingCentre({ latitude: centre.latitude + 0.0000001, longitude: centre.longitude })}>
        simulate-jitter
      </button>
      <SearchThisAreaButton />
    </>
  );
}

function renderHarness() {
  return render(
    <MapSearchProvider>
      <Harness />
    </MapSearchProvider>,
  );
}

describe("SearchThisAreaButton", () => {
  beforeEach(() => {
    // jsdom's `window.location` persists across tests in a file; the
    // provider reads it on mount, so each test needs a clean starting URL.
    window.history.replaceState(null, "", "/resources");
  });

  it("is not rendered when the map hasn't moved", async () => {
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByText("activate-map"));

    expect(screen.queryByRole("button", { name: "Search this area" })).not.toBeInTheDocument();
  });

  it("stays hidden for sub-threshold jitter (e.g. a programmatic recentre)", async () => {
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByText("activate-map"));
    await user.click(screen.getByText("simulate-jitter"));

    expect(screen.queryByRole("button", { name: "Search this area" })).not.toBeInTheDocument();
  });

  it("appears after a real move, is keyboard accessible, and disappears once activated", async () => {
    const user = userEvent.setup();
    renderHarness();
    await user.click(screen.getByText("activate-map"));
    await user.click(screen.getByText("simulate-real-move"));

    const button = await screen.findByRole("button", { name: "Search this area" });
    button.focus();
    expect(button).toHaveFocus();

    await user.keyboard("{Enter}");

    expect(screen.queryByRole("button", { name: "Search this area" })).not.toBeInTheDocument();
  });
});
