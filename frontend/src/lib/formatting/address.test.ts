import { addressLines } from "./address";

describe("addressLines", () => {
  it("renders every present line", () => {
    expect(
      addressLines({
        addressLine1: "5381 Spring Garden Rd",
        addressLine2: "Suite 200",
        city: "Halifax",
        province: "NS",
        postalCode: "B3J 2K9",
      }),
    ).toEqual(["5381 Spring Garden Rd", "Suite 200", "Halifax, NS B3J 2K9"]);
  });

  it("omits addressLine2 when absent", () => {
    expect(
      addressLines({ addressLine1: "5381 Spring Garden Rd", addressLine2: null, city: "Halifax", province: "NS", postalCode: null }),
    ).toEqual(["5381 Spring Garden Rd", "Halifax, NS"]);
  });

  it("returns an empty array when no address fields are present", () => {
    expect(addressLines({})).toEqual([]);
  });

  it("renders just the postal code line when only it is present", () => {
    expect(addressLines({ postalCode: "B3J 2K9" })).toEqual(["B3J 2K9"]);
  });
});
