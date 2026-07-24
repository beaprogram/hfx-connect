import { formatCityProvince } from "./location";

describe("formatCityProvince", () => {
  it("joins city and province with a comma", () => {
    expect(formatCityProvince("Halifax", "NS")).toBe("Halifax, NS");
  });

  it("returns just the city when province is missing", () => {
    expect(formatCityProvince("Halifax", null)).toBe("Halifax");
  });

  it("returns just the province when city is missing", () => {
    expect(formatCityProvince(undefined, "NS")).toBe("NS");
  });

  it("returns null when both are missing", () => {
    expect(formatCityProvince(null, null)).toBeNull();
  });

  it("treats a blank string as missing", () => {
    expect(formatCityProvince("  ", "NS")).toBe("NS");
  });
});
