import { getReadMeConfig, getRelatedDataWithCampaign, throwError } from "../utils/genericUtils";
import { SheetMap } from "../models/SheetMap";
import { getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { searchBoundaryRelationshipData, searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { logger } from "../utils/logger";
import { processStatuses, allProcesses, dataRowStatuses } from "../config/constants";

/**
 * Template generator for Attendance Register
 * Generates Excel template with:
 * 1. README sheet with instructions
 * 2. Attendance Register List sheet (empty for user fill)
 * 3. Boundary hierarchy data for dropdown population
 *
 * Precondition: Project creation must be complete
 */
export class TemplateClass {
    static async generate(templateConfig: any, responseToSend: any, localizationMap: any): Promise<SheetMap> {
        logger.info("Generating attendance register template...");
        logger.info(`Input payload: ${JSON.stringify(responseToSend)}`);

        const { tenantId, type, campaignId } = responseToSend;

        // Precondition: Verify project creation is complete
        await this.verifyProjectCreationComplete(campaignId, tenantId);

        // Fetch campaign details
        const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        const campaignDetails = campaignResp?.CampaignDetails?.[0];
        if (!campaignDetails) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", "Campaign not found");
        }

        const { hierarchyType } = campaignDetails;

        // Localized keys for sheet names and column headers
        const templateSheetForReadMe = templateConfig?.sheets?.[0];
        const readMeHeaderKey = Object.keys(templateSheetForReadMe?.schema?.properties || {})[0];

        // Prepare ReadMe sheet
        const readMeConfig = await getReadMeConfig(tenantId, type);
        const readMeData = this.getReadMeData(readMeConfig, readMeHeaderKey, localizationMap);

        // Prepare Boundary sheet for dropdown population
        const boundaryData = await this.getBoundaryData(campaignDetails, localizationMap);
        const boundaryDynamicColumns = await this.getBoundaryDynamicColumns(tenantId, hierarchyType);

        // Prepare Attendance Register List sheet — populate from campaign_data if registers exist
        const existingRegisterRows = await getRelatedDataWithCampaign(
            "attendanceRegister", campaignDetails.campaignNumber, tenantId, dataRowStatuses.completed
        );
        const attendanceRegisterData = existingRegisterRows.map((r: any) => r.data || {});
        logger.info("Loaded {} existing attendance registers from campaign_data for template", attendanceRegisterData.length);

        // Construct the final SheetMap
        const sheetMap: SheetMap = {
            [templateSheetForReadMe?.sheetName]: {
                data: readMeData,
                dynamicColumns: {
                    [readMeHeaderKey]: { adjustHeight: true, width: 120 }
                }
            },
            ["HCM_ADMIN_CONSOLE_BOUNDARY_DATA"]: {
                data: boundaryData,
                dynamicColumns: boundaryDynamicColumns
            },
            ["HCM_ATTENDANCE_REGISTER_LIST"]: {
                data: attendanceRegisterData,
                dynamicColumns: null
            }
        };

        logger.info(`SheetMap generated for attendance register template`);
        return sheetMap;
    }

    /**
     * Precondition check: Verify project creation is complete
     * Throws error if project creation process is not completed
     */
    private static async verifyProjectCreationComplete(campaignId: string, tenantId: string): Promise<void> {
        try {
            const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
            const campaign = campaignResp?.CampaignDetails?.[0];

            if (!campaign) {
                throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", "Campaign not found");
            }

            const processes = campaign.processes || [];
            const projectCreationProcess = processes.find(
                (p: any) => p.processName === allProcesses.projectCreation
            );

            if (!projectCreationProcess || projectCreationProcess.status !== processStatuses.completed) {
                logger.warn("Project creation not complete for campaign: {}", campaignId);
                throwError(
                    "CAMPAIGN",
                    400,
                    "PROJECT_CREATION_NOT_COMPLETE",
                    "Projects must be created before generating attendance register template"
                );
            }

            logger.info("Project creation verified as complete for campaign: {}", campaignId);
        } catch (error: any) {
            logger.error("Error verifying project creation: {}", error?.message);
            throw error;
        }
    }

    static getReadMeData(readMeConfig: any, readMeColumnHeader: any, localizationMap: any) {
        const dataArray = [];
        for (const text of readMeConfig?.texts) {
            if (!text?.inSheet) continue;
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

    static async getBoundaryData(campaignDetails: any, localizationMap: any) {
        const tenantId = campaignDetails?.tenantId;
        const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(
            tenantId, campaignDetails?.hierarchyType, true, true, false
        );
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

        const structuredBoundaries = this.structureBoundaries(boundaries, campaignDetails?.hierarchyType, localizationMap);
        logger.info(`Structured boundaries prepared.`);
        return structuredBoundaries;
    }

    static structureBoundaries(boundaries: any[], hierarchyType: any, localizationMap: any) {
        const result: any = [];

        // Step 1: Index boundaries by code - O(n)
        const codeToBoundary: Record<string, any> = {};
        for (const boundary of boundaries) {
            codeToBoundary[boundary.code] = { ...boundary, children: [] };
        }

        // Step 2: Build tree - O(n)
        const roots: any[] = [];
        for (const boundary of boundaries) {
            if (boundary.parent) {
                codeToBoundary[boundary.parent].children.push(codeToBoundary[boundary.code]);
            } else {
                roots.push(codeToBoundary[boundary.code]);
            }
        }

        // Step 3: DFS traversal - O(n)
        function traverse(node: any, path: any[] = []) {
            const entry: Record<string, string> = {};

            // Add main boundary code
            entry["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] = node.code;

            // Traverse current path
            const fullPath = [...path, node];
            for (const b of fullPath) {
                const key = `${hierarchyType}_${b.type}`.toUpperCase();
                const localizedValue = getLocalizedName(b.code, localizationMap);
                entry[key] = localizedValue;
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

    static async getBoundaryDynamicColumns(tenantId: any, hierarchyType: any) {
        const response = await searchBoundaryRelationshipDefinition({
            BoundaryTypeHierarchySearchCriteria: {
                tenantId: tenantId,
                hierarchyType: hierarchyType
            }
        });

        if (response?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.length > 0) {
            const boundaryTypes = response.BoundaryHierarchy[0].boundaryHierarchy.map(
                (hierarchy: any) => hierarchy?.boundaryType
            );

            const total = boundaryTypes.length;
            const result: Record<string, any> = {};

            boundaryTypes.forEach((type: string, index: number) => {
                const key = `${hierarchyType}_${type}`.toUpperCase();
                result[key] = { orderNumber: -1 * (total - index), adjustHeight: true, color: '#f3842d', freezeColumn: true };
            });
            result["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] = { adjustHeight: true, width: 80, freezeColumn: true };
            logger.info(`Dynamic columns prepared for boundary data.`);
            return result;
        } else {
            throw new Error("Boundary Hierarchy not found");
        }
    }
}
