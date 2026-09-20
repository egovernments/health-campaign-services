import { getErrorCodes } from "../config/constants";

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn(),
}));

jest.mock("../kafka/Producer", () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
    producer: { connect: jest.fn(), send: jest.fn() },
}));

jest.mock("../utils/db", () => ({
    executeQuery: jest.fn().mockResolvedValue({ rows: [] }),
    getTableName: jest.fn((table: string) => table),
}));

jest.mock("../utils/request", () => ({
    httpRequest: jest.fn().mockResolvedValue({}),
    defaultheader: {},
}));

describe("filterResourceDetailType", () => {
    let filterResourceDetailType: (type: string) => void;

    beforeAll(() => {
        filterResourceDetailType = require("../utils/sheetManageUtils").filterResourceDetailType;
    });

    const captureError = (type: string) => {
        try {
            filterResourceDetailType(type);
            return null;
        } catch (e: any) {
            return e;
        }
    };

    it("rejects an unknown type with 400 instead of crashing on a clone of undefined", () => {
        const error = captureError("bogus-type");

        expect(error).not.toBeNull();
        expect(error).not.toBeInstanceOf(SyntaxError);
        expect(error.status).toBe(400);
        expect(error.description).toContain("bogus-type");
    });

    it("rejects attendance register create types so creation stays campaign-driven", () => {
        expect(captureError("attendanceRegister")?.status).toBe(400);
        expect(captureError("attendanceRegisterAttendee")?.status).toBe(400);
    });

    it("accepts the controller-facing validation types", () => {
        expect(captureError("attendanceRegisterValidation")).toBeNull();
        expect(captureError("attendanceRegisterAttendeeValidation")).toBeNull();
        expect(captureError("attendanceRegisterUserBulkMapping")).toBeNull();
    });
});

describe("FILE error code registry", () => {
    it("resolves INVALID_TEMPLATE so template rejections keep their 400 status", () => {
        const resolved: any = getErrorCodes("FILE", "INVALID_TEMPLATE");

        expect(resolved.code).toBe("INVALID_TEMPLATE");
        expect(resolved.code).not.toBe("UNKNOWN_ERROR");
    });
});
