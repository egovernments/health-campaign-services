import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { SheetMap } from "../models/SheetMap";
import { getBoundaryOnWhichWeSplit, getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { validateTargetFromTargetConfigs } from "../validators/campaignValidators";
import { callMdmsSchema } from "../api/genericApis";
import { sheetDataRowStatuses } from "../config/constants";
import { getHierarchy } from "../api/campaignApis";
import { searchBoundaryRelationshipData } from "../api/coreApis";

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
        let requiredLowestBoundaries = await this.getRequiredLowestBoundaries(campaignDetails);
        let allLowestBoundariesInFromSheets : any[] = []
        const finalErrors : any[] = [];
        for (const sheetName in wholeSheetData) {
            if (sheetName !== readMeSheetName) {
                const sheetData = wholeSheetData[sheetName];
                const errors: any[] = [];
                validateTargetFromTargetConfigs(sheetData, errors, requiredTargetColumns, optionalTargetColumns, localizationMap);
                sheetData.forEach((row: any) => {
                    if(row["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]){
                        allLowestBoundariesInFromSheets.push(row["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"])
                    }
                })
                finalErrors.push(...errors);
                this.processErrors(sheetData, errors);
            }
        }
        this.erichErrorIfBoundariesMismatch(requiredLowestBoundaries, allLowestBoundariesInFromSheets, wholeSheetData, readMeSheetName, finalErrors);
        if (finalErrors.length > 0) {
            resourceDetails.additionalDetails = {
                ...resourceDetails.additionalDetails,
                sheetErrors: finalErrors
            };
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

    private static erichErrorIfBoundariesMismatch(
        requiredLowestBoundaries: any[],
        allLowestBoundariesInFromSheets: any[],
        wholeSheetData: any,
        readMeSheetName: string,
        finalErrors: any
    ) {
        const requiredSet = new Set(requiredLowestBoundaries);
        const sheetSet = new Set(allLowestBoundariesInFromSheets);

        const missingInSheets = requiredLowestBoundaries.filter(b => !sheetSet.has(b));
        const extraInSheets = allLowestBoundariesInFromSheets.filter(b => !requiredSet.has(b));
        const errors: any[] = [];
        if (missingInSheets.length > 0 || extraInSheets.length > 0) {
            const message = `Some boundaries are missing or extra in the sheet. Please use the generated template only.`;
            errors.push({ row: 3, message });
        }

        for (const sheetName in wholeSheetData) {
            if (sheetName !== readMeSheetName) {
                const sheetData = wholeSheetData[sheetName];
                this.processErrors(sheetData, errors);
                finalErrors.push(...errors);
            }
        }
    }
    

    private static async getRequiredLowestBoundaries(campaignDetails: any) {
        if(campaignDetails?.boundaries && campaignDetails?.boundaries?.length > 0 ){
            const relationship = await searchBoundaryRelationshipData(campaignDetails.tenantId, campaignDetails?.hierarchyType, true, true, false);
            const rootBoundary = relationship?.TenantBoundary?.[0]?.boundary?.[0];

            const boundaries = campaignDetails?.boundaries || [];
            const boundaryChildren = Object.fromEntries(boundaries.map(({ code, includeAllChildren }: any) => [code, includeAllChildren]));
            const boundaryCodes = new Set(boundaries.map(({ code }: any) => code));
            const hierarchyDef = await getHierarchy(campaignDetails.tenantId, campaignDetails?.hierarchyType);
            const lowestLevel = hierarchyDef[hierarchyDef.length - 1];

            await populateBoundariesRecursively(
                rootBoundary,
                boundaries,
                boundaryChildren[rootBoundary?.code],
                boundaryCodes,
                boundaryChildren
            );
            const lowestBoundaries = boundaries.filter((boundary: any) => boundary.type == lowestLevel);
            return lowestBoundaries.map((boundary: any) => boundary.code);
        }
        else{
            throw new Error("No boundaries found");
        }
    }

    private static processErrors(sheetData : any, errors : any[]) {
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