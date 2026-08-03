import { savedResourceKeys } from "./keys";

describe("savedResourceKeys", () => {
  it("roots every key in the given userId", () => {
    expect(savedResourceKeys.all("user-1")).toEqual(["saved-resources", "user-1"]);
    expect(savedResourceKeys.list("user-1", { page: 0, size: 20 })[1]).toBe("user-1");
    expect(savedResourceKeys.status("user-1", ["a"])[1]).toBe("user-1");
  });

  it("never uses the same key for two different accounts", () => {
    const keyA = savedResourceKeys.list("user-1", { page: 0, size: 20 });
    const keyB = savedResourceKeys.list("user-2", { page: 0, size: 20 });
    expect(keyA).not.toEqual(keyB);
  });

  it("sorts resourceIds so the same set in a different order shares one cache entry", () => {
    const keyA = savedResourceKeys.status("user-1", ["b", "a"]);
    const keyB = savedResourceKeys.status("user-1", ["a", "b"]);
    expect(keyA).toEqual(keyB);
  });

  it("distinguishes a genuinely different set of ids", () => {
    const keyA = savedResourceKeys.status("user-1", ["a"]);
    const keyB = savedResourceKeys.status("user-1", ["a", "b"]);
    expect(keyA).not.toEqual(keyB);
  });
});
