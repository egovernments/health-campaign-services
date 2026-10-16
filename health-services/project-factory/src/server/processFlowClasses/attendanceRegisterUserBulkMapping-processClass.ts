import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import { TemplateClass as AttendanceRegisterAttendeeTemplateClass } from "./attendanceRegisterAttendee-processClass";
import { attendanceCacheKeys } from "../config/constants";
import {
    collectBulkWorkerIds,
    fetchIndividualProfilesById,
    getBulkRowsBySheetName,
    getLocalizedAttendanceSheetData,
    hasActionableRows,
    logBulkProjectionSummary,
    mergeResolvedIndividualIdObjects,
    normalizeBulkRowsForAttendeeFlow,
    ensureRowsHaveStatus,
    toBulkSheetMap,
} from "../utils/attendanceRegisterUserBulkMappingUtils";

/**
 * Processes bulk register-user mapping uploads by normalizing the 3 attendee tabs
 * (with prepended register columns) and delegating to attendee process flow.
 * Rows without mapping edits are kept as SKIPPED in the returned sheet output.
 *
 * Reuses existing attendee processing for persistence/API behavior.
 */
export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        await validateResourceDetailsBeforeProcess("attendanceRegisterUserBulkMappingValidation", resourceDetails, localizationMap);

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
            const localizedAttendanceSheetData = getLocalizedAttendanceSheetData(actionableRowsBySheetName, localizationMap);
            const skipPreValidationBefore = resourceDetails.additionalDetails?.skipPreValidation;
            resourceDetails.additionalDetails.skipPreValidation = true;
            try {
                await AttendanceRegisterAttendeeTemplateClass.process(
                    resourceDetails,
                    localizedAttendanceSheetData,
                    localizationMap,
                    templateConfig
                );
            } finally {
                if (skipPreValidationBefore === undefined) {
                    delete resourceDetails.additionalDetails.skipPreValidation;
                } else {
                    resourceDetails.additionalDetails.skipPreValidation = skipPreValidationBefore;
                }
            }
        }

        ensureRowsHaveStatus(allRowsBySheetName);

        const totalRows = Array.from(allRowsBySheetName.values()).reduce((sum, rows) => sum + rows.length, 0);
        logger.info(`Bulk attendee mapping process complete — rows=${totalRows}`);
        return toBulkSheetMap(allRowsBySheetName);
    }
}
