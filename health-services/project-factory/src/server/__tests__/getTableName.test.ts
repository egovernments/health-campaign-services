jest.mock("../config", () => ({
  __esModule: true,
  default: { isEnvironmentCentralInstance: true, DB_CONFIG: { DB_SCHEMA: "health" } },
}));
jest.mock("../config/dbPoolConfig", () => ({ __esModule: true, default: { query: jest.fn() } }));
jest.mock("../utils/logger", () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock("../utils/genericUtils", () => ({
  throwError: jest.fn((_module: string, status: number, code: string, description: string) => {
    throw Object.assign(new Error(description), { status, code });
  }),
}));

import config from "../config";
import { getTableName } from "../utils/db";

describe("getTableName", () => {
  afterEach(() => {
    (config as { isEnvironmentCentralInstance: boolean }).isEnvironmentCentralInstance = true;
  });

  describe("central instance", () => {
    it("qualifies the table with the state segment of the tenant", () => {
      expect(getTableName("eg_cm_campaign_details", "ng.kaduna")).toBe("ng.eg_cm_campaign_details");
    });

    it("uses the whole tenant when it has no dot", () => {
      expect(getTableName("eg_cm_campaign_details", "mz")).toBe("mz.eg_cm_campaign_details");
    });

    it("rejects a tenant whose state segment carries SQL", () => {
      expect(() => getTableName("eg_cm_campaign_details", "pg_shadow s, public")).toThrow(
        expect.objectContaining({ status: 400, code: "VALIDATION_ERROR" })
      );
    });

    it("rejects an empty tenant", () => {
      expect(() => getTableName("eg_cm_campaign_details", "")).toThrow(
        expect.objectContaining({ status: 400, code: "VALIDATION_ERROR" })
      );
    });
  });

  describe("non-central instance", () => {
    it("uses the configured schema and ignores the tenant", () => {
      (config as { isEnvironmentCentralInstance: boolean }).isEnvironmentCentralInstance = false;
      expect(getTableName("eg_cm_campaign_details", "pg_shadow s, public")).toBe("health.eg_cm_campaign_details");
    });
  });
});
