import config from "../../config";
import { logger,getFormattedStringForDebug } from "../logger";
import { throwError } from "../genericUtils";
import pool from "../../config/dbPoolConfig";


export const getTableName = (tableName: string, tenantId: string): string => {
  if (config.isEnvironmentCentralInstance) {
    // If tenantId has no ".", default to tenantId itself
    const firstTenantPartAfterSplit = tenantId.includes(".")
      ? tenantId.split(".")[0]
      : tenantId;
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