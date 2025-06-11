import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { SheetMap } from "../models/SheetMap";
import { defaultRequestInfo, searchBoundaryRelationshipData, searchMDMSDataViaV2Api } from "../api/coreApis";
import { getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign } from "../utils/genericUtils";
import { dataRowStatuses, mappingStatuses } from "../config/constants";
import { logger } from "../utils/logger";
import { enrichProjectDetailsFromCampaignDetails } from "../utils/transforms/projectTypeUtils";
import { confirmProjectParentCreation } from "../api/campaignApis";
import { httpRequest } from "../utils/request";
import { fetchProjectsWithBoundaryCodeAndReferenceId } from "../utils/onGoingCampaignUpdateUtils";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";

export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        await validateResourceDetailsBeforeProcess("boundaryValidation", resourceDetails, localizationMap);
        logger.info("Processing file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);
        const {
            campaignDetails,
            boundaries,
            targetColumns,
            targetConfig,
            campaignNumber
        } = await this.prepareTargetConfigAndBoundaryInfo(resourceDetails);

        const datas = this.extractDatasFromSheets(wholeSheetData, localizationMap, targetColumns);

        this.enrichDatasForParents(boundaries, datas);

        let currentBoundaryData = await getRelatedDataWithCampaign(resourceDetails?.type, campaignNumber);
        await this.persistNewBoundaryData(currentBoundaryData, datas, campaignNumber, resourceDetails);
        await this.updateBoundaryData(currentBoundaryData, datas);
        
        let currentMappingData = await getMappingDataRelatedToCampaign("resource", campaignNumber);
        let mappingDataFromSheet = this.extractMappingDataFromSheets(boundaries, "resource", campaignDetails);

        await this.persistMappingData(currentMappingData, mappingDataFromSheet, resourceDetails);
        currentBoundaryData = await getRelatedDataWithCampaign(resourceDetails?.type, campaignNumber);
        currentBoundaryData.push(...await getRelatedDataWithCampaign(resourceDetails?.type, campaignNumber));
        await this.createAndUpdateProjects(currentBoundaryData, campaignDetails, boundaries, targetConfig);
        return {};
    }

    private static async persistMappingData(currentMappingData: any[], mappingDataFromSheet: any[], resourceDetails: any) {
        logger.info("Persisting mapping data...");
        let setOfCombinationForPvarBoundary = new Set();
        for(let i = 0; i < currentMappingData.length; i++) {
            setOfCombinationForPvarBoundary.add(`${currentMappingData[i]?.uniqueIdentifierForData}-${currentMappingData[i]?.boundaryCode}`);
        }
        let mappingDatasToBePersisted = [];
        for(let i = 0; i < mappingDataFromSheet.length; i++) {
            if(!setOfCombinationForPvarBoundary.has(`${mappingDataFromSheet[i]?.uniqueIdentifierForData}-${mappingDataFromSheet[i]?.boundaryCode}`)) {
                mappingDatasToBePersisted.push(mappingDataFromSheet[i]);
            }
        }
        let batchSize = 100;
        for (let i = 0; i < mappingDatasToBePersisted.length; i += batchSize) {
            const batch = mappingDatasToBePersisted.slice(i, i + batchSize);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_SAVE_MAPPING_DATA_TOPIC);
        }
        let waitTime = Math.max(5000, mappingDatasToBePersisted?.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));
    }

    private static extractMappingDataFromSheets(boundaries: any[], type: string, campaignDetails: any): any[] {
        logger.info("Extracting mapping data from sheets...");
        // Initialize sets for unique boundary codes and product variant IDs
        const boundaryCodes = new Set();
        const allSheetMappingResourceData: any[] = [];

        // Extract boundary codes
        for (const boundary of boundaries) {
            if (boundary?.code) {
                boundaryCodes.add(boundary.code);
            }
        }

        // Convert Set to array
        const boundaryCodesArray = Array.from(boundaryCodes);

        // Extract product variant IDs from cycleData
        const setOfAllProductVariantIds = new Set();
        const deliveryRules = campaignDetails?.deliveryRules;
        if (deliveryRules && Array.isArray(deliveryRules) && deliveryRules.length > 0) {
            for (const deliveryRule of deliveryRules) {
                const products = deliveryRule?.resources;
                if (products) {
                    for (const product of products) {
                        setOfAllProductVariantIds.add(product?.productVariantId); // Add pvar ID to the Set
                    }
                }
            }
        }

        // Create mapping data for each combination of product variant ID and boundary code
        const productVariantIds = Array.from(setOfAllProductVariantIds);
        for (let i = 0; i < productVariantIds.length; i++) {
            const productVariantId = productVariantIds[i];
            for (let j = 0; j < boundaryCodesArray.length; j++) {
                const boundaryCode = boundaryCodesArray[j];
                const newMappingData = {
                    campaignNumber: campaignDetails?.campaignNumber ?? '',
                    type,
                    boundaryCode,
                    uniqueIdentifierForData: productVariantId,
                    status: mappingStatuses.toBeMapped,
                    mappingId : null
                };
                allSheetMappingResourceData.push(newMappingData);
            }
        }
        logger.info(`Extracted mapping data from sheets.`);
        return allSheetMappingResourceData;
    }

    private static async createAndUpdateProjects(currentBoundaryData: any[], campaignDetails: any, boundaries: any, targetConfig: any) {
        const boundaryChildrenToTypeAndParentMap: any = this.getBoundaryChildrenToTypeAndParentMap(boundaries, currentBoundaryData);
        const { projectCreateBody, Projects } = await this.prepareProjectCreationContext(campaignDetails);
        const sortedBoundaryData = this.topologicallySortBoundaries(currentBoundaryData, boundaryChildrenToTypeAndParentMap);
        const sortedBoundaryDataForCreate = sortedBoundaryData.filter((d: any) => !d?.uniqueIdAfterProcess && (d?.status == dataRowStatuses.pending || d?.status == dataRowStatuses.failed));
        const sortedBoundaryDataForUpdate = sortedBoundaryData.filter((d: any) => d?.uniqueIdAfterProcess && (d?.status == dataRowStatuses.pending || d?.status == dataRowStatuses.failed));
        const useruuid = campaignDetails?.auditDetails?.createdBy;
        await this.processProjectCreationInOrder(sortedBoundaryDataForCreate, campaignDetails?.tenantId, campaignDetails?.campaignNumber, targetConfig, projectCreateBody, Projects, boundaryChildrenToTypeAndParentMap, useruuid);
        await this.processProjectUpdateInOrder(sortedBoundaryDataForUpdate, campaignDetails?.tenantId, campaignDetails?.campaignNumber, targetConfig, useruuid);
    }



    private static getBoundaryChildrenToTypeAndParentMap( boundaries: any[], currentBoundaryData: any[] ) {
        const boundaryToProjectId = currentBoundaryData.reduce((acc: Record<string, string>, boundary: any) => {
            const code = boundary?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            const id = boundary?.uniqueIdAfterProcess;

            if (code && id) {
                acc[code] = id;
            }

            return acc;
        }, {});
        
        const boundaryChildrenToTypeAndParentMap = boundaries.reduce(
            (acc: any, boundary: any) => {
                const code = boundary?.code;
                if (code) {
                    acc[code] = {
                        type: boundary?.type || "",
                        parent: boundary?.parent || null,
                        projectId: boundaryToProjectId[code] || null
                    };
                }
                return acc;
            },
            {}
        );
        return boundaryChildrenToTypeAndParentMap;
    }

    private static async prepareProjectCreationContext(campaignDetails: any) {

        const MdmsCriteria : any = {
            tenantId: campaignDetails?.tenantId,
            schemaCode: "HCM-PROJECT-TYPES.projectTypes",
            filters: {
                code: campaignDetails?.projectType
            }
        };

        const mdmsResponse = await searchMDMSDataViaV2Api(MdmsCriteria, true);
        if (!mdmsResponse?.mdms?.[0]?.data) {
            throw new Error(`Error in fetching project types from mdms`);
        }

        const Projects = enrichProjectDetailsFromCampaignDetails(campaignDetails, mdmsResponse?.mdms?.[0]?.data);
        const projectCreateBody = {
            RequestInfo: { ...defaultRequestInfo?.RequestInfo, userInfo: { uuid: campaignDetails?.auditDetails?.createdBy } },
            Projects
        };

        return { projectCreateBody, Projects };
    }
    
    
    private static topologicallySortBoundaries(currentBoundaryData: any[], boundaryMap: Record<string, { parent: string | null }>) {
        const graph: Record<string, string[]> = {};
        const inDegree: Record<string, number> = {};

        for (const bd of currentBoundaryData) {
            const code = bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            const parent : any = boundaryMap?.[code]?.parent;

            if (!graph[parent]) graph[parent] = [];
            graph[parent].push(code);

            inDegree[code] = (inDegree[code] || 0) + 1;
            if (!(parent in inDegree)) inDegree[parent] = 0;
        }

        const queue = Object.entries(inDegree)
            .filter(([_, deg]) => deg === 0)
            .map(([code]) => code);

        const sortedCodes: string[] = [];
        while (queue.length) {
            const current = queue.shift();
            if (!current || current === "undefined") continue;
            sortedCodes.push(current);
            for (const neighbor of graph[current] || []) {
                inDegree[neighbor]--;
                if (inDegree[neighbor] === 0) queue.push(neighbor);
            }
        }

        const codeToDataMap = Object.fromEntries(
            currentBoundaryData.map(bd => [bd?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"], bd])
        );

        return sortedCodes.map(code => codeToDataMap[code]).filter(Boolean);
    }

    private static async processProjectUpdateInOrder(
        sortedBoundaryData: any[],
        tenantId: string,
        campaignNumber: string,
        targetConfig: any,
        useruuid: string
    ) {
        for (const boundaryData of sortedBoundaryData) {
            const data = boundaryData?.data;
            const boundaryCode = data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            try {
                const projectSearchResponse =
                    await fetchProjectsWithBoundaryCodeAndReferenceId(
                        boundaryCode,
                        tenantId,
                        campaignNumber,
                        defaultRequestInfo?.RequestInfo
                    );
                const projectToUpdate = projectSearchResponse?.Project?.[0];
                const targetMap: Record<string, number> = {};

                for (const beneficiary of targetConfig.beneficiaries) {
                    for (const col of beneficiary.columns) {
                        const value = data[col];
                        if (value == 0 || value) {
                            targetMap[beneficiary.beneficiaryType] = (targetMap[beneficiary.beneficiaryType] || 0) + value;
                        } else {
                            logger.warn(`Target missing for beneficiary ${beneficiary.beneficiaryType}, column ${col}, boundary ${boundaryCode}`);
                        }
                    }
                }
                if(projectToUpdate?.targets?.length > 0) {
                    for (const target of projectToUpdate?.targets) {
                        const beneficiaryType = target?.beneficiaryType;
                        if(targetMap[beneficiaryType]) {
                            target.targetNo = targetMap[beneficiaryType];
                        }
                    }
                }

                for(const key in targetMap) {
                    if(!projectToUpdate?.targets?.find((target: any) => target.beneficiaryType === key)) {
                        projectToUpdate.targets.push({
                            beneficiaryType: key,
                            targetNo: targetMap[key]
                        });
                    }
                }
                const RequestInfo = JSON.parse(JSON.stringify(defaultRequestInfo?.RequestInfo));
                RequestInfo.userInfo.uuid = useruuid;
                const response = await httpRequest(
                    config.host.projectHost + config.paths.projectUpdate,
                    {
                        RequestInfo,
                        Projects: [projectToUpdate]
                    },
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    true
                );

                const updatedProjectId = response?.Project?.[0]?.id;
                if (updatedProjectId) {
                    logger.info(`Project updated successfully for boundary ${boundaryCode}`);
                    boundaryData.status = dataRowStatuses.completed;
                    await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
                }
                else {
                    throw new Error(`Failed to update project for boundary ${boundaryCode}`);
                }
            } catch (error) {
                console.error(`Error while updating project for boundary ${boundaryCode}: ${error}`);
                boundaryData.status = dataRowStatuses.failed;
                await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
                throw error;
            }
        }
    }
    
    
    private static async processProjectCreationInOrder(
        sortedBoundaryData: any[],
        tenantId: string,
        camapignNumber: string,
        targetConfig: any,
        projectCreateBody: any,
        Projects: any,
        boundaryMap: Record<string, { type: string; parent: string | null; projectId?: string }>,
        useruuid: string
    ) {
        for (const boundaryData of sortedBoundaryData) {
            const data = boundaryData?.data;
            const boundaryCode = data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            try {
                Projects[0].address = {
                    tenantId,
                    boundary: boundaryCode,
                    boundaryType: boundaryMap[boundaryCode]?.type,
                };

                const parent = boundaryMap?.[boundaryCode]?.parent;
                if (parent && boundaryMap?.[parent]?.projectId) {
                    const parentProjectId = boundaryMap?.[parent]?.projectId;
                    Projects[0].parent = parentProjectId;
                    if(!config.values.skipParentProjectConfirmation) {
                        await confirmProjectParentCreation(tenantId, useruuid, parentProjectId);
                    }
                }
                else if (parent && !boundaryMap?.[parent]?.projectId) {
                    throw new Error(`Parent ${parent} of boundary ${boundaryCode} not found in boundaryMap`);
                }
                else {
                    Projects[0].parent = null;
                }

                Projects[0].referenceID = camapignNumber;
                const targetMap: Record<string, number> = {};

                for (const beneficiary of targetConfig.beneficiaries) {
                    for (const col of beneficiary.columns) {
                        const value = data[col];
                        if (value == 0 || value) {
                            targetMap[beneficiary.beneficiaryType] = (targetMap[beneficiary.beneficiaryType] || 0) + value;
                        } else {
                            logger.warn(`Target missing for beneficiary ${beneficiary.beneficiaryType}, column ${col}, boundary ${boundaryCode}`);
                        }
                    }
                }

                Projects[0].targets = Object.entries(targetMap).map(([key, val]) => ({
                    beneficiaryType: key,
                    targetNo: val
                }));

                const response = await httpRequest(
                    config.host.projectHost + config.paths.projectCreate,
                    projectCreateBody,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    true
                );

                const createdProjectId = response?.Project?.[0]?.id;
                if (createdProjectId) {
                    logger.info(`✅ Project created: ${response?.Project[0]?.name} for boundary ${boundaryCode}`);
                    boundaryMap[boundaryCode].projectId = createdProjectId;
                    boundaryData.uniqueIdAfterProcess = createdProjectId;
                    boundaryData.status = dataRowStatuses.completed;
                    await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
                }
                else {
                    throw new Error(`Failed to create project for boundary ${boundaryCode}`);
                }
            } catch (error) {
                console.error(`Error creating project for boundary ${boundaryCode}: ${error}`);
                boundaryData.status = dataRowStatuses.failed;
                await produceModifiedMessages({ datas: [boundaryData] }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
                throw error;
            }
        }
    }
    

    private static async prepareTargetConfigAndBoundaryInfo(resourceDetails: any) {
        const { campaignId, tenantId } = resourceDetails;

        const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        const campaignDetails = campaignResp?.CampaignDetails?.[0];
        if (!campaignDetails) throw new Error("Campaign not found");

        const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(
            tenantId, campaignDetails?.hierarchyType, true, true, false
        );

        const boundaries = campaignDetails?.boundaries || [];

        const boundaryChildren: Record<string, boolean> = boundaries.reduce((acc: any, boundary: any) => {
            acc[boundary.code] = boundary.includeAllChildren;
            return acc;
        }, {});
        const boundaryCodes: any = new Set(boundaries.map((b: any) => b.code));

        await populateBoundariesRecursively(
            boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0],
            boundaries,
            boundaryChildren[boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0]?.code],
            boundaryCodes,
            boundaryChildren
        );

        const { projectType, campaignNumber } = campaignDetails;

        const MdmsCriteria : any = {
            tenantId,
            schemaCode: "HCM-ADMIN-CONSOLE.targetConfigs",
            uniqueIdentifiers: [projectType]
        };
        const response = await searchMDMSDataViaV2Api(MdmsCriteria, true);
        if (!response?.mdms?.[0]?.data) {
            throw new Error(`Target Config not found for ${projectType}`);
        }

        const targetConfig = response.mdms[0].data;
        const targetColumns: string[] = [];
        for (const beneficiary of targetConfig.beneficiaries) {
            for (const col of beneficiary.columns) {
                targetColumns.push(col);
            }
        }

        return { campaignDetails, boundaries, targetColumns, targetConfig, campaignNumber };
    }
    
    private static extractDatasFromSheets(wholeSheetData: any, localizationMap: Record<string, string>, targetColumns: string[]) {
        const datas: any[] = [];
        const readMeSheetName = getLocalizedName("HCM_README_SHEETNAME", localizationMap);

        for (const sheetName in wholeSheetData) {
            if (sheetName !== readMeSheetName) {
                const sheetData = wholeSheetData[sheetName];
                for (const row of sheetData) {
                    const boundaryCode = row["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
                    const data: any = { ["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]: boundaryCode };

                    for (const col of targetColumns) {
                        const target = row[col];
                        data[col] = target || 0;
                    }

                    datas.push(data);
                }
            }
        }

        return datas;
    }
    
    

    private static enrichDatasForParents(boundaries: any[], datas: any[]) {
        const codeToChildren: Record<string, string[]> = {};
        const codeToTarget: Record<string, Record<string, number>> = {};
        const rootBoundaryCode = boundaries.find((b: any) => !b.parent)?.code;

        // Step 1: Build parent → children map
        for (const b of boundaries) {
            if(!b.parent) continue;
            if (!codeToChildren[b.parent]) codeToChildren[b.parent] = [];
            codeToChildren[b.parent].push(b.code);
        }

        // Step 2: Initialize data for leaf nodes
        for (const d of datas) {
            const code = d.HCM_ADMIN_CONSOLE_BOUNDARY_CODE;
            codeToTarget[code] = {};

            for (const key in d) {
                if (key === "HCM_ADMIN_CONSOLE_BOUNDARY_CODE") continue;
                const val = Number(d[key]);
                if (!isNaN(val)) codeToTarget[code][key] = val;
            }
        }

        // Step 3: DFS function to aggregate children's data
        const dfs = (code: string): Record<string, number> => {
            const result: Record<string, number> = { ...(codeToTarget[code] || {}) };

            for (const child of codeToChildren[code] || []) {
                const childData = dfs(child);
                for (const key in childData) {
                    result[key] = (result[key] || 0) + childData[key];
                }
            }

            codeToTarget[code] = result;
            return result;
        };

        // Step 4: DFS traversal
        dfs(rootBoundaryCode);

        // Step 5: Convert aggregated map back to datas array
        for (const code in codeToTarget) {
            if (!datas.find(d => d.HCM_ADMIN_CONSOLE_BOUNDARY_CODE === code)) {
                datas.push({
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE: code,
                    ...codeToTarget[code],
                });
            }
        }
    }
    
    


    private static async persistInBatches(datas: any[], topic: string): Promise<void> {
        const BATCH_SIZE = 100;
        for (let i = 0; i < datas.length; i += BATCH_SIZE) {
            const batch = datas.slice(i, i + BATCH_SIZE);
            await produceModifiedMessages({ datas: batch }, topic);
        }
        const waitTime = Math.max(5000, datas.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));
    }

    private static async persistNewBoundaryData(currentBoundaryData: any[], datas: any[], campaignNumber: string, resourceDetails: any) {
        const setOfAlreadyAddedBoundaryCodes = new Set(currentBoundaryData.map((d: any) => d?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]));
        const newEntries = datas.filter((d: any) => !setOfAlreadyAddedBoundaryCodes.has(d?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]));
        if(newEntries.length == 0) {
            return;
        }
        const dataToPersist = newEntries.map((d: any) => {
            return {
                campaignNumber,
                data : d,
                type: resourceDetails?.type,
                uniqueIdentifier: d?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"],
                uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending
            };
        })
        await this.persistInBatches(dataToPersist, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC);
    }

    private static async updateBoundaryData(currentBoundaryData : any[], datas: any[]) {
        const mapOfBoundayVsData = new Map(datas.map((d: any) => [d?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"], d]));
        const updatedEntries = [];
        for(const entry of currentBoundaryData) {
            const data = mapOfBoundayVsData.get(entry?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]);
            if(data) {
                const entryData = entry?.data;
                let isDataChanged = false
                for(const key in data) {
                    if (entryData[key] != data[key]) {
                        entryData[key] = data[key];
                        isDataChanged = true;
                    }
                }
                if(isDataChanged) {
                    entry.status = dataRowStatuses.pending;
                    updatedEntries.push(entry);
                }
            }
        }
        if(updatedEntries.length == 0) {
            return;
        }
        await this.persistInBatches(updatedEntries, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
    }
}