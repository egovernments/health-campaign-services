import Ajv from "ajv";
import { searchCampaignDetailsSchema } from "../config/models/searchCampaignDetails";

describe("searchCampaignDetailsSchema", () => {
  const validate = new Ajv({ strict: false }).compile(searchCampaignDetailsSchema);
  const withSortBy = (sortBy: string) => ({ tenantId: "mz", pagination: { sortBy } });

  describe("pagination.sortBy", () => {
    it("accepts a bare column name", () => {
      expect(validate(withSortBy("createdTime"))).toBe(true);
    });

    it("accepts a snake_case column name", () => {
      expect(validate(withSortBy("last_modified_time"))).toBe(true);
    });

    it("rejects a value carrying an extra SQL expression", () => {
      expect(validate(withSortBy("createdTime; SELECT pg_sleep(5)"))).toBe(false);
    });

    it("rejects a subquery", () => {
      expect(validate(withSortBy("(SELECT 1)"))).toBe(false);
    });

    it("rejects an empty string", () => {
      expect(validate(withSortBy(""))).toBe(false);
    });
  });
});
