import { getLocalizedName, populateBoundariesRecursively } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import config from "../config";
import { getCampaignDataRowsWithUniqueIdentifiers, throwError } from "../utils/genericUtils";
import { dataRowStatuses, sheetDataRowStatuses } from "../config/constants";
import { defaultRequestInfo, searchBoundaryRelationshipData } from "../api/coreApis";
import { httpRequest } from "../utils/request";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { validateActiveFieldMinima, validateDatasWithSchema } from "../validators/campaignValidators";
import { ResourceDetails } from "../config/models/resourceDetailsSchema";


// This will be a dynamic template class for different types
export class TemplateClass {
    // Static generate function
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        logger.info("Processing file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);
        const userSheetData = wholeSheetData[getLocalizedName("HCM_ADMIN_CONSOLE_USER_LIST", localizationMap)];
        if (!userSheetData || !userSheetData?.length) {
            throwError("COMMON", 400, "VALIDATION_ERROR", `Sheet: '${getLocalizedName("HCM_ADMIN_CONSOLE_USER_LIST", localizationMap)}' is empty or not present`);
        }
        const errors: any[] = [];
        const userSchema = templateConfig?.sheets?.filter((s: any) => s?.sheetName === "HCM_ADMIN_CONSOLE_USER_LIST")[0]?.schema;
        validateDatasWithSchema(userSheetData, userSchema, errors, localizationMap);
        validateActiveFieldMinima(userSheetData,"HCM_ADMIN_CONSOLE_USER_USAGE", errors);
        await this.validatePhoneNumber(userSheetData, resourceDetails.tenantId, errors);
        await this.validateUserNames(userSheetData, resourceDetails.tenantId, errors);
        await this.validateBoundaries(userSheetData, resourceDetails, errors);

        this.processErrors(userSheetData, errors, resourceDetails);       
        const sheetMap: SheetMap = {
            ["HCM_ADMIN_CONSOLE_USER_LIST"]: {
                data: userSheetData,
                dynamicColumns: {
                    ["Password"]: {
                        hideColumn: true,
                        showInProcessed: false
                    }
                }
            }
        }
        logger.info(`SheetMap generated for template of type ${resourceDetails.type}.`);
        return sheetMap;
    }

