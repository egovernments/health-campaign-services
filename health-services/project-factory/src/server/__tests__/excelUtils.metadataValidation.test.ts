const mockConfig = {
    values: {
        validateCampaignIdInMetadata: false,
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

import { validateFileCmapaignIdInMetaData } from "../utils/excelUtils";

describe("validateFileCmapaignIdInMetaData", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockConfig.values.validateCampaignIdInMetadata = false;
    });

    it("allows missing metadata for attendance bulk mapping when strict campaign metadata validation is disabled", () => {
        expect(() =>
            validateFileCmapaignIdInMetaData(
                {},
                "cmp-1",
                "attendanceRegisterUserBulkMapping"
            )
        ).not.toThrow();
        expect(mockThrowError).not.toHaveBeenCalled();
    });

    it("allows invalid metadata format for attendance bulk mapping when strict campaign metadata validation is disabled", () => {
        expect(() =>
            validateFileCmapaignIdInMetaData(
                { keywords: "en_BEDNET" },
                "cmp-1",
                "attendanceRegisterUserBulkMapping"
            )
        ).not.toThrow();
        expect(mockThrowError).not.toHaveBeenCalled();
    });

    it("still rejects missing metadata for non-bulk types", () => {
        expect(() =>
            validateFileCmapaignIdInMetaData(
                {},
                "cmp-1",
                "attendanceRegisterAttendeeValidation"
            )
        ).toThrow("The template doesn't have campaign metadata. Please upload the generated template only.");
        expect(mockThrowError).toHaveBeenCalledWith(
            "FILE",
            400,
            "INVALID_TEMPLATE",
            "The template doesn't have campaign metadata. Please upload the generated template only."
        );
    });

    it("rejects missing metadata for bulk mapping when strict campaign metadata validation is enabled", () => {
        mockConfig.values.validateCampaignIdInMetadata = true;

        expect(() =>
            validateFileCmapaignIdInMetaData(
                {},
                "cmp-1",
                "attendanceRegisterUserBulkMapping"
            )
        ).toThrow("The template doesn't have campaign metadata. Please upload the generated template only.");
        expect(mockThrowError).toHaveBeenCalledWith(
            "FILE",
            400,
            "INVALID_TEMPLATE",
            "The template doesn't have campaign metadata. Please upload the generated template only."
        );
    });
});
