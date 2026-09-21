/**
 * Tests for resolveHttpTimeout — NaN-safe axios timeout resolution from an env value.
 * Fix #7 (PR #2018): a blank/garbage HTTP_TIMEOUT_MS must NOT become 0 (disabled) or NaN (hang).
 */
import { resolveHttpTimeout } from "../utils/httpTimeout";

describe("resolveHttpTimeout", () => {
    describe("falls back to the default", () => {
        it("returns 300000 when the env var is undefined (unset)", () => {
            expect(resolveHttpTimeout(undefined)).toBe(300_000);
        });

        it("returns 300000 for an empty string (the k8s configmap footgun)", () => {
            expect(resolveHttpTimeout("")).toBe(300_000);
        });

        it("returns 300000 for whitespace only", () => {
            expect(resolveHttpTimeout("   ")).toBe(300_000);
        });

        it("returns 300000 for a non-numeric value", () => {
            expect(resolveHttpTimeout("abc")).toBe(300_000);
        });
    });

    describe("honours explicit values", () => {
        it("returns 0 for an explicit \"0\" (timeout disabled, as documented)", () => {
            expect(resolveHttpTimeout("0")).toBe(0);
        });

        it("returns the parsed number for a valid numeric string", () => {
            expect(resolveHttpTimeout("60000")).toBe(60_000);
        });

        it("parses a leading-numeric string the same way parseInt does", () => {
            expect(resolveHttpTimeout("45000ms")).toBe(45_000);
        });
    });

    describe("custom default", () => {
        it("uses the provided default when the value is invalid", () => {
            expect(resolveHttpTimeout("", 120_000)).toBe(120_000);
        });
    });
});
