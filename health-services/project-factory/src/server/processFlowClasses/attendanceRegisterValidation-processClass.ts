import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { sheetDataRowStatuses } from "../config/constants";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { validateDatasWithSchema } from "../validators/campaignValidators";
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { throwError } from "../utils/genericUtils";

export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        const sheetKey = getLocalizedName("HCM_ATTENDANCE_REGISTER_LIST", localizationMap);
        const registerSheetData = wholeSheetData[sheetKey];
        logger.info(`Validating Attendance Register file — tenantId=${resourceDetails?.tenantId}, campaignId=${resourceDetails?.campaignId}, rowCount=${registerSheetData?.length || 0}`);

        if (!registerSheetData || registerSheetData.length === 0) {
            throwError("FILE", 400, "SHEET_MISSING_ERROR", `Sheet: '${sheetKey}' is empty or not present`);
        }

        const errors: any[] = [];
        const registerSchema = templateConfig?.sheets?.filter((s: any) => s?.sheetName === "HCM_ATTENDANCE_REGISTER_LIST")[0]?.schema;

        if (registerSchema) {
            validateDatasWithSchema(registerSheetData, registerSchema, errors, localizationMap);
        }

        await this.validateBoundaryCodes(registerSheetData, resourceDetails, localizationMap, errors);

        this.applyErrors(registerSheetData, errors, resourceDetails);

        const sheetMap: SheetMap = {
            [sheetKey]: {
                data: registerSheetData,
                dynamicColumns: null
            }
        };

        const invalidRowCount = registerSheetData.filter((r: any) => r["#status#"] === sheetDataRowStatuses.INVALID).length;
        logger.info(`Attendance Register validation complete — totalRows=${registerSheetData.length}, errors=${errors.length}, invalidRows=${invalidRowCount}`);
        return sheetMap;
    }

    private static applyErrors(sheetData: any[], errors: any[], resourceDetails: ResourceDetails) {
        for (const error of errors) {
            const row = error.row - 3;
            const existing = sheetData?.[row]?.["#errorDetails#"];
            if (existing) {
                const trimmed = existing.trim();
                const sep = trimmed.endsWith(".") ? " " : ". ";
                sheetData[row]["#errorDetails#"] = `${trimmed}${sep}${error.message}`;
            } else {
                sheetData[row]["#errorDetails#"] = error.message;
            }
            sheetData[row]["#status#"] = sheetDataRowStatuses.INVALID;
        }
        if (errors.length > 0) {
            resourceDetails.additionalDetails = {
                ...resourceDetails.additionalDetails,
                sheetErrors: errors
            };
        }
    }

    private static async validateBoundaryCodes(
        sheetData: any[],
        resourceDetails: any,
        localizationMap: Record<string, string>,
        errors: any[]
    ) {
        logger.info(`Validating boundary codes — ${sheetData.length} rows against campaign boundaries`);
        const campaignDetails = await this.getCampaignDetails(resourceDetails);
        const campaignBoundaryCodes = new Set(
            (campaignDetails?.boundaries || []).map((b: any) => b.code)
        );
        const boundaryCodeKey = getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", localizationMap);

        for (let i = 0; i < sheetData.length; i++) {
            const code = sheetData[i]?.[boundaryCodeKey];
            if (code && !campaignBoundaryCodes.has(code)) {
                logger.debug(`Row ${i + 3}: boundary code '${code}' not found in campaign boundaries`);
                errors.push({
                    row: i + 3,
                    message: `Boundary code '${code}' is not part of this campaign.`
                });
            }
        }
        const boundaryErrors = errors.filter(e => e.message.includes("Boundary code")).length;
        logger.info(`Boundary code validation complete — ${boundaryErrors} boundary error(s) found`);
    }

    private static async getCampaignDetails(resourceDetails: any): Promise<any> {
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) {
            logger.error(`Campaign not found — tenantId=${resourceDetails.tenantId}, campaignId=${resourceDetails?.campaignId}`);
            throw new Error("Campaign not found");
        }
        return campaign;
    }
}
