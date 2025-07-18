import { getReadMeConfig, getRelatedDataWithCampaign } from "../utils/genericUtils";
import { SheetMap } from "../models/SheetMap";
import { getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { searchBoundaryRelationshipData, searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { logger } from "../utils/logger";
import { getAllFacilities } from "../api/campaignApis";
import { dataRowStatuses } from "../config/constants";
import { DataTransformer } from "../utils/transFormUtil";
import { transformConfigs } from "../config/transformConfigs";
import Ajv from "ajv";
export class TemplateClass {

    static async generate(templateConfig: any, responseToSend: any, localizationMap: any): Promise<SheetMap> {
        logger.info("Generating template...");
        logger.info(`Response to send ${JSON.stringify(responseToSend)}`);
        const campaignDetailsResponse: any = await searchProjectTypeCampaignService({ tenantId: responseToSend.tenantId, ids: [responseToSend?.campaignId] });
        if (!campaignDetailsResponse?.CampaignDetails?.[0]) throw new Error("Campaign not found");
        const campaignDetails: any = campaignDetailsResponse?.CampaignDetails?.[0];
        const readMeConfig = await getReadMeConfig(responseToSend.tenantId, responseToSend.type);
        const readMeSchema = templateConfig?.sheets?.filter((s: any) => s?.sheetName === "HCM_README_SHEETNAME")[0]?.schema;
        const readMeColumnHeader = Object.keys(readMeSchema?.properties || {})?.[0];
        const readMeData: any = this.getReadMeData(readMeConfig, readMeColumnHeader, localizationMap);
        const allPermanentFacilities = await getAllFacilities(responseToSend?.tenantId);
        const completedFacilitiesRow = await getRelatedDataWithCampaign(responseToSend.type, campaignDetails.campaignNumber, responseToSend?.tenantId, dataRowStatuses.completed);
        const permanentCodes = new Set(
            allPermanentFacilities.map(f => f?.id)
        );

        // Filter current facilities that are NOT already in permanent
        const newFacilities = completedFacilitiesRow?.filter((f: any) =>
            !permanentCodes.has(f.data?.HCM_ADMIN_CONSOLE_FACILITY_CODE)
        );

        const permanentCompletedFacilitiesFromDB = completedFacilitiesRow.filter(
            (f: any) => permanentCodes.has(f?.data?.HCM_ADMIN_CONSOLE_FACILITY_CODE) // if f.uniqueIdentifier is facility id
        );

        const dbFacilityUniqueIdentifierToDataMap = new Map(
            permanentCompletedFacilitiesFromDB.map((f: any) => [f.uniqueIdentifier, f.data])
        );


        // Generate final data
        const { structuredBoundaries: boundaryData, codesOfBoundaries }: any = await this.getBoundaryData(campaignDetails, localizationMap);
        const faciltySchema = templateConfig?.sheets?.filter((s: any) => s?.sheetName === "HCM_ADMIN_CONSOLE_FACILITIES")[0]?.schema;
        const allPermanentFacilitiesTransformed: any = await this.getFacilityData(allPermanentFacilities, codesOfBoundaries, dbFacilityUniqueIdentifierToDataMap, faciltySchema);
        const facilityData = [...allPermanentFacilitiesTransformed, ...newFacilities?.map((f: any) => f.data)]
        const boundaryDynamicColumns: any = await this.getBoundaryDynamicColumns(campaignDetails?.tenantId, campaignDetails?.hierarchyType);
        const sheetMap: SheetMap = {
            [templateConfig?.sheets?.[0]?.sheetName]: {
                data: readMeData,
                dynamicColumns: {
                    [readMeColumnHeader]: {
                        adjustHeight: true,
                        width: 120
                    }
                }
            },
            ["HCM_ADMIN_CONSOLE_FACILITIES"]: {
                data: facilityData,
                dynamicColumns: null
            },
            ["HCM_ADMIN_CONSOLE_BOUNDARY_DATA"]: {
                data: boundaryData,
                dynamicColumns: boundaryDynamicColumns
            }
        }; // Initialize the SheetMap object
        logger.info(`SheetMap generated for template of type ${responseToSend.type}.`);
        return sheetMap;
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
        const structuredBoundaries = this.structureBoundaries(boundaries, campaignDetails?.hierarchyType, localizationMap);
        const codesOfBoundaries = new Set(boundaries.map((b: any) => b.code));
        logger.info(`Structured boundaries prepared.`);
        return { structuredBoundaries, codesOfBoundaries };
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
                const localizedValue = getLocalizedName(b.code, localizationMap);
                entry[`${hierarchyType}_${b.type}`.toUpperCase()] = localizedValue;
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

    static async getFacilityData(allFacilities: any, codesOfBoundaries: any, dbFacilityMap: Map<string, any>, schema: any) {
        const transformer = new DataTransformer(transformConfigs.Facility);
        let allFacilitiesRecursed = allFacilities.map((facility: any) => {
            return {
                Facility: facility
            }
        })
        const data = await transformer.reverseTransform(allFacilitiesRecursed);
        const ajv = new Ajv({ allErrors: true, strict: false });
        const validate = ajv.compile(schema);

        const result = data
            .filter((d: any) => {
                const boundaryCode = d?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"];
                return codesOfBoundaries.has(boundaryCode);
            })
            .map((d: any) => {
                const facilityName = d?.["HCM_ADMIN_CONSOLE_FACILITY_NAME"];
                const transformedUsage = d?.["HCM_ADMIN_CONSOLE_FACILITY_USAGE"];

                const dbData = dbFacilityMap.get(facilityName);

                if (!transformedUsage) {
                    const usageFromDB = dbData?.["HCM_ADMIN_CONSOLE_FACILITY_USAGE"];
                    d["HCM_ADMIN_CONSOLE_FACILITY_USAGE"] = usageFromDB ?? "Inactive";
                }

                if (dbData) {
                    d["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] = dbData?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"];
                }

                return d;
            })
            .filter((d: any) => {
                // Only keep valid records
                const isValid = validate(d);
                return isValid;
            });

        return result;
    }
}