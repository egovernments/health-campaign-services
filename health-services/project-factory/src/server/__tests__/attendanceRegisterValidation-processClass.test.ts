/**
 * Tests for applyErrors in attendanceRegisterValidation-processClass.ts
 * Fix #6 (PR #2018): error.row - 3 must never crash on sheetData[row] for an out-of-range row;
 * every error is still recorded in additionalDetails.sheetErrors.
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────
jest.mock('../config', () => ({ default: { host: {}, paths: {}, kafka: {} }, __esModule: true }));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('../utils/campaignUtils', () => ({
    getLocalizedName: jest.fn((key: string) => key),
}));

jest.mock('../utils/genericUtils', () => ({ throwError: jest.fn() }));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

jest.mock('../validators/campaignValidators', () => ({
    validateDatasWithSchema: jest.fn(),
}));

import { TemplateClass } from "../processFlowClasses/attendanceRegisterValidation-processClass";
import { sheetDataRowStatuses } from "../config/constants";

function applyErrors(sheetData: any[], errors: any[], resourceDetails: any): void {
    (TemplateClass as any).applyErrors(sheetData, errors, resourceDetails);
}

// rows are 0-indexed in sheetData; a sheet error for sheetData[i] carries row = i + 3
const newSheet = (n: number): any[] => Array.from({ length: n }, (_, i) => ({ id: i }));

describe("applyErrors", () => {
    describe("valid in-range errors", () => {
        it("annotates the mapped row and marks it INVALID", () => {
            const sheetData = newSheet(3);
            const resourceDetails: any = { additionalDetails: {} };
            applyErrors(sheetData, [{ row: 3, message: "bad value" }], resourceDetails); // row 3 → index 0
            expect(sheetData[0]["#errorDetails#"]).toBe("bad value");
            expect(sheetData[0]["#status#"]).toBe(sheetDataRowStatuses.INVALID);
        });

        it("appends to an existing error message with a separator", () => {
            const sheetData: any[] = [{ "#errorDetails#": "first error" }];
            applyErrors(sheetData, [{ row: 3, message: "second error" }], { additionalDetails: {} });
            expect(sheetData[0]["#errorDetails#"]).toBe("first error. second error");
        });

        it("records all errors in additionalDetails.sheetErrors", () => {
            const resourceDetails: any = { additionalDetails: {} };
            const errors = [{ row: 3, message: "e1" }];
            applyErrors(newSheet(1), errors, resourceDetails);
            expect(resourceDetails.additionalDetails.sheetErrors).toEqual(errors);
        });
    });

    describe("out-of-range rows (Fix #6 — must not crash)", () => {
        it("does not throw when row maps to a negative index (row < 3)", () => {
            const sheetData = newSheet(2);
            expect(() => applyErrors(sheetData, [
                { row: 0, message: "x" }, // -3
                { row: 1, message: "y" }, // -2
                { row: 2, message: "z" }, // -1
            ], { additionalDetails: {} })).not.toThrow();
            // no row was annotated
            expect(sheetData.some(r => r["#status#"] === sheetDataRowStatuses.INVALID)).toBe(false);
        });

        it("does not throw when row index is beyond the sheet length", () => {
            const sheetData = newSheet(1);
            expect(() => applyErrors(sheetData, [{ row: 9999, message: "x" }], { additionalDetails: {} })).not.toThrow();
        });

        it("still records out-of-range errors in additionalDetails.sheetErrors", () => {
            const resourceDetails: any = { additionalDetails: {} };
            const errors = [{ row: 0, message: "out of range" }];
            applyErrors(newSheet(1), errors, resourceDetails);
            expect(resourceDetails.additionalDetails.sheetErrors).toEqual(errors);
        });

        it("annotates valid rows while skipping out-of-range ones in the same batch", () => {
            const sheetData = newSheet(2);
            applyErrors(sheetData, [
                { row: 0, message: "skip-me" },   // -3 → skipped
                { row: 4, message: "keep-me" },   // index 1 → annotated
            ], { additionalDetails: {} });
            expect(sheetData[1]["#errorDetails#"]).toBe("keep-me");
            expect(sheetData[1]["#status#"]).toBe(sheetDataRowStatuses.INVALID);
            expect(sheetData[0]["#status#"]).toBeUndefined();
        });
    });

    describe("no errors", () => {
        it("leaves additionalDetails untouched when there are no errors", () => {
            const resourceDetails: any = { additionalDetails: { existing: true } };
            applyErrors(newSheet(1), [], resourceDetails);
            expect(resourceDetails.additionalDetails.sheetErrors).toBeUndefined();
        });
    });
});
