import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { SheetMap } from "../models/SheetMap";
import { getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { searchBoundaryRelationshipData, searchBoundaryRelationshipDefinition } from "../api/coreApis";
import { logger } from "../utils/logger";
import { dataRowStatuses, sheetDataRowStatuses } from "../config/constants";
import { bulkDecrypt } from "../utils/cryptUtils";
import { WorkerRegistryRecord, searchWorkersByIds } from "../utils/workerRegistryUtils";

// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function 
    static async generate(templateConfig: any, responseToSend: any, localizationMap: any): Promise<SheetMap> {
        logger.info("Generating template...");
        logger.info(`Input payload: ${JSON.stringify(responseToSend)}`);

        const { tenantId, type, campaignId } = responseToSend;

        // Fetch campaign details
        const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        const campaignDetails = campaignResp?.CampaignDetails?.[0];
        if (!campaignDetails) throw new Error("Campaign not found");


        // Prepare User List sheet
        const users = await getRelatedDataWithCampaign("user", campaignDetails?.campaignNumber, tenantId, dataRowStatuses.completed);

        // Fetch decrypted payee details from worker registry for all users that have a stored workerId.
        // Worker registry always decrypts fields server-side on search, so this guarantees plain-text values
        // regardless of whether the DIGIT PII encryption service was available during batch processing.
        const workerIdToWorkerMap = new Map<string, WorkerRegistryRecord>();
        const requestInfo = responseToSend?.requestInfo;
        if (requestInfo) {
            const workerIds: string[] = users
                .map((u: any) => u?.data?.["HCM_ADMIN_CONSOLE_USER_WORKER_ID"])
                .filter((id: any): id is string => !!id);

            if (workerIds.length > 0) {
                try {
                    const workers = await searchWorkersByIds(workerIds, tenantId, requestInfo);
                    for (const worker of workers) {
                        if (worker?.id) {
                            workerIdToWorkerMap.set(worker.id, worker);
                        }
                    }
                    logger.info(`Fetched ${workers.length} workers from worker registry for credential sheet`);
                } catch (workerSearchError) {
                    logger.error("Failed to fetch worker details for credential sheet — payee fields will fall back to stored values", workerSearchError);
                }
            }
        } else {
            logger.warn("requestInfo not available in responseToSend — skipping worker registry search for credential sheet");
        }

        logger.info(`Decrypting ${users.length} users`);

        const encryptedUserNames: string[] = [];
        const encryptedPasswords: string[] = [];
        for (const u of users) {
            const rawData = u?.data || {};
            encryptedUserNames.push(rawData["UserName"]);
            encryptedPasswords.push(rawData["Password"]);
        }

        // bulkDecrypt is capped at 500 entries per call, so chunk both arrays in lockstep.
        const BULK_DECRYPT_CHUNK = 500;
        const decryptedUserNames: string[] = [];
        const decryptedPasswords: string[] = [];
        for (let i = 0; i < encryptedUserNames.length; i += BULK_DECRYPT_CHUNK) {
            decryptedUserNames.push(...bulkDecrypt(encryptedUserNames.slice(i, i + BULK_DECRYPT_CHUNK)));
            decryptedPasswords.push(...bulkDecrypt(encryptedPasswords.slice(i, i + BULK_DECRYPT_CHUNK)));
        }

        const userData = users.map((u: any, idx: number) => {
            const rawData = u?.data || {};
            const localizedData: Record<string, any> = {};

            for (const key in rawData) {
                localizedData[key] = rawData[key];
            }

            localizedData["#status#"] = sheetDataRowStatuses.CREATED;
            localizedData["UserName"] = decryptedUserNames[idx];
            localizedData["Password"] = decryptedPasswords[idx];

            // Overlay decrypted payee details from worker registry search result.
            // This replaces any potentially encrypted values that may have been stored
            // in campaign_data during batch processing.
            const workerId = rawData["HCM_ADMIN_CONSOLE_USER_WORKER_ID"];
            if (workerId) {
                const worker = workerIdToWorkerMap.get(workerId);
                if (worker) {
                    if (worker.payeeName) localizedData["HCM_ADMIN_CONSOLE_USER_PAYEE_NAME"] = worker.payeeName;
                    if (worker.bankAccount) localizedData["HCM_ADMIN_CONSOLE_USER_BANK_ACCOUNT"] = worker.bankAccount;
                    if (worker.bankCode) localizedData["HCM_ADMIN_CONSOLE_USER_BANK_CODE"] = worker.bankCode;
                    if (worker.beneficiaryCode) localizedData["HCM_ADMIN_CONSOLE_USER_BENEFICIARY_CODE"] = worker.beneficiaryCode;
                    if (worker.paymentProvider) localizedData["HCM_ADMIN_CONSOLE_USER_PAYMENT_PROVIDER"] = worker.paymentProvider;
                    if (worker.payeePhoneNumber) localizedData["HCM_ADMIN_CONSOLE_USER_PAYEE_PHONE_NUMBER"] = worker.payeePhoneNumber;
                }
            }

            const boundaryCode = localizedData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] ;
            // Add localized boundary code
            if (boundaryCode && !localizedData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"]){
                localizedData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] = localizedData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            }
            const boundaryMandatoryCode = localizedData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] ;
            // Add boundary Name
            if (!localizedData["HCM_ADMIN_CONSOLE_BOUNDARY_NAME"]) {
                localizedData["HCM_ADMIN_CONSOLE_BOUNDARY_NAME"] = getLocalizedName(boundaryMandatoryCode, localizationMap);
            }
            return localizedData;
        });

        // Construct the final SheetMap
        const sheetMap: SheetMap = {
            ["HCM_ADMIN_CONSOLE_USER_LIST"]: {
                data: userData,
                dynamicColumns: {
                    ["Password"]: { hideColumn: false },
                    ["HCM_ADMIN_CONSOLE_BOUNDARY_NAME"]: { hideColumn: false }
                }
            }
        };

        logger.info(`SheetMap generated for template type: ${type}`);
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
        logger.info(`Structured boundaries prepared.`);
        return structuredBoundaries;
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
                result[key] = { orderNumber: -1 * (total - index), adjustHeight: true, color: '#93c47d', freezeColumn: true };
            });
            result["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] = { adjustHeight: true, width: 80, freezeColumn: true };
            logger.info(`Dynamic columns prepared for boundary data.`);
            return result;
        } else {
            throw new Error("Boundary Hierarchy not found");
        }
    }

}
