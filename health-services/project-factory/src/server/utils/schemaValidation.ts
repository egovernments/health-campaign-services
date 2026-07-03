import config from "../config";
import { executeQuery } from "./db";
import { logger } from "./logger";

const REQUIRED_MAPPING_COLUMNS = ["retrycount", "lasterror"];

export async function validateRequiredSchema(): Promise<void> {
  const tableName = config?.DB_CONFIG?.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME;
  for (const column of REQUIRED_MAPPING_COLUMNS) {
    const result = await executeQuery(
      `SELECT 1 FROM information_schema.columns WHERE table_name = $1 AND column_name = $2`,
      [tableName, column]
    );
    if (!result?.rows?.length) {
      throw new Error(
        `Required column "${column}" missing from table "${tableName}". ` +
        `Run migration V20260612120000 before starting this pod.`
      );
    }
  }
  logger.info("Schema validation passed — required mapping columns present.");
}
