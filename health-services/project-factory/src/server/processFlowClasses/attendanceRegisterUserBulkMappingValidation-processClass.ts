import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { attendanceCacheKeys, attendanceSheetNames, sheetDataRowStatuses } from "../config/constants";
import { getLocalizedName } from "../utils/campaignUtils";
import { TemplateClass as AttendanceRegisterAttendeeValidationTemplateClass } from "./attendanceRegisterAttendeeValidation-processClass";
import {
    applyProjectedStatusesToBulkRows,
    bulkAttendanceSheetName,
    collectBulkSheetErrors,
    collectBulkWorkerIds,
    fetchIndividualProfilesById,
    getLocalizedAttendanceSheetData,
    logBulkProjectionSummary,
    mergeResolvedIndividualIdObjects,
    projectBulkRowsToAttendanceSheets,
} from "../utils/attendanceRegisterUserBulkMappingUtils";

const SHEET_NAMES = [
    attendanceSheetNames.WORKER,
    attendanceSheetNames.MARKER,
    attendanceSheetNames.APPROVER,
];

/**
 * Validates bulk register-user mapping rows by projecting each row into the
 * existing attendee validation pipeline (worker/marker/approver sheets).
 */
export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        const localizedBulkSheetName = getLocalizedName(bulkAttendanceSheetName, localizationMap);
        const bulkRows: Record<string, any>[] = wholeSheetData?.[localizedBulkSheetName] || [];

        if (!Array.isArray(bulkRows)) {
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

        const groupedRowsByRegister = this.groupProjectedRowsByRegister(projections);
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

        applyProjectedStatusesToBulkRows(projections);
        this.ensureSkippedRowsHaveStatus(bulkRows);

        const sheetErrors = collectBulkSheetErrors(bulkRows);
        if (sheetErrors.length > 0) {
            resourceDetails.additionalDetails.sheetErrors = sheetErrors;
        } else {
            delete resourceDetails.additionalDetails.sheetErrors;
        }

        return {
            [bulkAttendanceSheetName]: {
                data: bulkRows,
                dynamicColumns: null
            }
        };
    }

    private static groupProjectedRowsByRegister(
        projections: Array<{ registerServiceCode: string; sheetName: string; projectedRow: Record<string, any> }>
    ): Map<string, Map<string, Record<string, any>[]>> {
        const groupedRowsByRegister = new Map<string, Map<string, Record<string, any>[]>>();
        for (const projection of projections) {
            const registerServiceCode = String(projection.registerServiceCode || "").trim();
            if (!registerServiceCode) continue;

            let groupedRows = groupedRowsByRegister.get(registerServiceCode);
            if (!groupedRows) {
                groupedRows = this.createEmptyProjectedSheetMap();
                groupedRowsByRegister.set(registerServiceCode, groupedRows);
            }
            groupedRows.get(projection.sheetName)?.push(projection.projectedRow);
        }
        return groupedRowsByRegister;
    }

    private static createEmptyProjectedSheetMap(): Map<string, Record<string, any>[]> {
        return new Map<string, Record<string, any>[]>(SHEET_NAMES.map((sheetName) => [sheetName, []]));
    }

    private static ensureSkippedRowsHaveStatus(bulkRows: Record<string, any>[]): void {
        for (const row of bulkRows) {
            if (!row["#status#"]) {
                row["#status#"] = sheetDataRowStatuses.SKIPPED;
            }
        }
    }
}
