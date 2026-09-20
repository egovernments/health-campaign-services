jest.mock("../config", () => ({
    __esModule: true,
    default: {
        DB_CONFIG: {
            DB_USER: "u",
            DB_HOST: "h",
            DB_NAME: "n",
            DB_PASSWORD: "p",
            DB_PORT: "5432",
            DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME: "eg_cm_generated_resource_details",
        },
        localisation: { defaultLocale: "en_IN" },
        host: new Proxy({ redisHost: "localhost" } as Record<string, string>, {
            get: (target, prop: string) => target[prop] ?? `http://${String(prop)}.stub/`,
        }),
        paths: new Proxy({} as Record<string, string>, {
            get: (_target, prop: string) => `stub/${String(prop)}`,
        }),
        cacheValues: { redisPort: "6379", cacheEnabled: false, resetCache: false },
        values: new Proxy({} as Record<string, string>, { get: () => "" }),
        kafka: {
            KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC: "update-generated-resource-details",
            KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC: "create-generated-resource-details",
        },
    },
}));

jest.mock("../utils/redisUtils", () => ({
    redis: { get: jest.fn(), set: jest.fn(), expire: jest.fn() },
    checkRedisConnection: jest.fn().mockResolvedValue(false),
}));

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), debug: jest.fn(), warn: jest.fn(), error: jest.fn() },
    getFormattedStringForDebug: jest.fn((v: any) => JSON.stringify(v)),
}));

