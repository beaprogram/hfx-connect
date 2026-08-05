import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { ResourceDetail } from "./resource-detail";
import { useAuth } from "@/lib/auth/auth-provider";
import type { ResourceResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/auth/auth-provider");
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

// The detail page's save control (Milestone 8A) needs a mocked AuthProvider
// and a real QueryClientProvider ancestor.
function renderWithQueryClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const fullResource: ResourceResponse = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Halifax Central Library",
  slug: "halifax-central-library",
  description: "A full-service public library with free WiFi and study rooms.",
  addressLine1: "5381 Spring Garden Rd",
  addressLine2: null,
  city: "Halifax",
  province: "NS",
  postalCode: "B3J 2K9",
  phone: "9025550100",
  email: "info@example.org",
  websiteUrl: "https://example.org",
  costType: "FREE",
  costDetails: "Free with a library card.",
  eligibility: "Open to the public.",
  verificationStatus: "VERIFIED",
  active: true,
  category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
  createdAt: "2026-07-15T00:00:00Z",
  updatedAt: "2026-07-15T00:00:00Z",
  hours: {
    timezone: "America/Halifax",
    weeklyHours: [
      { dayOfWeek: "MONDAY", closed: false, opensAt: "09:00:00", closesAt: "17:00:00", overnight: false },
      { dayOfWeek: "TUESDAY", closed: true, opensAt: null, closesAt: null, overnight: false },
      { dayOfWeek: "FRIDAY", closed: false, opensAt: "22:00:00", closesAt: "02:00:00", overnight: true },
    ],
    hoursStatus: "OPEN",
    openNow: true,
  },
};

describe("ResourceDetail", () => {
  beforeEach(() => {
    mockUseAuth.mockReturnValue({
      state: { status: "unauthenticated" },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn(),
    });
  });

  it("renders the resource name as the only h1", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Halifax Central Library");
  });

  it("links to the correction-report form for this resource's slug", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    expect(screen.getByRole("link", { name: "Report incorrect information" })).toHaveAttribute(
      "href",
      "/resources/halifax-central-library/report",
    );
  });

  it("links the category to the filtered resource list", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    expect(screen.getByRole("link", { name: "Study Spaces" })).toHaveAttribute("href", "/resources?categoryId=1");
  });

  it("uses tel:, mailto:, and a safe external link for contact fields", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    expect(screen.getByRole("link", { name: "9025550100" })).toHaveAttribute("href", "tel:9025550100");
    expect(screen.getByRole("link", { name: "info@example.org" })).toHaveAttribute("href", "mailto:info@example.org");

    const websiteLink = screen.getByRole("link", { name: /^https:\/\/example\.org/ });
    expect(websiteLink).toHaveAttribute("href", "https://example.org");
    expect(websiteLink).toHaveAttribute("target", "_blank");
    expect(websiteLink).toHaveAttribute("rel", expect.stringContaining("noopener"));
  });

  it("renders the address on separate lines", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    expect(screen.getByText("5381 Spring Garden Rd")).toBeInTheDocument();
    expect(screen.getByText("Halifax, NS B3J 2K9")).toBeInTheDocument();
  });

  it("shows verified status and eligibility when present", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    expect(screen.getByText(/Verified/)).toBeInTheDocument();
    expect(screen.getByText("Open to the public.")).toBeInTheDocument();
  });

  it("omits Contact, Eligibility, and Location sections entirely when the backend returns no data for them", () => {
    const minimalResource: ResourceResponse = {
      ...fullResource,
      addressLine1: null,
      addressLine2: null,
      city: null,
      province: null,
      postalCode: null,
      phone: null,
      email: null,
      websiteUrl: null,
      eligibility: null,
    };

    renderWithQueryClient(<ResourceDetail resource={minimalResource} />);

    expect(screen.queryByRole("heading", { name: "Contact" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Eligibility" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Location" })).not.toBeInTheDocument();
  });

  it("never renders an 'Accessibility' section, since the backend has no such field", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    expect(screen.queryByText(/Accessibility/i)).not.toBeInTheDocument();
  });

  it("shows the current open status badge", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    expect(screen.getByText("Open now")).toBeInTheDocument();
  });

  it("lists the weekly schedule Monday through Sunday, in order", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    const dayLabels = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
    let previous: Element | null = null;
    for (const label of dayLabels) {
      const element = screen.getByText(label);
      if (previous) {
        expect(previous.compareDocumentPosition(element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      }
      previous = element;
    }
  });

  it("shows 'Closed' for an explicitly closed day", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    const tuesdayRow = screen.getByText("Tuesday").closest("div");
    expect(tuesdayRow).toHaveTextContent("Closed");
  });

  it("shows 'Hours unavailable' for a day with no schedule entry, distinct from Closed", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    const wednesdayRow = screen.getByText("Wednesday").closest("div");
    expect(wednesdayRow).toHaveTextContent("Hours unavailable");
  });

  it("formats an ordinary interval as readable 12-hour times", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    const mondayRow = screen.getByText("Monday").closest("div");
    expect(mondayRow).toHaveTextContent("9:00 AM");
    expect(mondayRow).toHaveTextContent("5:00 PM");
  });

  it("clearly marks an overnight interval that crosses midnight", () => {
    renderWithQueryClient(<ResourceDetail resource={fullResource} />);

    const fridayRow = screen.getByText("Friday").closest("div");
    expect(fridayRow).toHaveTextContent("10:00 PM");
    expect(fridayRow).toHaveTextContent("2:00 AM");
    expect(fridayRow).toHaveTextContent(/continues past midnight/i);
  });

  it("shows a plain 'no hours added yet' message instead of a fabricated schedule when hoursStatus is UNKNOWN", () => {
    const noScheduleResource: ResourceResponse = {
      ...fullResource,
      hours: { timezone: "America/Halifax", weeklyHours: [], hoursStatus: "UNKNOWN", openNow: null },
    };

    renderWithQueryClient(<ResourceDetail resource={noScheduleResource} />);

    expect(screen.getByText(/Hours have not been added/i)).toBeInTheDocument();
    expect(screen.queryByText("Monday")).not.toBeInTheDocument();
  });
});
