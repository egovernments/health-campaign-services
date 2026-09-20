const mockConfig = {
    values: {
        validateCampaignIdInMetadata: true,
    },
};

const mockThrowError = jest.fn((module = "COMMON", status = 500, code = "UNKNOWN_ERROR", description: any = null) => {
    const error: any = new Error(description || code);
    error.status = status;
    error.code = code;
    error.description = description;
    throw error;
});

jest.mock("../config", () => ({
    default: mockConfig,
    __esModule: true,
}));

jest.mock("../utils/genericUtils", () => ({
    changeFirstRowColumnColour: jest.fn(),
    throwError: (...args: any[]) => mockThrowError(...args),
}));

jest.mock("../utils/request", () => ({
    httpRequest: jest.fn(),
}));

jest.mock("../utils/onGoingCampaignUpdateUtils", () => ({
    freezeUnfreezeColumnsForProcessedFile: jest.fn(),
    getColumnIndexByHeader: jest.fn(() => -1),
    hideColumnsOfProcessedFile: jest.fn(),
}));

jest.mock("../utils/campaignUtils", () => ({
    getLocalizedName: jest.fn((key: string) => key),
}));

jest.mock("../config/createAndSearch", () => ({}));

jest.mock("../utils/logger", () => ({
    logger: {
        info: jest.fn(),
        warn: jest.fn(),
        error: jest.fn(),
        debug: jest.fn(),
    },
}));

import * as ExcelJS from "exceljs";
import { enrichTemplateMetaData, getLocaleFromWorkbook, validateFileCmapaignIdInMetaData } from "../utils/excelUtils";

const BULK_MAPPING_TYPE = "attendanceRegisterUserBulkMapping";

describe("validateFileCmapaignIdInMetaData", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockConfig.values.validateCampaignIdInMetadata = true;
    });

    it("rejects files with no metadata in strict mode", () => {
        expect(() =>
            validateFileCmapaignIdInMetaData(
                {},
                "cmp-1",
                BULK_MAPPING_TYPE
            )
        ).toThrow("The template doesn't have campaign metadata. Please upload the generated template only.");
        expect(mockThrowError).toHaveBeenCalledWith(
            "FILE",
            400,
            "INVALID_TEMPLATE",
            "The template doesn't have campaign metadata. Please upload the generated template only."
        );
    });

    it("passes strict validation when keywords are stripped but metadata sheet exists", () => {
        const workbook = new ExcelJS.Workbook();
        enrichTemplateMetaData(workbook, "en_BEDNET", "cmp-1", BULK_MAPPING_TYPE);
        workbook.keywords = undefined as any;

        expect(() =>
            validateFileCmapaignIdInMetaData(
                workbook,
                "cmp-1",
                BULK_MAPPING_TYPE
            )
        ).not.toThrow();
        expect(mockThrowError).not.toHaveBeenCalled();
    });

    it("fails strict validation when sheet metadata campaign does not match", () => {
        const workbook = new ExcelJS.Workbook();
        enrichTemplateMetaData(workbook, "en_BEDNET", "cmp-1", BULK_MAPPING_TYPE);
        workbook.keywords = undefined as any;

        expect(() =>
            validateFileCmapaignIdInMetaData(
                workbook,
                "cmp-2",
                BULK_MAPPING_TYPE
            )
        ).toThrow("The template doesn't have matching campaign metadata. Please upload the generated template for the current campaign only.");
        expect(mockThrowError).toHaveBeenCalledWith(
            "FILE",
            400,
            "INVALID_TEMPLATE",
            "The template doesn't have matching campaign metadata. Please upload the generated template for the current campaign only."
        );
    });

    it("reads locale from metadata sheet when keywords are stripped", () => {
        const workbook = new ExcelJS.Workbook();
        enrichTemplateMetaData(workbook, "en_BEDNET", "cmp-1", BULK_MAPPING_TYPE);
        workbook.keywords = undefined as any;

        expect(getLocaleFromWorkbook(workbook)).toBe("en_BEDNET");
    });

    it("creates metadata sheet for attendance-register templates", () => {
        const workbook = new ExcelJS.Workbook();
        enrichTemplateMetaData(workbook, "en_BEDNET", "cmp-1", "attendanceRegister");

        expect(workbook.getWorksheet("_hcm_template_meta_")).toBeDefined();
    });

    it("does not create metadata sheet for non-attendance templates", () => {
        const workbook = new ExcelJS.Workbook();
        enrichTemplateMetaData(workbook, "en_BEDNET", "cmp-1", "facility");

        expect(workbook.getWorksheet("_hcm_template_meta_")).toBeUndefined();
        expect(() =>
            validateFileCmapaignIdInMetaData(
                workbook,
                "cmp-1",
                "facility"
            )
        ).not.toThrow();
    });
});