    private static processErrors(sheetData : any, errors : any[], resourceDetails : ResourceDetails) {
            for (const error of errors) {
                const row = error.row - 3;
                const existingError = sheetData[row]["#errorDetails#"];
    
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


    private static async validatePhoneNumber(userSheetData: any, tenantId: string, errors: any[]) {
        logger.info("Validating phone numbers...");
        const phoneNumbersToRowMap: any = {};
        for (let i = 0; i < userSheetData.length; i++) {
            if (userSheetData[i]["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"]) {
                const phoneNumber = userSheetData[i]["HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"];
                phoneNumbersToRowMap[phoneNumber] = i + 3;
            }
        }
        const allPhoneNumbersToSearch = Object.keys(phoneNumbersToRowMap);
        const allCurrentUsersInCampaignDataWithPhoneNumbersRows = await getCampaignDataRowsWithUniqueIdentifiers("user", allPhoneNumbersToSearch, dataRowStatuses.completed);
        const setOfAllCurrentUsersInCampaignDataWithPhoneNumbers = new Set(allCurrentUsersInCampaignDataWithPhoneNumbersRows.map((user: any) => user?.uniqueIdentifier)); // uniqueIdentifiers of AllCurrentUsersInCampaignDataWithPhoneNumbers
        const allPhoneNumbersNotInCampaignData = allPhoneNumbersToSearch.filter((phoneNumber: any) => !setOfAllCurrentUsersInCampaignDataWithPhoneNumbers.has(phoneNumber));
        const searchBody: any = {
            RequestInfo: defaultRequestInfo.RequestInfo,
            Individual: {
            },
        };
        const params = {
            limit: 55,
            offset: 0,
            tenantId,
            includeDeleted: true,
        };
        const searchBatchlimit = 50;
        for (let i = 0; i < allPhoneNumbersNotInCampaignData.length; i += searchBatchlimit) {
            const batch = allPhoneNumbersNotInCampaignData.slice(i, i + searchBatchlimit);
            searchBody.Individual.mobileNumber = batch;
            logger.info("Individual search to validate the mobile no initiated");
            const response = await httpRequest(
                config.host.healthIndividualHost + config.paths.healthIndividualSearch,
                searchBody,
                params,
                undefined,
                undefined,
                undefined,
                undefined,
                true
            );
            if (!response) {
                throwError(
                    "COMMON",
                    400,
                    "INTERNAL_SERVER_ERROR",
                    "Error occurred during user search while validating mobile number."
                );
            }
            if (response?.Individual?.length === 0) {
                continue;
            }
            for (const user of response?.Individual) {
                logger.warn(
                    `User with mobileNumber ${user?.mobileNumber} already exists in campaign data`
                );
                errors.push({
                    row: phoneNumbersToRowMap[user?.mobileNumber],
                    message: `User with mobileNumber ${user?.mobileNumber} already exists and is not suitable for this campaign.`
                });
            }
        }
        logger.info("Phone number validation completed.");
    }

    private static async validateUserNames(userSheetData: any, tenantId: string, errors: any[]) {
        logger.info("Validating user names...");
        const userNamesToRowMap: any = {};
        for (let i = 0; i < userSheetData.length; i++) {
            if (userSheetData[i]["UserName"]) {
                const userName = userSheetData[i]["UserName"];
                if (!userSheetData[i]["UserService Uuids"] && userName) {
                    userNamesToRowMap[userName] = i + 3;
                }
            }
        }
        const allUserNamesToCheck = Object.keys(userNamesToRowMap);
        const searchBody: any = {
            RequestInfo: defaultRequestInfo?.RequestInfo,
            Individual: {
                username: []
            }
        };

        // const tenantId = request?.body?.ResourceDetails?.tenantId;
        const searchUrl = config.host.healthIndividualHost + config.paths.healthIndividualSearch;
        const chunkSize = 50;

        for (let i = 0; i < allUserNamesToCheck.length; i += chunkSize) {
            const chunk = allUserNamesToCheck.slice(i, i + chunkSize);
            searchBody.Individual.username = chunk;
            const params = {
                tenantId,
                limit: 51,
                offset: 0
            };

            try {
                const response = await httpRequest(
                    searchUrl,
                    searchBody,
                    params,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    true
                );
                if (response?.Individual?.length > 0) {
                    response.Individual.forEach((emp: any) => {
                        errors.push({
                            row: userNamesToRowMap[emp?.userDetails?.username],
                            message: `User with userName ${emp?.userDetails?.username} already exists.`
                        })
                    });
                }
            } catch (error: any) {
                console.log(error);
                throwError(
                    "COMMON",
                    500,
                    "INTERNAL_SERVER_ERROR",
                    error.message ||
                    "Some internal error occurred while searching employees"
                );
            }
        }
        logger.info("User name validation completed.");
    }

    private static async validateBoundaries(userSheetData: any, resourceDetails: any, errors: any[]) {
        logger.info("Validating boundaries...");
        const campaignDetails = await this.getCampaignDetails(resourceDetails);
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
        const setOfCampaignBoundaries = new Set();
        for (let i = 0; i < boundaries.length; i++) {
            setOfCampaignBoundaries.add(boundaries[i]?.code);
        }
        for (let i = 0; i < userSheetData.length; i++) {
            if (userSheetData[i]?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"]) {
                const boundariesAfterSplitAndTrim = userSheetData[i]?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"].split(",").map((boundary: any) => boundary.trim());
                boundariesAfterSplitAndTrim.forEach((boundary: any) => {
                    if (!setOfCampaignBoundaries.has(boundary)) {
                        errors.push({
                            row: i + 3,
                            message: `Boundary code ${boundary} is not part of this campaign.`
                        });
                    }
                });
            }
        }
        logger.info("Boundary validation completed.");
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
}
