import { SheetMap } from "../models/SheetMap";
import { getLocalizedName } from "../utils/campaignUtils";
import { logger } from "../utils/logger";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import { TemplateClass as AttendanceRegisterAttendeeTemplateClass } from "./attendanceRegisterAttendee-processClass";
import { attendanceCacheKeys, sheetDataRowStatuses } from "../config/constants";
import {
    applyProjectedStatusesToBulkRows,
    bulkAttendanceSheetName,
    collectBulkWorkerIds,
    fetchIndividualProfilesById,
    getLocalizedAttendanceSheetData,
    logBulkProjectionSummary,
    mergeResolvedIndividualIdObjects,
    projectBulkRowsToAttendanceSheets,
} from "../utils/attendanceRegisterUserBulkMappingUtils";

/**
 * Processes bulk register-user mapping uploads by projecting each row into the
 * existing attendanceRegisterAttendee process flow and persisting updates.
 */
export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        await validateResourceDetailsBeforeProcess("attendanceRegisterUserBulkMappingValidation", resourceDetails, localizationMap);

        const localizedBulkSheetName = getLocalizedName(bulkAttendanceSheetName, localizationMap);
        const bulkRows: Record<string, any>[] = wholeSheetData?.[localizedBulkSheetName] || [];
        if (!Array.isArray(bulkRows) || bulkRows.length === 0) {
            return {
                [bulkAttendanceSheetName]: { data: [], dynamicColumns: null }
            };
        }

        const tenantId = String(resourceDetails?.tenantId || "").split(".")[0];
        const workerIds = collectBulkWorkerIds(bulkRows);
        const profiles = await fetchIndividualProfilesById(tenantId, workerIds, resourceDetails?.requestInfo);
        const { rowsBySheetName, projections, resolvedIndividualIds } = projectBulkRowsToAttendanceSheets(
            bulkRows,
            profiles,
            localizationMap
        );
        logBulkProjectionSummary(rowsBySheetName);

        resourceDetails.additionalDetails = resourceDetails.additionalDetails || {};
        resourceDetails.additionalDetails[attendanceCacheKeys.RESOLVED_INDIVIDUAL_IDS] = mergeResolvedIndividualIdObjects(
            resourceDetails.additionalDetails?.[attendanceCacheKeys.RESOLVED_INDIVIDUAL_IDS],
            resolvedIndividualIds
        );

        const localizedAttendanceSheetData = getLocalizedAttendanceSheetData(rowsBySheetName, localizationMap);
        const hasProjectedRows = Object.values(localizedAttendanceSheetData).some((rows) => rows.length > 0);

        if (hasProjectedRows) {
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

        applyProjectedStatusesToBulkRows(projections);
        this.ensureRowsHaveStatus(bulkRows);

        logger.info(`Bulk attendee mapping process complete — rows=${bulkRows.length}`);
        return {
            [bulkAttendanceSheetName]: {
                data: bulkRows,
                dynamicColumns: null
            }
        };
    }

    private static ensureRowsHaveStatus(rows: Record<string, any>[]): void {
        for (const row of rows) {
            if (!row["#status#"]) {
                row["#status#"] = sheetDataRowStatuses.SKIPPED;
            }
        }
    }
}
