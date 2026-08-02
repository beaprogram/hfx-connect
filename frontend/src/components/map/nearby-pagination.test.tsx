import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NearbyPagination } from "./nearby-pagination";
import { MapSearchProvider, useMapSearch } from "@/lib/map/map-search-context";

jest.mock("next/navigation", () => ({
  usePathname: () => "/resources",
  useSearchParams: () => new URLSearchParams(),
}));

function PageProbe() {
  const { nearbyPage } = useMapSearch();
  return <p data-testid="page-probe">{nearbyPage}</p>;
}

describe("NearbyPagination", () => {
  beforeEach(() => {
    // jsdom's `window.location` persists across tests in a file; the
    // provider reads it on mount, so each test needs a clean starting URL.
    window.history.replaceState(null, "", "/resources");
  });

  it("renders nothing for a single page", () => {
    const { container } = render(
      <MapSearchProvider>
        <NearbyPagination totalPages={1} />
      </MapSearchProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("disables Previous on the first page and Next on the last page", () => {
    render(
      <MapSearchProvider>
        <NearbyPagination totalPages={3} />
      </MapSearchProvider>,
    );

    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next page" })).toBeEnabled();
    expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();
  });

  it("advances the shared nearbyPage state on Next, client-side (no page in the URL)", async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, "", "/resources");
    render(
      <MapSearchProvider>
        <NearbyPagination totalPages={3} />
        <PageProbe />
      </MapSearchProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Next page" }));

    expect(screen.getByTestId("page-probe")).toHaveTextContent("1");
    expect(window.location.search).not.toContain("page");
  });
});
