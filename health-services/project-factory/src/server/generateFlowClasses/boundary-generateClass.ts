import { logger } from "../utils/logger";
import { SheetMap } from "../models/SheetMap";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { callMdmsSchema } from "../api/genericApis";
import { getBoundaryOnWhichWeSplit, getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { getHierarchy } from "../api/campaignApis";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async generate(templateConfig: any, responseToSend: any, localizationMap: any): Promise<SheetMap> {
        logger.info("Generating template...");
        logger.info(`Response to send ${JSON.stringify(responseToSend)}`);

        const { tenantId, campaignId } = responseToSend;
        const campaignDetails = await this.getCampaignDetails(tenantId, campaignId);
        const schema = await this.getSchema(tenantId, campaignDetails.projectType);

        const {
            boundaries,
            splitOn,
            structuredBoundaries,
            localisedSolitOn,
            localisedHierarchyAfterSplit,
        } = await this.prepareBoundaryData(campaignDetails, tenantId, campaignId, localizationMap);

        const filteredBoundaries = this.getFilteredBoundaries(
            structuredBoundaries,
            splitOn,
            boundaries,
            localisedSolitOn,
            localisedHierarchyAfterSplit,
            localizationMap
        );

        const groupedBySheetName = this.groupBoundariesBySheetName(filteredBoundaries, localisedSolitOn, localisedHierarchyAfterSplit);
        const dynamicColumns = this.getLocalizedDynamicColumns(schema, localizationMap);

        return this.buildSheetMap(groupedBySheetName, dynamicColumns, localisedHierarchyAfterSplit);
    }

    private static async getCampaignDetails(tenantId: string, campaignId: string) {
        const result = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        return result?.CampaignDetails?.[0] || {};
    }

    private static async getSchema(tenantId: string, projectType: string) {
        const schemaName = `target-${projectType}`;
        return await callMdmsSchema(tenantId, schemaName);
    }

    private static async prepareBoundaryData(campaignDetails: any, tenantId: string, campaignId: string, localizationMap: any) {
        const splitOn = await getBoundaryOnWhichWeSplit(campaignId, tenantId);
        const relationship = await searchBoundaryRelationshipData(tenantId, campaignDetails?.hierarchyType, true, true, false);
        const rootBoundary = relationship?.TenantBoundary?.[0]?.boundary?.[0];

        const boundaries = campaignDetails?.boundaries || [];
        const boundaryChildren = Object.fromEntries(boundaries.map(({ code, includeAllChildren }: any) => [code, includeAllChildren]));
        const boundaryCodes = new Set(boundaries.map(({ code }: any) => code));

        await populateBoundariesRecursively(
            rootBoundary,
            boundaries,
            boundaryChildren[rootBoundary?.code],
            boundaryCodes,
            boundaryChildren
        );

        const structuredBoundaries = this.structureBoundaries(boundaries, campaignDetails?.hierarchyType, localizationMap);
        const hierarchyDef = await getHierarchy(tenantId, campaignDetails?.hierarchyType);
        const hierarchyAfterSplit = hierarchyDef.slice(hierarchyDef.indexOf(splitOn));

        const localisedSolitOn = getLocalizedName(`${campaignDetails?.hierarchyType}_${splitOn}`.toUpperCase(), localizationMap);
        const localisedHierarchyAfterSplit = hierarchyAfterSplit.map((h: string) =>
            getLocalizedName(`${campaignDetails?.hierarchyType}_${h}`.toUpperCase(), localizationMap)
        );

        return {
            boundaries,
            rootBoundary,
            boundaryCodes,
            boundaryChildren,
            splitOn,
            structuredBoundaries,
            hierarchyAfterSplit,
            localisedSolitOn,
            localisedHierarchyAfterSplit
        };
    }

    private static getFilteredBoundaries(
        structuredBoundaries: any[],
        splitOn: string,
        boundaries: any[],
        localisedSolitOn: string,
        localisedHierarchyAfterSplit: string[],
        localizationMap: any
    ) {
        const sheetNames = boundaries
            .filter((b: any) => b.type === splitOn)
            .map((b: any) => getLocalizedName(b.code, localizationMap));

        return structuredBoundaries
            .filter((b: any) => sheetNames.includes(b[localisedSolitOn]))
            .map((b: any) => {
                const filtered: any = {};
                for (const key of localisedHierarchyAfterSplit) {
                    if (key in b) filtered[key] = b[key];
                }
                return filtered;
            });
    }

    private static groupBoundariesBySheetName(boundaries: any[], localisedSolitOn: string, localisedHierarchyAfterSplit: string[]) {
        const lowestLevelKey = localisedHierarchyAfterSplit[localisedHierarchyAfterSplit.length - 1];
        return boundaries.reduce((acc: Record<string, any[]>, b: any) => {
            const sheetName = b[localisedSolitOn];
            const lowestValue = b[lowestLevelKey];
            if (!sheetName || !lowestValue || lowestValue.trim() === "") return acc;

            if (!acc[sheetName]) acc[sheetName] = [];
            acc[sheetName].push(b);
            return acc;
        }, {});
    }

    private static getLocalizedDynamicColumns(schema: any, localizationMap: any): Record<string, any> {
        const dynamicColumns: Record<string, any> = {};
        for (const key of Object.keys(schema?.properties || {})) {
            const localizedKey = getLocalizedName(key, localizationMap);
            dynamicColumns[localizedKey] = schema.properties[key];
        }
        return dynamicColumns;
    }

    private static buildSheetMap(
        groupedBySheetName: Record<string, any[]>,
        baseDynamicColumns: Record<string, any>,
        localisedHierarchyAfterSplit: string[]
    ): SheetMap {
        const sheetMap: SheetMap = {};
        for (const [sheetName, data] of Object.entries(groupedBySheetName)) {
            const dynamicColumns = { ...baseDynamicColumns };

            for (const key of localisedHierarchyAfterSplit) {
                dynamicColumns[key] = {
                    freezeColumn: true,
                    width: 60,
                    color: "#93c47d",
                    orderNumber: -1 * (localisedHierarchyAfterSplit.length - localisedHierarchyAfterSplit.indexOf(key))
                };
            }

            sheetMap[sheetName] = { dynamicColumns, data };
        }
        return sheetMap;
    }

    static structureBoundaries(boundaries: any[], hierarchyType: any, localizationMap: any) {
        const result: any = [];

        // Step 1: Index boundaries by code
        const codeToBoundary: Record<string, any> = {};
        for (const boundary of boundaries) {
            codeToBoundary[boundary.code] = { ...boundary, children: [] };
        }

        // Step 2: Build tree
        const roots: any[] = [];
        for (const boundary of boundaries) {
            if (boundary.parent) {
                codeToBoundary[boundary.parent].children.push(codeToBoundary[boundary.code]);
            } else {
                roots.push(codeToBoundary[boundary.code]);
            }
        }

        // Step 3: DFS traversal
        function traverse(node: any, path: any[] = []) {
            const entry: Record<string, string> = {};

            // Add main boundary code
            entry[getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", localizationMap)] = node.code;

            // Traverse current path
            const fullPath = [...path, node];
            for (const b of fullPath) {
                const localizedKey = getLocalizedName(`${hierarchyType}_${b.type}`.toUpperCase(), localizationMap);
                const localizedValue = getLocalizedName(b.code, localizationMap);
                entry[localizedKey] = localizedValue;
            }

            result.push(entry);

            for (const child of node.children) {
                traverse(child, fullPath);
            }
        }

        // Step 4: Start traversal from roots
        for (const root of roots) {
            traverse(root);
        }

        return result;
    }

}
