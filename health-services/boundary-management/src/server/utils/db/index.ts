import config from "../../config";
import { logger,getFormattedStringForDebug } from "../logger";
import { throwError } from "../genericUtils";
import pool from "../../config/dbPoolConfig";


// The schema segment derived from tenantId is interpolated directly into SQL (a schema/table
// identifier cannot be a bound parameter), so it must be a safe SQL identifier. Reject anything
// else to prevent SQL injection via a crafted tenantId (e.g. "t WHERE false UNION SELECT ... --").
const SCHEMA_IDENTIFIER_PATTERN = /^[A-Za-z_][A-Za-z0-9_]{0,62}$/;

export const getTableName = (tableName: string, tenantId: string): string => {
  if (config.isEnvironmentCentralInstance) {
    // If tenantId has no ".", default to tenantId itself
    const firstTenantPartAfterSplit = tenantId.includes(".")
      ? tenantId.split(".")[0]
      : tenantId;
    if (!SCHEMA_IDENTIFIER_PATTERN.test(firstTenantPartAfterSplit)) {
      // Use the codebase-standard VALIDATION_ERROR code so this returns a proper HTTP 400
      // (an unregistered code is remapped to 500 by throwError/getErrorCodes).
      throwError("COMMON", 400, "VALIDATION_ERROR", "Invalid tenantId format");
    }
    return `${firstTenantPartAfterSplit}.${tableName}`;
  } else {
    return `${getDBSchemaName(config.DB_CONFIG.DB_SCHEMA)}.${tableName}`;
  }
};

const getDBSchemaName = (dbSchema = "") => {
  // return "health";
  return dbSchema ? (dbSchema == "egov" ? "public" : dbSchema) : "public";
}

export const executeQuery = async (
  query: string,
  values: any
): Promise<any> => {
  try {
    logger.info(`DB QUERY :: STATEMENT :: ${query}`);
    logger.info(`DB QUERY :: VALUES ::   ${values}`);
    const queryResponse = await pool.query(query, values);
    logger.info(
      `DB QUERY :: RESPONSE ::  SUCCESS :: returns ${queryResponse?.rowCount} rows.`
    );
    logger.debug( `DB QUERY :: RESPONSE ::  SUCCESS :: Query Response ${getFormattedStringForDebug(queryResponse?.rows)}`);
    return queryResponse;
  } catch (error: any) {
    logger.error(`Error fetching data from the database: ${error?.message}`);
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", error?.message);
    throw error;
  }
};