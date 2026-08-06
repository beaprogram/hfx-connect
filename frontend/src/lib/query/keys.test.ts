import { savedResourceKeys, resourceSubmissionKeys, correctionReportKeys, moderationKeys } from "./keys";

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

describe("resourceSubmissionKeys", () => {
  it("roots every key in the given userId", () => {
    expect(resourceSubmissionKeys.all("user-1")).toEqual(["resource-submissions", "user-1"]);
    expect(resourceSubmissionKeys.list("user-1", { page: 0, size: 20 })[1]).toBe("user-1");
    expect(resourceSubmissionKeys.detail("user-1", "sub-1")[1]).toBe("user-1");
  });

  it("never uses the same key for two different accounts", () => {
    const keyA = resourceSubmissionKeys.list("user-1", { page: 0, size: 20 });
    const keyB = resourceSubmissionKeys.list("user-2", { page: 0, size: 20 });
    expect(keyA).not.toEqual(keyB);
  });

  it("distinguishes different submission ids", () => {
    const keyA = resourceSubmissionKeys.detail("user-1", "sub-1");
    const keyB = resourceSubmissionKeys.detail("user-1", "sub-2");
    expect(keyA).not.toEqual(keyB);
  });
});

describe("correctionReportKeys", () => {
  it("roots every key in the given userId", () => {
    expect(correctionReportKeys.all("user-1")).toEqual(["correction-reports", "user-1"]);
    expect(correctionReportKeys.list("user-1", { page: 0, size: 20 })[1]).toBe("user-1");
    expect(correctionReportKeys.detail("user-1", "report-1")[1]).toBe("user-1");
  });

  it("never uses the same key for two different accounts", () => {
    const keyA = correctionReportKeys.list("user-1", { page: 0, size: 20 });
    const keyB = correctionReportKeys.list("user-2", { page: 0, size: 20 });
    expect(keyA).not.toEqual(keyB);
  });

  it("distinguishes different report ids", () => {
    const keyA = correctionReportKeys.detail("user-1", "report-1");
    const keyB = correctionReportKeys.detail("user-1", "report-2");
    expect(keyA).not.toEqual(keyB);
  });
});

describe("moderationKeys", () => {
  it("roots every key in a fixed 'moderation' prefix, not a userId", () => {
    expect(moderationKeys.submissionQueue({ page: 0, size: 20 })[0]).toBe("moderation");
    expect(moderationKeys.correctionQueue({ page: 0, size: 20 })[0]).toBe("moderation");
    expect(moderationKeys.globalAudit({ page: 0, size: 20 })[0]).toBe("moderation");
  });

  it("distinguishes different submission detail ids", () => {
    const keyA = moderationKeys.submissionDetail("sub-1");
    const keyB = moderationKeys.submissionDetail("sub-2");
    expect(keyA).not.toEqual(keyB);
  });

  it("distinguishes different correction detail ids", () => {
    const keyA = moderationKeys.correctionDetail("report-1");
    const keyB = moderationKeys.correctionDetail("report-2");
    expect(keyA).not.toEqual(keyB);
  });

  it("distinguishes different queue filter params", () => {
    const keyA = moderationKeys.submissionQueue({ status: "PENDING_REVIEW", page: 0, size: 20 });
    const keyB = moderationKeys.submissionQueue({ status: "APPROVED", page: 0, size: 20 });
    expect(keyA).not.toEqual(keyB);
  });
});
