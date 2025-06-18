import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { dataRowStatuses, mappingStatuses, sheetDataRowStatuses } from "../config/constants";
import { DataTransformer } from "../utils/transFormUtil";
import { transformConfigs } from "../config/transformConfigs";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";
import { httpRequest } from "../utils/request";
import { defaultRequestInfo } from "../api/coreApis";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import { executeQuery } from "../utils/db";


interface CampaignMappingRow {
    campaignNumber: string;
    type: string;
    uniqueIdentifierForData: string;
    boundaryCode: string;
    mappingId?: string;
    status: string;
}

// const STATUS_TO_BE_MAPPED = "TO_BE_MAPPED";
// const STATUS_DEMAPPED = "TO_BE_DEMAPPED";

export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        await validateResourceDetailsBeforeProcess("facilityValidation", resourceDetails, localizationMap);
        logger.info("Processing Facility file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);

        const campaign = await this.getCampaignDetails(resourceDetails);
        const userUuid = campaign?.auditDetails?.createdBy;

        const sheetData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_FACILITIES", localizationMap)];
        const facilityNameKey = "HCM_ADMIN_CONSOLE_FACILITY_NAME";
        const updatedSheetData = this.addUniqueFacilityKeyInSheetData(sheetData, facilityNameKey);

        const newFacilities = await this.extractNewFacilities(updatedSheetData, campaign.campaignNumber, resourceDetails);
        await this.persistInBatches(newFacilities, config?.kafka?.KAFKA_SAVE_SHEET_DATA_TOPIC);

        const waitTime = Math.max(5000, newFacilities.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));

        await this.createFacilityFromTableData(resourceDetails, userUuid);
        await this.syncFacilityBoundaryMapping(campaign.campaignNumber, sheetData, facilityNameKey, "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY");

        const allCurrentFacilties = await getRelatedDataWithCampaign(resourceDetails?.type, campaign.campaignNumber, dataRowStatuses.completed);
        const allData = allCurrentFacilties?.map((u: any) => {
            let data: any = u?.data;
            data["#status#"] = sheetDataRowStatuses.CREATED;
            return data;
        });
        const sheetMap: SheetMap = {};
        sheetMap["HCM_ADMIN_CONSOLE_FACILITIES"] = {
            data: allData,
            dynamicColumns: null
        };
        logger.info(`SheetMap generated for template of type ${resourceDetails.type}.`);
        return sheetMap;
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

    private static async extractNewFacilities(
        sheetData: any[],
        campaignNumber: string,
        resourceDetails: any
    ): Promise<any[]> {
        const facilityMap = Object.fromEntries(
            sheetData
                .filter((row: any) => {
                    const code = row?.["HCM_ADMIN_CONSOLE_FACILITY_CODE"];
                    return !code; // Only for new entries
                })
                .map((row: any) => [row?.uniqueFacilityKey, row])
                .filter(([key]) => !!key)
        );

        const alreadyCompletedFacilityMap = Object.fromEntries(
            sheetData
                .filter((row: any) => {
                    const code = row?.["HCM_ADMIN_CONSOLE_FACILITY_CODE"];
                    return !!code; // Only those with Facility Code
                })
                .map((row: any) => [row?.uniqueFacilityKey, row])
                .filter(([key]) => !!key)
        );

        const existing = await getRelatedDataWithCampaign("facility", campaignNumber);
        const existingMapEntries: [string, any][] = existing
            .map((f: any): [string, any] | null => {
                const name = String(f?.data?.["HCM_ADMIN_CONSOLE_FACILITY_NAME"]);
                if (!name) return null;
                return [name, f];
            })
            .filter((entry): entry is [string, any] => entry !== null);
        const existingMap = new Map<string, any>(existingMapEntries);

        const newEntries = [];
        // For already existing facility rows with Facility Code
        for (const [uniqueKey, row] of Object.entries(alreadyCompletedFacilityMap) as any) {
            if (existingMap.has(uniqueKey)) continue;
            delete row?.uniqueFacilityKey;
            newEntries.push({
                campaignNumber,
                data: row,
                type: resourceDetails?.type,
                uniqueIdentifier: uniqueKey,
                uniqueIdAfterProcess: row?.["HCM_ADMIN_CONSOLE_FACILITY_CODE"],
                status: dataRowStatuses.completed, // Mark as already completed
            });
        }

        for (const [uniqueKey, row] of Object.entries(facilityMap) as any) {
            if (existingMap.has(uniqueKey)) continue;
            delete row?.uniqueFacilityKey;
            newEntries.push({
                campaignNumber,
                data: row,
                type: resourceDetails?.type,
                uniqueIdentifier: uniqueKey,
                uniqueIdAfterProcess: null,
                status: dataRowStatuses.pending,
            });
        }

        return newEntries;
    }

    private static async persistInBatches(facilities: any[], topic: string): Promise<void> {
        const BATCH_SIZE = 100;
        for (let i = 0; i < facilities.length; i += BATCH_SIZE) {
            const batch = facilities.slice(i, i + BATCH_SIZE);
            await produceModifiedMessages({ datas: batch }, topic);
        }
    }

    static async createFacilityFromTableData(resourceDetails: any, userUuid: string): Promise<any> {
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) throw new Error("Campaign not found");

        const campaignNumber = campaign?.campaignNumber;
        // const userUuid = campaign?.auditDetails?.createdBy;

        // Get all existing facilities for this campaign
        const allCurrentFacilities = await getRelatedDataWithCampaign("facility", campaignNumber);

        // Map facility unique identifiers (Facility Name + campaign) to existing data
        const uniqueKeyAndFacilityMap: Record<string, any> = {};
        for (const facility of allCurrentFacilities) {
            const uniqueKey = facility?.uniqueIdentifier;
            if (uniqueKey) {
                uniqueKeyAndFacilityMap[uniqueKey] = facility;
            }
        }
        // Filter pending or failed facilities
        const facilitiesToCreate = allCurrentFacilities.filter(
            (f: any) => f?.status === dataRowStatuses.pending || f?.status === dataRowStatuses.failed
        );

        logger.info(`${facilitiesToCreate?.length} facilities to create`);

        const facilityRowDatas = facilitiesToCreate.map((facility: any) => facility?.data);

        // Dummy transformer config (replace later with actual one)
        const transformConfig = transformConfigs?.["Facility"];
        transformConfig.metadata.tenantId = resourceDetails.tenantId;
        transformConfig.metadata.hierarchy = resourceDetails.hierarchyType;

        const transformer = new DataTransformer(transformConfig);

        logger.info("Transforming facilities...");
        const transformedFacilities = await transformer.transform(facilityRowDatas);
        logger.info(`${transformedFacilities?.length} transformed facilities`);

        const BATCH_SIZE = 100;
        const successfullyCreatedFacilities: any[] = [];
        for (let i = 0; i < transformedFacilities.length; i += BATCH_SIZE) {
            const batch = transformedFacilities.slice(i, i + BATCH_SIZE);

            for (const facilityItem of batch) {

                const response: any = await this.createFacilitiesOneByOne(facilityItem?.Facility, userUuid);

                const createdFacility = response?.Facility;

                if (createdFacility) {
                    const uniqueKey = facilityItem?.Facility?.name;
                    if (uniqueKeyAndFacilityMap[uniqueKey]) {
                        const existingFacility = uniqueKeyAndFacilityMap[uniqueKey];
                        existingFacility.status = dataRowStatuses.completed;
                        existingFacility.data = {
                            ...existingFacility.data,
                            HCM_ADMIN_CONSOLE_FACILITY_CODE: createdFacility?.id
                        };
                        existingFacility.uniqueIdAfterProcess = createdFacility?.id;
                        successfullyCreatedFacilities.push(existingFacility);
                    }
                }
            }

            await this.persistInBatches(successfullyCreatedFacilities, config?.kafka?.KAFKA_UPDATE_SHEET_DATA_TOPIC);
            const waitTime = Math.max(5000, successfullyCreatedFacilities?.length * 8);
            logger.info(`Waiting for ${waitTime} ms for persistence...`);
            await new Promise((res) => setTimeout(res, waitTime));
        }
    }

    static addUniqueFacilityKeyInSheetData(
        sheetData: any[],
        facilityNameKey: string
    ) {
        for (const row of sheetData) {
            const facilityName = row?.[facilityNameKey];
            if (!facilityName) continue;

            row["uniqueFacilityKey"] = facilityName;
        }
        return sheetData
    }

    static async createFacilitiesOneByOne(facility: any, userUuid: string) {
        const url = config.host.facilityHost + config.paths.facilityCreate; // Update accordingly

        const requestBody = {
            RequestInfo: defaultRequestInfo?.RequestInfo,
            Facility: facility
        };
        requestBody.RequestInfo.userInfo.uuid = userUuid;

        let response: any;
        try {
            response = await httpRequest(url, requestBody);
            return response;
        } catch (error: any) {
            console.error("Facility creation failed:", error);
            throw new Error(error);
        }
    }


    static async syncFacilityBoundaryMapping(
        campaignNumber: string,
        sheetData: any[],
        facilityNameKey: string,
        boundaryCodeKey: string
    ) {
        const type = "facility";

        const sheetMap = this.buildSheetFacilityBoundaryMap(sheetData, facilityNameKey, boundaryCodeKey, "HCM_ADMIN_CONSOLE_FACILITY_USAGE");
        const existingDbMappings = await this.getMappingDataWithCampaign(campaignNumber, type);
        const dbMappingKeySet = this.buildDbMappingKeySet(existingDbMappings);

        const toBeMapped = this.getToBeMappedRows(sheetMap, dbMappingKeySet, campaignNumber, type);
        const toBeDemapped = this.getDemappedRows(sheetMap, existingDbMappings);

        if (toBeMapped.length > 0) await this.insertCampaignMappingData(toBeMapped);
        if (toBeDemapped.length > 0) await this.markMappingsAsDemapped(toBeDemapped);

        logger.info(`Inserted ${toBeMapped.length} TO_BE_MAPPED mappings.`);
        logger.info(`Updated ${toBeDemapped.length} to DEMAPPED.`);
        this.syncFacilitySheetData(sheetData, campaignNumber);
    }


    static async syncFacilitySheetData(sheetData: any[], campaignNumber: string) {
        const dbRows = await getRelatedDataWithCampaign("facility", campaignNumber); // status optional

        // Create map of uniqueIdentifier -> full DB row
        const dbMap = new Map<string, any>();
        for (const row of dbRows) {
            if (row?.uniqueIdentifier) {
                dbMap.set(row.uniqueIdentifier, row); // full row (with status, etc.)
            }
        }

        const modifiedRowsToUpdate = [];

        for (const row of sheetData) {
            const uniqueKey = row?.uniqueFacilityKey;
            if (!uniqueKey) continue;

            const sheetUsage = row?.HCM_ADMIN_CONSOLE_FACILITY_USAGE;
            const sheetBoundary = row?.HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY;

            const dbRow = dbMap.get(uniqueKey);
            const dbData = dbRow?.data;

            if (!dbData) continue;

            const dbUsage = dbData?.HCM_ADMIN_CONSOLE_FACILITY_USAGE;
            const dbBoundary = dbData?.HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY;

            if (sheetUsage !== dbUsage || sheetBoundary !== dbBoundary) {
                const newData = { ...dbData };

                if (sheetUsage !== dbUsage) {
                    newData["HCM_ADMIN_CONSOLE_FACILITY_USAGE"] = sheetUsage;
                }

                if (sheetBoundary !== dbBoundary) {
                    newData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] = sheetBoundary;
                }

                modifiedRowsToUpdate.push({
                    ...dbRow, // keep existing fields like status, campaignNumber, etc.
                    data: newData, // only data is updated
                });
            }
        }

        if (modifiedRowsToUpdate.length > 0) {
            const kafkaPayload = { datas: modifiedRowsToUpdate };
            await produceModifiedMessages(kafkaPayload, config?.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC);
        } else {
            logger.info("✅ No modified rows found. DB already in sync.");
        }
    }

    static buildSheetFacilityBoundaryMap(
        sheetData: any[],
        facilityNameKey: string,
        boundaryCodeKey: string,
        activeKey: string
    ): Record<string, string[]> {
        const map: Record<string, Set<string>> = {};
        for (const row of sheetData) {
            const name = row?.[facilityNameKey];
            const bcRaw = row?.[boundaryCodeKey]; // comma-separated boundary codes
            const active = row?.[activeKey];
            if (!name || !bcRaw || active !== "Active") continue;
            const codes = bcRaw.split(",").map((c: string) => c.trim());
            if (!map[name]) map[name] = new Set();
            codes.forEach((c: any) => map[name].add(c));
        }

        // Convert Set → Array for uniformity
        return Object.fromEntries(Object.entries(map).map(([k, v]) => [k, Array.from(v)]));
    }

    static buildDbMappingKeySet(existing: CampaignMappingRow[]): Set<string> {
        const set = new Set<string>();
        for (const row of existing) {
            const key = `${row.uniqueIdentifierForData}|${row.boundaryCode}`;
            set.add(key);
        }
        return set;
    }

    static getToBeMappedRows(
        sheetMap: Record<string, string[]>,
        dbMappingKeys: Set<string>,
        campaignNumber: string,
        type: string
    ): CampaignMappingRow[] {
        const rows: CampaignMappingRow[] = [];
        for (const [facility, bcList] of Object.entries(sheetMap)) {
            for (const bc of bcList) {
                const key = `${facility}|${bc}`;
                if (!dbMappingKeys.has(key)) {
                    rows.push({
                        campaignNumber,
                        type,
                        uniqueIdentifierForData: facility,
                        boundaryCode: bc,
                        status: mappingStatuses?.toBeMapped,
                    });
                }
            }
        }
        return rows;
    }

    static getDemappedRows(
        sheetMap: Record<string, string[]>,
        existing: CampaignMappingRow[]
    ): CampaignMappingRow[] {
        const rows: CampaignMappingRow[] = [];
        for (const row of existing) {
            const bcList = sheetMap[row.uniqueIdentifierForData] || [];
            if (!bcList.includes(row.boundaryCode)) {
                rows.push({ ...row, status: mappingStatuses?.toBeDeMapped });
            }
        }
        return rows;
    }

    static async markMappingsAsDemapped(rows: CampaignMappingRow[]): Promise<void> {
        if (!rows.length) return;
        const query = `UPDATE ${config?.DB_CONFIG.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME}
    SET status = $1 WHERE campaignNumber = $2 AND type = $3 AND uniqueIdentifierForData = $4 AND boundaryCode = $5`;

        for (const row of rows) {
            await executeQuery(query, [
                mappingStatuses?.toBeDeMapped,
                row.campaignNumber,
                row.type,
                row.uniqueIdentifierForData,
                row.boundaryCode,
            ]);
        }
    }


    static async insertCampaignMappingData(rows: CampaignMappingRow[]): Promise<void> {
        if (!rows.length) return;
        const query = `INSERT INTO ${config?.DB_CONFIG.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME}
    (campaignNumber, type, uniqueIdentifierForData, boundaryCode, mappingId, status)
    VALUES ($1, $2, $3, $4, $5, $6)`;

        for (const row of rows) {
            await executeQuery(query, [
                row.campaignNumber,
                row.type,
                row.uniqueIdentifierForData,
                row.boundaryCode,
                row.mappingId || null,
                row.status,
            ]);
        }
    }

    static async getMappingDataWithCampaign(
        campaignNumber: string,
        type: string,
        status?: string
    ): Promise<CampaignMappingRow[]> {
        let queryString = `SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME} WHERE type = $1 AND campaignNumber = $2`;
        const arrayStatements: any[] = [type, campaignNumber];
        if (status) {
            queryString += ` AND status = $3`;
            arrayStatements.push(status);
        }
        const result = await executeQuery(queryString, arrayStatements);
        if (!result?.rows) return [];
        return result.rows.map((row: any) => ({
            campaignNumber: row?.campaignnumber,
            type: row?.type,
            uniqueIdentifierForData: row?.uniqueidentifierfordata,
            boundaryCode: row?.boundarycode,
            mappingId: row?.mappingid,
            status: row?.status,
        }));
    }


}
