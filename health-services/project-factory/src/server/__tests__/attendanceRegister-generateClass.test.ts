/**
 * Tests for structureBoundaries in attendanceRegister-generateClass.ts
 * Fix #2 (PR #2018): a boundary whose parent code is NOT in the provided set must be treated
 * as a root instead of crashing on `codeToBoundary[parent].children.push(...)`.
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────
jest.mock('../config', () => ({ default: { host: {}, paths: {}, kafka: {} }, __esModule: true }));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('../utils/campaignUtils', () => ({
    // identity localization so we can assert on raw codes
    getLocalizedName: jest.fn((key: string) => key),
    populateBoundariesRecursively: jest.fn(),
}));

jest.mock('../utils/genericUtils', () => ({
    getReadMeConfig: jest.fn(),
    getRelatedDataWithCampaign: jest.fn(),
    throwError: jest.fn(),
}));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

jest.mock('../api/coreApis', () => ({
    searchBoundaryRelationshipData: jest.fn(),
    searchBoundaryRelationshipDefinition: jest.fn(),
}));

import { TemplateClass } from "../generateFlowClasses/attendanceRegister-generateClass";

function structureBoundaries(boundaries: any[], hierarchyType: string, localizationMap: Record<string, string> = {}): any[] {
    return (TemplateClass as any).structureBoundaries(boundaries, hierarchyType, localizationMap);
}

const codesOf = (rows: any[]): string[] => rows.map(r => r["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]);

describe("structureBoundaries", () => {
    describe("normal hierarchy", () => {
        it("builds a row per node for a clean parent/child tree", () => {
            const boundaries = [
                { code: "C1", type: "Country", parent: null },
                { code: "P1", type: "Province", parent: "C1" },
                { code: "D1", type: "District", parent: "P1" },
            ];
            const result = structureBoundaries(boundaries, "ADMIN");
            expect(codesOf(result).sort()).toEqual(["C1", "D1", "P1"]);
        });

        it("nests descendants under the correct ancestor path", () => {
            const boundaries = [
                { code: "C1", type: "Country", parent: null },
                { code: "P1", type: "Province", parent: "C1" },
            ];
            const result = structureBoundaries(boundaries, "ADMIN");
            const provinceRow = result.find(r => r["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] === "P1");
            // path includes the country ancestor → keyed by `${hierarchyType}_${type}`
            expect(provinceRow["ADMIN_COUNTRY"]).toBe("C1");
            expect(provinceRow["ADMIN_PROVINCE"]).toBe("P1");
        });
    });

    describe("missing / orphan parent (Fix #2 — must not crash)", () => {
        it("treats a node whose parent is absent from the set as a root", () => {
            const boundaries = [
                { code: "C1", type: "Country", parent: null },
                // parent "GHOST" is not present in the array (filtered / multi-hierarchy path)
                { code: "P9", type: "Province", parent: "GHOST" },
            ];
            expect(() => structureBoundaries(boundaries, "ADMIN")).not.toThrow();
            const result = structureBoundaries(boundaries, "ADMIN");
            expect(codesOf(result)).toContain("P9");
            expect(codesOf(result)).toContain("C1");
        });

        it("does not throw when every boundary points to a missing parent", () => {
            const boundaries = [
                { code: "X1", type: "District", parent: "NOPE" },
                { code: "X2", type: "District", parent: "ALSO-NOPE" },
            ];
            let result: any[] = [];
            expect(() => { result = structureBoundaries(boundaries, "ADMIN"); }).not.toThrow();
            expect(codesOf(result).sort()).toEqual(["X1", "X2"]);
        });
    });

    describe("edge cases", () => {
        it("returns an empty array for no boundaries", () => {
            expect(structureBoundaries([], "ADMIN")).toEqual([]);
        });

        it("treats a node with no parent as a root", () => {
            const result = structureBoundaries([{ code: "R1", type: "Country", parent: null }], "ADMIN");
            expect(codesOf(result)).toEqual(["R1"]);
        });
    });
});
