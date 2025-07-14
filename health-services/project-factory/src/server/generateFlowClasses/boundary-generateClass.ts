import { logger } from "../utils/logger";
import { SheetMap } from "../models/SheetMap";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { callMdmsSchema } from "../api/genericApis";
import { getBoundaryOnWhichWeSplit, getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { getHierarchy } from "../api/campaignApis";
import { getReadMeConfig, getRelatedDataWithCampaign } from "../utils/genericUtils";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async generate(templateConfig: any, responseToSend: any, localizationMap: any): Promise<SheetMap> {
        logger.info("Generating template...");
        logger.info(`Response to send: ${JSON.stringify(responseToSend)}`);

        const { tenantId, campaignId, type } = responseToSend;

        // Run async operations in parallel
        const [readMeConfig, campaignDetails] = await Promise.all([
            getReadMeConfig(tenantId, type),
            this.getCampaignDetails(tenantId, campaignId),
        ]);

        const boundarySchema = await this.getSchema(tenantId, campaignDetails.projectType);

        const sheetsConfig = templateConfig?.sheets?.[0];
        const readMeColumnHeader = Object.keys(sheetsConfig?.schema?.properties || {})?.[0];
        const readMeSheetName = sheetsConfig?.sheetName

        const readMeData = this.getReadMeData(readMeConfig, readMeColumnHeader, localizationMap);

        const {
            boundaries,
            splitOn,
            structuredBoundaries,
            localisedSolitOn,
            hierarchyAfterSplitCode,
        } = await this.prepareBoundaryData(campaignDetails, tenantId, campaignId, localizationMap);

        const [filteredBoundaries, schema] = await Promise.all([
            Promise.resolve(this.getFilteredBoundaries(
                structuredBoundaries,
                splitOn,
                boundaries,
                localisedSolitOn,
                hierarchyAfterSplitCode
            )),
            boundarySchema
        ]);

        const groupedBySheetName = this.groupBoundariesBySheetName(
            filteredBoundaries,
            localisedSolitOn,
            hierarchyAfterSplitCode,
            localizationMap
        );

        const dynamicColumns = this.getDynamicColumns(schema);
        const currentBoundaryData = await getRelatedDataWithCampaign(type, campaignDetails?.campaignNumber, tenantId);
        const boundaryCodeToDataMap = currentBoundaryData.reduce((acc: any, curr: any) => {
            const data = curr?.data;
            const code = data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            if (data && code) {
                acc[code] = data;
            }
            return acc;
        }, {});        

        return this.buildSheetMap(
            groupedBySheetName,
            boundaryCodeToDataMap,
            dynamicColumns,
            hierarchyAfterSplitCode,
            readMeData,
            readMeColumnHeader,
            readMeSheetName,
            localizationMap
        );
    }


    static getReadMeData(readMeConfig: any, readMeColumnHeader: any, localizationMap: any) {
        const dataArray = [];
        for (const text of readMeConfig?.texts) {
            if(!text?.inSheet) continue;
            dataArray.push({ [readMeColumnHeader]: "" });
            dataArray.push({ [readMeColumnHeader]: "" });
            let header = getLocalizedName(text.header, localizationMap);
            if (text.isHeaderBold) {
                header = `**${header}**`;
            }
            dataArray.push({
                [readMeColumnHeader]: header
            })
            for (const description of text.descriptions) {
                dataArray.push({
                    [readMeColumnHeader]: getLocalizedName(description.text, localizationMap)
                })
            }
        }
        logger.info(`Readme data prepared.`);
        return dataArray;
    }

    private static async getCampaignDetails(tenantId: string, campaignId: string) {
        const result = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        if(!result?.CampaignDetails?.[0]) throw new Error("Campaign not found");
        return result?.CampaignDetails?.[0];
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

        const localisedSolitOn = `${campaignDetails?.hierarchyType}_${splitOn}`.toUpperCase();
        const hierarchyAfterSplitCode = hierarchyAfterSplit.map((h: string) =>
            `${campaignDetails?.hierarchyType}_${h}`.toUpperCase()
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
            hierarchyAfterSplitCode
        };
    }

    private static getFilteredBoundaries(
        structuredBoundaries: any[],
        splitOn: string,
        boundaries: any[],
        localisedSolitOn: string,
        hierarchyAfterSplit: string[],
    ) {
        const sheetNames = boundaries
            .filter((b: any) => b.type === splitOn)
            .map((b: any) => b.code);
        const boundaryCodeKey = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
        return structuredBoundaries
            .filter((b: any) => sheetNames.includes(b[localisedSolitOn]))
            .map((b: any) => {
                const filtered: any = {};
                for (const key of hierarchyAfterSplit) {
                    if (key in b) filtered[key] = b[key];
                }
                filtered[boundaryCodeKey] = b[boundaryCodeKey];
                return filtered;
            });
    }

    private static groupBoundariesBySheetName(boundaries: any[], localisedSolitOn: string, localisedHierarchyAfterSplit: string[], localizationMap: any) {
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

    private static getDynamicColumns(schema: any): Record<string, any> {
        const dynamicColumns: Record<string, any> = {};
        for (const key of Object.keys(schema?.properties || {})) {
            dynamicColumns[key] = schema.properties[key];
        }
        return dynamicColumns;
    }

    private static buildSheetMap(
        groupedBySheetName: Record<string, any[]>,
        boundaryCodeToDataMap: Record<string, any[]>,
        baseDynamicColumns: Record<string, any>,
        localisedHierarchyAfterSplit: string[],
        readMeData: any[],
        readMeColumnHeader: string,
        readMeSheetName: string,
        localizationMap: any
    ): SheetMap {
        const sheetMap: SheetMap = {};
        const hierarchyDynamicColumns = buildHierarchyColumns(localisedHierarchyAfterSplit);
        const commonDynamicColumns = {
            ...baseDynamicColumns,
            ...hierarchyDynamicColumns,
            ["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]: {
                color: "#f3842d",
                hideColumn: true,
                orderNumber: -1,
                freezeColumn: true
            }
        };

        for (const [sheetName, data] of Object.entries(groupedBySheetName)) {
            const localisedData = data.map((d: any) => {
                const boundaryCode = d["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
                const targetData = boundaryCodeToDataMap[boundaryCode];
                d = { ...d, ...targetData };
                for(let key in d) {
                    if(key === "HCM_ADMIN_CONSOLE_BOUNDARY_CODE") continue;
                    d[key] = getLocalizedName(d[key], localizationMap);
                }
                return d;
            })
            // TEST : default target filled for all selected boundaries testing
            // localisedData.forEach((d: any) => {
            //     for(const col of Object.keys(baseDynamicColumns)) {
            //         if(!d[col]) d[col] = 100;
            //     }
            // })
            sheetMap[sheetName] = {
                dynamicColumns: { ...commonDynamicColumns },
                data : localisedData
            };
        }

        sheetMap[readMeSheetName] = {
            data: readMeData,
            dynamicColumns: {
                [readMeColumnHeader]: {
                    adjustHeight: true,
                    width: 120
                }
            }
        };

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
            entry["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] = node.code;

            // Traverse current path
            const fullPath = [...path, node];
            for (const b of fullPath) {
                entry[`${hierarchyType}_${b.type}`.toUpperCase()] = b.code;
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
