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
        logger.info("Validating Attendance Register file...");

        const sheetKey = getLocalizedName("HCM_ATTENDANCE_REGISTER_LIST", localizationMap);
        const registerSheetData = wholeSheetData[sheetKey];

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

        logger.info(`Attendance Register validation completed with ${errors.length} error(s).`);
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
        logger.info("Validating boundary codes in attendance register...");
        const campaignDetails = await this.getCampaignDetails(resourceDetails);
        const campaignBoundaryCodes = new Set(
            (campaignDetails?.boundaries || []).map((b: any) => b.code)
        );
        const boundaryCodeKey = getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", localizationMap);

        for (let i = 0; i < sheetData.length; i++) {
            const code = sheetData[i]?.[boundaryCodeKey];
            if (code && !campaignBoundaryCodes.has(code)) {
                errors.push({
                    row: i + 3,
                    message: `Boundary code '${code}' is not part of this campaign.`
                });
            }
        }
        logger.info("Boundary code validation completed.");
    }

    private static async getCampaignDetails(resourceDetails: any): Promise<any> {
        if (!resourceDetails?.campaignId && !resourceDetails?.campaignNumber) {
            throw new Error("Either campaignId or campaignNumber must be present in resourceDetails");
        }
        const searchCriteria: any = { tenantId: resourceDetails.tenantId };
        if (resourceDetails?.campaignId) {
            searchCriteria.ids = [resourceDetails.campaignId];
        } else if (resourceDetails?.campaignNumber) {
            searchCriteria.campaignNumber = resourceDetails.campaignNumber;
        }
        const response = await searchProjectTypeCampaignService(searchCriteria);
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");
        return campaign;
    }
}
