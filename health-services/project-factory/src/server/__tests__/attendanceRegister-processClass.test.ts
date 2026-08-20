/**
 * Output of the processed register file. It is what the console serves as the current register
 * list after an upload, so registers deleted in the attendance service must not appear in it.
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────
jest.mock('../config', () => ({
    default: {
        host: {},
        paths: {},
        kafka: {},
        attendanceRegister: { serviceCodeParallelSearchLimit: 5, batchSize: 50 },
    },
    __esModule: true,
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('../utils/campaignUtils', () => ({ getLocalizedName: jest.fn((key: string) => key) }));

jest.mock('../utils/sheetManageUtils', () => ({ validateResourceDetailsBeforeProcess: jest.fn() }));

jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn().mockResolvedValue([]),
    throwError: jest.fn(),
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

jest.mock('../api/coreApis', () => ({
    searchBoundaryRelationshipData: jest.fn(),
    searchBoundaryRelationshipDefinition: jest.fn(),
}));

import { TemplateClass } from "../processFlowClasses/attendanceRegister-processClass";
import { CampaignDataRow } from "../config/models/campaignDataRow";
import { SheetRow } from "../models/SheetMap";

// buildOutputData is private, so the test reaches it through a view that still checks its signature.
const templateInternals = TemplateClass as unknown as {
    buildOutputData: (allRows: Partial<CampaignDataRow>[], unpersistableRows: SheetRow[]) => SheetRow[];
};

const buildOutputData = (allRows: Partial<CampaignDataRow>[], unpersistableRows: SheetRow[] = []) =>
    templateInternals.buildOutputData(allRows, unpersistableRows);

describe("isDeletedInAttendance", () => {
    const internals = TemplateClass as unknown as {
        isDeletedInAttendance: (register: { status?: string; isDeleted?: boolean }) => boolean;
    };

    it("reads INACTIVE as gone, since the register search has no status filter", () => {
        expect(internals.isDeletedInAttendance({ status: "INACTIVE" })).toBe(true);
    });

    it("reads the flag as gone too", () => {
        expect(internals.isDeletedInAttendance({ isDeleted: true })).toBe(true);
    });

    it("treats an active register as live", () => {
        expect(internals.isDeletedInAttendance({ status: "ACTIVE", isDeleted: false })).toBe(false);
    });

    it("treats a register with neither signal as live", () => {
        expect(internals.isDeletedInAttendance({})).toBe(false);
    });
});

describe("buildOutputData", () => {
    it("drops registers deleted in the attendance service", () => {
        const rows = [
            { isDeleted: false, data: { HCM_ATTENDANCE_REGISTER_ID: "REG-001" } },
            { isDeleted: true, data: { HCM_ATTENDANCE_REGISTER_ID: "REG-002" } },
        ];

        expect(buildOutputData(rows)).toEqual([{ HCM_ATTENDANCE_REGISTER_ID: "REG-001" }]);
    });

    it("keeps rows where isDeleted is absent", () => {
        expect(buildOutputData([{ data: { HCM_ATTENDANCE_REGISTER_ID: "REG-001" } }])).toHaveLength(1);
    });

    it("appends unpersistable rows after the stored ones", () => {
        const rows = [{ isDeleted: false, data: { HCM_ATTENDANCE_REGISTER_ID: "REG-001" } }];

        expect(buildOutputData(rows, [{ HCM_ATTENDANCE_REGISTER_ID: "" }])).toEqual([
            { HCM_ATTENDANCE_REGISTER_ID: "REG-001" },
            { HCM_ATTENDANCE_REGISTER_ID: "" },
        ]);
    });

    it("returns only unpersistable rows when every register is deleted", () => {
        const rows = [{ isDeleted: true, data: { HCM_ATTENDANCE_REGISTER_ID: "REG-002" } }];

        expect(buildOutputData(rows, [{ HCM_ATTENDANCE_REGISTER_ID: "" }])).toEqual([
            { HCM_ATTENDANCE_REGISTER_ID: "" },
        ]);
    });
});
