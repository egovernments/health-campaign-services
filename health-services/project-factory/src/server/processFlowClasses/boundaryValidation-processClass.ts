import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { SheetMap } from "../models/SheetMap";
import { getBoundaryOnWhichWeSplit, getLocalizedName } from "../utils/campaignUtils";
import { validateTargetFromTargetConfigs } from "../validators/campaignValidators";
import { callMdmsSchema } from "../api/genericApis";
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { sheetDataRowStatuses } from "../config/constants";
import { getHierarchy } from "../api/campaignApis";

export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        const readMeSheetName = getLocalizedName("HCM_README_SHEETNAME", localizationMap);
        const campaignDetails = await this.getCampaignDetails(resourceDetails.tenantId, resourceDetails.campaignId);
        const boundarySchema = await this.getSchema(resourceDetails.tenantId, campaignDetails.projectType);
        const dynamicColumns = this.getDynamicColumns(boundarySchema);
        const requiredTargetColumns : string[] = [];
        const optionalTargetColumns : string[] = [];
        for(const key in dynamicColumns){
            if(dynamicColumns[key].isRequired){
                requiredTargetColumns.push(key);
            }
            else{
                optionalTargetColumns.push(key);
            }
        }
        for (const sheetName in wholeSheetData) {
            if (sheetName !== readMeSheetName) {
                const sheetData = wholeSheetData[sheetName];
                const errors: any[] = [];
                validateTargetFromTargetConfigs(sheetData, errors, requiredTargetColumns, optionalTargetColumns, localizationMap);
                this.processErrors(sheetData, errors, resourceDetails);
            }
        }
        const splitOn = await getBoundaryOnWhichWeSplit(resourceDetails.campaignId, resourceDetails.tenantId);
        const hierarchyDef = await getHierarchy(resourceDetails.tenantId, campaignDetails?.hierarchyType);
        const hierarchyAfterSplit = hierarchyDef.slice(hierarchyDef.indexOf(splitOn));
        const hierarchyDynamicColumnsLocalisedCodes: string[] = hierarchyAfterSplit.map(
            (h: string) => `${campaignDetails?.hierarchyType}_${h}`.toUpperCase()
          );
        const dynamicHierarchyColumns = buildHierarchyColumns(hierarchyDynamicColumnsLocalisedCodes);
        const commonDynamicColumns : any= {
            ...dynamicColumns,
            ...dynamicHierarchyColumns,
            ["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]: {
                color: "#f3842d",
                hideColumn: true,
                orderNumber: -1,
                freezeColumn: true
            }
        };

        const sheetMap: SheetMap = {};
        for(const sheets of templateConfig.sheets){
            const localisedSheetName = getLocalizedName(sheets.sheetName, localizationMap);
            if (localisedSheetName == readMeSheetName) continue;
            sheetMap[sheets.sheetName] = {
                data: wholeSheetData[localisedSheetName],
                dynamicColumns : {
                    ...commonDynamicColumns,
                    ["#status#"]: {
                        "color": "#ffff00",
                        orderNumber: 99
                    },
                    ["#errorDetails#"]: {
                        "color": "#ffff00",
                        orderNumber: 100
                    }
                }
            }
        }
        return sheetMap;

        function buildHierarchyColumns(keys: string[]): Record<string, any> {
            return keys.reduce((acc, key, idx) => {
                acc[key] = {
                    freezeColumn: true,
                    width: 60,
                    color: "#f3842d",
                    orderNumber: -1 * (keys.length - idx + 1)
                };
                return acc;
            }, {} as Record<string, any>);
        }
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

    private static getDynamicColumns(schema: any): Record<string, any> {
        const dynamicColumns: Record<string, any> = {};
        for (const key of Object.keys(schema?.properties || {})) {
            dynamicColumns[key] = schema.properties[key];
        }
        return dynamicColumns;
    }

    private static async getCampaignDetails(tenantId: string, campaignId: string) {
        const result = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        if (!result?.CampaignDetails?.[0]) throw new Error("Campaign not found");
        return result?.CampaignDetails?.[0];
    }

    private static async getSchema(tenantId: string, projectType: string) {
        const schemaName = `target-${projectType}`;
        return await callMdmsSchema(tenantId, schemaName);
    }
}