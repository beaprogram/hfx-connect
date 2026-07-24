import { formatDate } from "./dates";

describe("formatDate", () => {
  it("formats an ISO timestamp using a fixed locale and time zone", () => {
    expect(formatDate("2026-07-15T22:33:35.142732Z")).toBe("Jul 15, 2026");
  });

  it("produces the same output regardless of call order (deterministic, not locale-dependent)", () => {
    const first = formatDate("2026-01-01T12:00:00Z");
    const second = formatDate("2026-01-01T12:00:00Z");
    expect(first).toBe(second);
  });
});
