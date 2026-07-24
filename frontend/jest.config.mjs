import nextJest from "next/jest.js";

// Plain ESM here, not TypeScript: the TS config loader this project's Jest
// version uses resolves next/jest.js's CommonJS default export incorrectly
// (the imported binding ends up as a non-callable module object rather than
// the exported function) on newer Node runtimes, while a native ESM import —
// as verified directly against the installed next/jest.js — resolves it
// correctly. See docs/architecture/frontend-architecture.md.

// No `dir` option: passing one makes next/jest asynchronously load
// next.config.js and install SWC bindings before building the Jest config,
// which hangs indefinitely in this environment (reproduced directly against
// the installed next/jest.js, independent of anything in this project's own
// code). `dir` is optional — without it, next/jest still supplies its SWC
// test transform and CSS/image/font mocks, which is all this project's tests
// need; the "@/*" path alias is configured manually below instead of via the
// automatic tsconfig.json lookup `dir` would have enabled.
const createJestConfig = nextJest();

/** @type {import("jest").Config} */
const config = {
  coverageProvider: "v8",
  testEnvironment: "jsdom",
  setupFilesAfterEnv: ["<rootDir>/jest.setup.ts"],
  moduleNameMapper: {
    "^@/(.*)$": "<rootDir>/src/$1",
  },
};

export default createJestConfig(config);
