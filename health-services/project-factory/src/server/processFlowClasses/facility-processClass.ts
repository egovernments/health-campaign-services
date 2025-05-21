import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { dataRowStatuses } from "../config/constants";
import { DataTransformer, transformConfigs } from "../config/transFormConfig";
import { produceModifiedMessages } from "../kafka/Producer";
import config from "../config";
import { httpRequest } from "../utils/request";
import { defaultRequestInfo } from "../api/coreApis";

export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>
    ): Promise<SheetMap> {
        logger.info("Processing Facility file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);

        const reverseMap = this.getReverseLocalizationMap(localizationMap);
        const campaign = await this.getCampaignDetails(resourceDetails);

        const sheetData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_FACILITIES", localizationMap)];
        const facilityName = getLocalizedName("HCM_ADMIN_CONSOLE_FACILITY_NAME", localizationMap);
        const updatedSheetData = this.addUniqueFacilityKeyInSheetData(sheetData, campaign, facilityName);

        const newFacilities = await this.extractNewFacilities(updatedSheetData, facilityName, campaign.campaignNumber, resourceDetails, localizationMap, reverseMap);
        await this.persistInBatches(newFacilities, config?.kafka?.KAFKA_SAVE_SHEET_DATA_TOPIC);

        const waitTime = Math.max(5000, newFacilities.length * 8);
        logger.info(`Waiting for ${waitTime} ms for persistence...`);
        await new Promise((res) => setTimeout(res, waitTime));

        await this.createFacilityFromTableData(resourceDetails, localizationMap);

        const allCurrentFacilties = await getRelatedDataWithCampaign(resourceDetails?.type, campaign.campaignNumber, dataRowStatuses.completed);
        const allData = allCurrentFacilties?.map((u: any) => {
            const data: any = {};
            for (const key of Object.keys(u?.data)) {
                data[getLocalizedName(key, localizationMap)] = u?.data[key];
            }
            data["#status#"] = "CREATED";
            return data;
        });
        const sheetMap: SheetMap = {};
        sheetMap[getLocalizedName("HCM_ADMIN_CONSOLE_FACILITIES", localizationMap)] = {
            data: allData,
            dynamicColumns: null
        };
        logger.info(`SheetMap generated for template of type ${resourceDetails.type}.`);
        return sheetMap;
    }

    private static getReverseLocalizationMap(localizationMap: Record<string, string>): Map<string, string> {
        const reverse = new Map<string, string>();
        Object.entries(localizationMap).forEach(([key, val]) => reverse.set(val, key));
        return reverse;
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
        facilityNameKey: string,
        campaignNumber: string,
        resourceDetails: any,
        localizationMap: any,
        reverseMap: Map<string, string>
    ): Promise<any[]> {
        const facilityMap = Object.fromEntries(
            sheetData
                .filter((row: any) => {
                    const code = row?.[getLocalizedName("HCM_ADMIN_CONSOLE_FACILITY_CODE", localizationMap)];
                    return !code; // Only for new entries
                })
                .map((row: any) => [row?.uniqueFacilityKey, row])
                .filter(([key]) => !!key)
        );

        const alreadyCompletedFacilityMap = Object.fromEntries(
            sheetData
                .filter((row: any) => {
                    const code = row?.[getLocalizedName("HCM_ADMIN_CONSOLE_FACILITY_CODE", localizationMap)];
                    return !!code; // Only those with Facility Code
                })
                .map((row: any) => [row?.uniqueFacilityKey, row])
                .filter(([key]) => !!key)
        );

        const existing = await getRelatedDataWithCampaign("facility", campaignNumber);
        const existingMapEntries: [string, any][] = existing
            .map((f: any): [string, any] | null => {
                const name = String(f?.data?.[reverseMap.get(facilityNameKey) || facilityNameKey]);
                if (!name) return null;
                return [name + "_" + campaignNumber, f];
            })
            .filter((entry): entry is [string, any] => entry !== null);
        const existingMap = new Map<string, any>(existingMapEntries);

        const newEntries = [];
        // For already existing facility rows with Facility Code
        for (const [uniqueKey, row] of Object.entries(alreadyCompletedFacilityMap)) {
            if (existingMap.has(uniqueKey)) continue;

            const data = Object.fromEntries(
                Object.entries(row as any).map(([k, v]) => [reverseMap.get(k) || k, v])
            );

            newEntries.push({
                campaignNumber,
                data,
                type: "facility",
                uniqueIdentifier: uniqueKey,
                uniqueIdAfterProcess: data?.["HCM_ADMIN_CONSOLE_FACILITY_CODE"],
                status: dataRowStatuses.completed, // Mark as already completed
            });
        }

        for (const [uniqueKey, row] of Object.entries(facilityMap)) {
            if (existingMap.has(uniqueKey)) continue;

            const data = Object.fromEntries(
                Object.entries(row as any).map(([k, v]) => [reverseMap.get(k) || k, v])
            );

            newEntries.push({
                campaignNumber,
                data,
                type: "facility",
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

    static async createFacilityFromTableData(resourceDetails: any, localizationMap: Record<string, string>): Promise<any> {
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
            const uniqueKey = facility?.data?.uniqueFacilityKey;
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

                const response: any = await this.createFacilitiesOneByOne(facilityItem?.Facility);

                const createdFacility = response?.Facility;

                if (createdFacility) {
                    const uniqueKey = createdFacility?.name + "_" + campaignNumber;
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
        }
    }

    static addUniqueFacilityKeyInSheetData(
        sheetData: any[],
        campaignDetails: any,
        facilityNameKey: string
    ) {
        for (const row of sheetData) {
            const facilityName = row?.[facilityNameKey];
            if (!facilityName) continue;

            row["uniqueFacilityKey"] = `${facilityName}_${campaignDetails?.campaignNumber}`;
        }
        return sheetData
    }

    static async createFacilitiesOneByOne(facility: any) {
        const url = config.host.facilityHost + config.paths.facilityCreate; // Update accordingly

        const requestBody = {
            RequestInfo: defaultRequestInfo?.RequestInfo,
            Facility: facility
        };

        let response: any;
        try {
            response = await httpRequest(url, requestBody);
            return response;
        } catch (error: any) {
            console.error("Facility creation failed:", error);
            throw new Error(error);
        }
    }

}