jest.mock("../kafka/Producer", () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock("../utils/genericUtils", () => ({
    searchAllGeneratedResources: jest.fn(),
    getLocalizedMessagesHandlerViaLocale: jest.fn(),
    throwError: jest.fn(),
}));

import { localityKeyOf } from "../utils/generatedResourceUtils";

describe("localityKeyOf", () => {
    it("returns the trimmed locality code when present", () => {
        expect(localityKeyOf({ additionalDetails: { localityCode: "WARD-1" } })).toBe("WARD-1");
        expect(localityKeyOf({ additionalDetails: { localityCode: "  WARD-1  " } })).toBe("WARD-1");
    });

    it("normalizes every campaign-wide representation to the same null key", () => {
        expect(localityKeyOf({ additionalDetails: {} })).toBeNull();
        expect(localityKeyOf({ additionalDetails: { localityCode: "" } })).toBeNull();
        expect(localityKeyOf({ additionalDetails: { localityCode: "   " } })).toBeNull();
        expect(localityKeyOf({ additionalDetails: { localityCode: undefined } })).toBeNull();
        expect(localityKeyOf({})).toBeNull();
        expect(localityKeyOf(undefined)).toBeNull();
    });

    it("treats a non-string locality code as no locality rather than coercing it", () => {
        expect(localityKeyOf({ additionalDetails: { localityCode: 42 } })).toBeNull();
        expect(localityKeyOf({ additionalDetails: { localityCode: null } })).toBeNull();
    });

    it("isolates different localities and matches identical ones", () => {
        const a = localityKeyOf({ additionalDetails: { localityCode: "WARD-1" } });
        const b = localityKeyOf({ additionalDetails: { localityCode: "WARD-2" } });
        const aAgain = localityKeyOf({ additionalDetails: { localityCode: "WARD-1" } });
        const campaignWide = localityKeyOf({ additionalDetails: {} });

        expect(a).not.toBe(b);
        expect(a).toBe(aAgain);
        expect(a).not.toBe(campaignWide);
        expect(campaignWide).toBe(localityKeyOf({ additionalDetails: {} }));
    });
});

describe("initializeGenerateAndGetResponse locality scoping", () => {
    const loadModule = () => {
        const { initializeGenerateAndGetResponse } = require("../utils/sheetManageUtils");
        const { searchAllGeneratedResources } = require("../utils/genericUtils");
        const { produceModifiedMessages } = require("../kafka/Producer");
        return { initializeGenerateAndGetResponse, searchAllGeneratedResources, produceModifiedMessages };
    };

    const rowWith = (id: string, status: string, localityCode?: string) => ({
        id,
        status,
        additionalDetails: localityCode ? { localityCode } : {},
        auditDetails: { createdTime: 1, lastModifiedTime: 1, createdBy: "u", lastModifiedBy: "u" },
    });

    const expiredIdsFrom = (produceModifiedMessages: jest.Mock) => {
        const updateCall = produceModifiedMessages.mock.calls.find(
            (call: any[]) => call[1] === "update-generated-resource-details"
        );
        if (!updateCall) return [];
        return updateCall[0].generatedResource.map((resource: any) => resource.id);
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it("expires only rows for the same locality, leaving other localities untouched", async () => {
        const { initializeGenerateAndGetResponse, searchAllGeneratedResources, produceModifiedMessages } = loadModule();
        searchAllGeneratedResources.mockImplementation(async (query: any) =>
            query.status === "completed"
                ? [rowWith("completed-loc1", "completed", "WARD-1"), rowWith("completed-loc2", "completed", "WARD-2")]
                : [rowWith("inprogress-loc2", "inprogress", "WARD-2")]
        );

        await initializeGenerateAndGetResponse(
            "bednet", "attendanceRegisterUserBulkMapping", "ADMIN", "cmp-1", "user-1", "en_IN", {},
            { localityCode: "WARD-1" }
        );

        expect(expiredIdsFrom(produceModifiedMessages)).toEqual(["completed-loc1"]);
    });

    it("expires only campaign-wide rows for a campaign-wide generation", async () => {
        const { initializeGenerateAndGetResponse, searchAllGeneratedResources, produceModifiedMessages } = loadModule();
        searchAllGeneratedResources.mockImplementation(async (query: any) =>
            query.status === "completed"
                ? [rowWith("completed-wide", "completed"), rowWith("completed-loc1", "completed", "WARD-1")]
                : []
        );

        await initializeGenerateAndGetResponse(
            "bednet", "attendanceRegisterUserBulkMapping", "ADMIN", "cmp-1", "user-1", "en_IN", {}, {}
        );

        expect(expiredIdsFrom(produceModifiedMessages)).toEqual(["completed-wide"]);
    });

    it("keeps locality-less types expiring exactly as before", async () => {
        const { initializeGenerateAndGetResponse, searchAllGeneratedResources, produceModifiedMessages } = loadModule();
        searchAllGeneratedResources.mockImplementation(async (query: any) =>
            query.status === "completed"
                ? [rowWith("boundary-1", "completed"), rowWith("boundary-2", "completed")]
                : [rowWith("boundary-3", "inprogress")]
        );

        await initializeGenerateAndGetResponse(
            "bednet", "facilityWithBoundary", "ADMIN", "cmp-1", "user-1", "en_IN", {}
        );

        expect(expiredIdsFrom(produceModifiedMessages)).toEqual(["boundary-1", "boundary-2", "boundary-3"]);
    });

    it("records the locality on the new in-progress row so a concurrent reader can compare it", async () => {
        const { initializeGenerateAndGetResponse, searchAllGeneratedResources, produceModifiedMessages } = loadModule();
        searchAllGeneratedResources.mockResolvedValue([]);

        const created = await initializeGenerateAndGetResponse(
            "bednet", "attendanceRegisterUserBulkMapping", "ADMIN", "cmp-1", "user-1", "en_IN", {},
            { localityCode: "WARD-1" }
        );

        expect(created.additionalDetails).toEqual({ localityCode: "WARD-1" });
        const createCall = produceModifiedMessages.mock.calls.find(
            (call: any[]) => call[1] === "create-generated-resource-details"
        );
        expect(createCall[0].generatedResource[0].additionalDetails).toEqual({ localityCode: "WARD-1" });
    });
});
