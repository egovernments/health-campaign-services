import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { attendanceCacheKeys } from "../config/constants";
import { TemplateClass as AttendanceRegisterAttendeeValidationTemplateClass } from "./attendanceRegisterAttendeeValidation-processClass";
import {
    collectBulkSheetErrors,
    collectBulkWorkerIds,
    fetchIndividualProfilesById,
    getBulkRowsBySheetName,
    getLocalizedAttendanceSheetData,
    groupRowsByRegister,
    hasActionableRows,
    logBulkProjectionSummary,
    mergeResolvedIndividualIdObjects,
    normalizeBulkRowsForAttendeeFlow,
    ensureRowsHaveStatus,
    toBulkSheetMap,
} from "../utils/attendanceRegisterUserBulkMappingUtils";

/**
 * Validates bulk register-user mapping rows by normalizing the 3 attendee tabs and
 * grouping by register before delegating to attendee validation.
 *
 * Delegates to attendee validation so existing date/business rules stay unchanged.
 */
export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        const allRowsBySheetName = getBulkRowsBySheetName(wholeSheetData, localizationMap);

        const tenantId = String(resourceDetails?.tenantId || "").split(".")[0];
        const workerIds = collectBulkWorkerIds(allRowsBySheetName);
        const profiles = await fetchIndividualProfilesById(tenantId, workerIds, resourceDetails?.requestInfo);
        const { actionableRowsBySheetName, resolvedIndividualIds } = normalizeBulkRowsForAttendeeFlow(
            allRowsBySheetName,
            profiles,
            localizationMap
        );
        logBulkProjectionSummary(allRowsBySheetName, actionableRowsBySheetName);

        resourceDetails.additionalDetails = resourceDetails.additionalDetails || {};
        resourceDetails.additionalDetails[attendanceCacheKeys.RESOLVED_INDIVIDUAL_IDS] = mergeResolvedIndividualIdObjects(
            resourceDetails.additionalDetails?.[attendanceCacheKeys.RESOLVED_INDIVIDUAL_IDS],
            resolvedIndividualIds
        );

        if (hasActionableRows(actionableRowsBySheetName)) {
            const groupedRowsByRegister = groupRowsByRegister(actionableRowsBySheetName);
            for (const [registerServiceCode, groupedRows] of groupedRowsByRegister.entries()) {
                logger.info(`Validating bulk mappings for register ${registerServiceCode}`);
                const localizedGroupedSheetData = getLocalizedAttendanceSheetData(groupedRows, localizationMap);
                await AttendanceRegisterAttendeeValidationTemplateClass.process(
                    resourceDetails,
                    localizedGroupedSheetData,
                    localizationMap,
                    templateConfig
                );
            }
        }

        ensureRowsHaveStatus(allRowsBySheetName);

        const sheetErrors = collectBulkSheetErrors(allRowsBySheetName);
        if (sheetErrors.length > 0) {
            resourceDetails.additionalDetails.sheetErrors = sheetErrors;
        } else {
            delete resourceDetails.additionalDetails.sheetErrors;
        }

        return toBulkSheetMap(allRowsBySheetName);
    }
}
