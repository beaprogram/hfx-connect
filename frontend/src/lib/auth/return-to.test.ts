import { isSafeReturnPath, resolveReturnPath, buildLoginHref } from "./return-to";

describe("isSafeReturnPath", () => {
  it("accepts a plain same-origin relative path", () => {
    expect(isSafeReturnPath("/resources/halifax-central-library")).toBe(true);
    expect(isSafeReturnPath("/dashboard")).toBe(true);
    expect(isSafeReturnPath("/resources?categoryId=3")).toBe(true);
  });

  it("rejects a missing or empty value", () => {
    expect(isSafeReturnPath(null)).toBe(false);
    expect(isSafeReturnPath(undefined)).toBe(false);
    expect(isSafeReturnPath("")).toBe(false);
  });

  it("rejects a value that doesn't start with a single leading slash", () => {
    expect(isSafeReturnPath("dashboard")).toBe(false);
    expect(isSafeReturnPath("resources/halifax-central-library")).toBe(false);
  });

  it("rejects a protocol-relative URL", () => {
    expect(isSafeReturnPath("//evil.example.org")).toBe(false);
    expect(isSafeReturnPath("//evil.example.org/dashboard")).toBe(false);
  });

  it("rejects a backslash trick some browsers normalize to protocol-relative", () => {
    expect(isSafeReturnPath("/\\evil.example.org")).toBe(false);
  });

  it("rejects an absolute URL to another origin", () => {
    expect(isSafeReturnPath("https://evil.example.org")).toBe(false);
    expect(isSafeReturnPath("http://evil.example.org/dashboard")).toBe(false);
  });
});

describe("resolveReturnPath", () => {
  it("returns the value when it is a safe path", () => {
    expect(resolveReturnPath("/resources/halifax-central-library", "/dashboard")).toBe(
      "/resources/halifax-central-library",
    );
  });

  it("returns the fallback when the value is missing or unsafe", () => {
    expect(resolveReturnPath(null, "/dashboard")).toBe("/dashboard");
    expect(resolveReturnPath("//evil.example.org", "/dashboard")).toBe("/dashboard");
  });
});

describe("buildLoginHref", () => {
  it("includes an encoded returnTo for a safe path", () => {
    expect(buildLoginHref("/resources/halifax-central-library")).toBe(
      "/login?returnTo=%2Fresources%2Fhalifax-central-library",
    );
  });

  it("omits returnTo entirely for an unsafe path", () => {
    expect(buildLoginHref("https://evil.example.org")).toBe("/login");
  });
});
