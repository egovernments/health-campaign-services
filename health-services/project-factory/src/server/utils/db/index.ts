import pool from "../../config/dbPoolConfig";
import { throwError } from "../genericUtils";
import { getFormattedStringForDebug, logger } from "../logger";

/**
 * Executes a database query asynchronously.
 *
 * @param {string} query - The SQL query to execute.
 * @param {any} values - The values to be used in the query.
 * @returns {Promise<any>} - A Promise resolving to the query response.
 * @throws - Throws an error if there is an issue with executing the query or closing the database connection.
 */
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
