import { httpRequest } from "../utils/request";
import { defaultRequestInfo } from "./coreApis";
import config from "../config";
import { throwError } from "../utils/genericUtils";
import { logger } from "../utils/logger";

export async function fetchProductVariants(pvarIds: string[], tenantId?: string) {
    const CHUNK_SIZE = 100;
    const allProductVariants: any[] = [];
    const params: any = { limit: CHUNK_SIZE, offset: 0, tenantId: tenantId };

    for (let i = 0; i < pvarIds.length; i += CHUNK_SIZE) {
        const chunk = pvarIds.slice(i, i + CHUNK_SIZE);
        try {
            const response = await httpRequest(
                config.host.productHost + config.paths.productVariantSearch,
                { ProductVariant: { id: chunk }, ...defaultRequestInfo },
                params
            );
            allProductVariants.push(...response?.ProductVariant || []);
        } catch (error: any) {
            logger.error("Error during product variant fetch");
            throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", `Some error occurred while fetching product variants. ${error?.message}`);
        }
    }

    return allProductVariants;  // Return the fetched product variants
}

/**
 * Create attendance registers via Attendance Service API
 * @param registers - Array of attendance register payloads
 * @param requestInfo - Request info object
 * @returns Response from Attendance Service
 */
export async function createAttendanceRegisters(registers: any[], requestInfo: any) {
    const url = config.host.attendanceHost + config.paths.attendanceRegisterCreate;
    const requestBody = {
        RequestInfo: requestInfo,
        attendanceRegister: registers
    };

    try {
        logger.info("Creating {} attendance registers", registers.length);
        const response = await httpRequest(url, requestBody);
        logger.info("Successfully created attendance registers");
        return response;
    } catch (error: any) {
        logger.error("Error creating attendance registers: {}", error?.message);
        throwError("ATTENDANCE", 500, "ATTENDANCE_REGISTER_CREATION_FAILED",
            `Failed to create attendance registers. ${error?.message}`);
    }
}

/**
 * Search for existing attendance registers by serviceCode
 * @param serviceCodes - Array of service codes to search for
 * @param tenantId - Tenant ID
 * @returns Array of existing attendance registers
 */
export async function searchAttendanceRegistersByServiceCodes(
    serviceCodes: string[],
    tenantId: string
): Promise<any[]> {
    const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
    const requestBody = {
        RequestInfo: defaultRequestInfo?.RequestInfo || {},
        attendanceRegisterSearchCriteria: {
            tenantId: tenantId,
            serviceCode: serviceCodes
        }
    };

    try {
        logger.info("Searching for existing attendance registers with {} serviceCode(s)", serviceCodes.length);
        const response = await httpRequest(url, requestBody);
        const registers = response?.attendanceRegister || [];
        logger.info("Found {} existing attendance registers", registers.length);
        return registers;
    } catch (error: any) {
        logger.warn("Error searching for existing attendance registers: {}", error?.message);
        // Don't throw - return empty array to allow process to continue
        return [];
    }
}
