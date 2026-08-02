import { formatDistanceAway } from "./distance";

describe("formatDistanceAway", () => {
  it("formats sub-kilometre distances in whole metres", () => {
    expect(formatDistanceAway(0)).toBe("0 m away");
    expect(formatDistanceAway(850.4)).toBe("850 m away");
    expect(formatDistanceAway(999)).toBe("999 m away");
  });

  it("formats 1 km and above in kilometres with one decimal place", () => {
    expect(formatDistanceAway(1000)).toBe("1.0 km away");
    expect(formatDistanceAway(1850)).toBe("1.9 km away");
    expect(formatDistanceAway(50_000)).toBe("50.0 km away");
  });

  it("never claims route distance or travel time", () => {
    expect(formatDistanceAway(1500)).not.toMatch(/walk|drive|minute|hour/i);
  });
});
