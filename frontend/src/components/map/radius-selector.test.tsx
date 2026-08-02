import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RadiusSelector } from "./radius-selector";
import { MapSearchProvider, useMapSearch } from "@/lib/map/map-search-context";
import { RADIUS_OPTIONS_KM, DEFAULT_RADIUS_KM } from "@/lib/constants/map";

jest.mock("next/navigation", () => ({
  usePathname: () => "/resources",
  useSearchParams: () => new URLSearchParams(),
}));

function RadiusProbe() {
  const { radiusKm } = useMapSearch();
  return <p data-testid="radius-probe">{radiusKm}</p>;
}

describe("RadiusSelector", () => {
  beforeEach(() => {
    // jsdom's `window.location` persists across tests in a file; the
    // provider reads it on mount, so each test needs a clean starting URL.
    window.history.replaceState(null, "", "/resources");
  });

  it("is a labelled control defaulting to 5 km", () => {
    render(
      <MapSearchProvider>
        <RadiusSelector />
      </MapSearchProvider>,
    );

    const select = screen.getByLabelText("Search radius");
    expect(select).toHaveValue(String(DEFAULT_RADIUS_KM));
  });

  it("offers exactly the backend-supported radius options", () => {
    render(
      <MapSearchProvider>
        <RadiusSelector />
      </MapSearchProvider>,
    );

    const options = screen.getAllByRole("option").map((option) => option.textContent);
    RADIUS_OPTIONS_KM.forEach((km) => {
      expect(options).toContain(`Within ${km} km`);
    });
  });

  it("updates the shared radius state and is keyboard accessible", async () => {
    const user = userEvent.setup();
    render(
      <MapSearchProvider>
        <RadiusSelector />
        <RadiusProbe />
      </MapSearchProvider>,
    );

    await user.selectOptions(screen.getByLabelText("Search radius"), "25");

    expect(screen.getByTestId("radius-probe")).toHaveTextContent("25");
  });
});
