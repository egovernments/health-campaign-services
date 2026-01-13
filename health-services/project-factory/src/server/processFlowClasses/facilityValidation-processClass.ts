import { getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { sheetDataRowStatuses } from "../config/constants";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { validateActiveFieldMinima, validateDatasWithSchema } from "../validators/campaignValidators";
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { throwError } from "../utils/genericUtils";


// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        logger.info("Processing file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);
        const facilitySheetData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_FACILITIES", localizationMap)];
        if(!facilitySheetData || !facilitySheetData?.length ) {
            throwError("FILE", 400, "SHEET_MISSING_ERROR", `Sheet: '${getLocalizedName("HCM_ADMIN_CONSOLE_FACILITIES", localizationMap)}' is empty or not present`);
        }
        const errors: any[] = [];
        const facilitySchema = templateConfig?.sheets?.filter((s: any) => s?.sheetName === "HCM_ADMIN_CONSOLE_FACILITIES")[0]?.schema;
        validateDatasWithSchema(facilitySheetData, facilitySchema, errors, localizationMap);
        validateActiveFieldMinima(facilitySheetData, "HCM_ADMIN_CONSOLE_FACILITY_USAGE", errors);
        await this.validateBoundaries(facilitySheetData, resourceDetails, errors);
        this.processErrors(facilitySheetData, errors, resourceDetails);
        const sheetMap: SheetMap = {
            ["HCM_ADMIN_CONSOLE_FACILITIES"]: {
                data: facilitySheetData,
                dynamicColumns: null
            }
        }
        logger.info(`SheetMap generated for template of type ${resourceDetails.type}.`);
        return sheetMap;
    }

    private static processErrors(sheetData : any, errors : any[], resourceDetails : ResourceDetails) {
        for (const error of errors) {
            const row = error.row - 3;
            const existingError = sheetData?.[row]?.["#errorDetails#"];

            if (existingError) {
                const trimmed = existingError.trim();
                const separator = trimmed.endsWith(".") ? " " : ". ";
                sheetData[row]["#errorDetails#"] = `${trimmed}${separator}${error.message}`;
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

    private static async validateBoundaries(facilitySheetData: any, resourceDetails: any, errors: any[]) {
        logger.info("Validating boundaries...");
        const campaignDetails = await this.getCampaignDetails(resourceDetails);
        const tenantId = campaignDetails?.tenantId;
        const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(tenantId, campaignDetails?.hierarchyType, true, true, false);
        const boundaries = campaignDetails?.boundaries || [];

        const boundaryChildren: Record<string, boolean> = boundaries.reduce((acc: any, boundary: any) => {
            acc[boundary.code] = boundary.includeAllChildren;
            return acc;
        }, {});

        const boundaryCodes: any = new Set(boundaries.map((boundary: any) => boundary.code));

        await populateBoundariesRecursively(
            boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0],
            boundaries,
            boundaryChildren[boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0]?.code],
            boundaryCodes,
            boundaryChildren
        );
        const setOfCampaignBoundaries = new Set();
        for (let i = 0; i < boundaries.length; i++) {
            setOfCampaignBoundaries.add(boundaries[i]?.code);
        }
        for (let i = 0; i < facilitySheetData.length; i++) {
            if (facilitySheetData[i]?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"]) {
                const boundariesAfterSplitAndTrim = facilitySheetData[i]?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"].split(",").map((boundary: any) => boundary.trim());
                boundariesAfterSplitAndTrim.forEach((boundary: any) => {
                    if (!setOfCampaignBoundaries.has(boundary)) {
                        errors.push({
                            row: i + 3,
                            message: `Boundary code ${boundary} is not part of this campaign.`
                        });
                    }
                });
            }
        }
        logger.info("Boundary validation completed.");
    }

    private static async getCampaignDetails(resourceDetails: any): Promise<any> {
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");
        return campaign;
    }
}
