import { throwError,getLocalizedHeaders } from "../utils/genericUtils";
import { processRequestSchema } from "../config/models/processRequestSchema";
import { validateBodyViaSchema,validateHierarchyType} from "./genericValidator";
import config from "../config";
import { httpRequest } from "../utils/request";
import { getLocaleFromRequest } from "../utils/localisationUtils";
import { validateFileMetaDataViaFileUrl } from "../utils/excelUtils";
import {getLocalizedName,getBoundaryTabName,getHeadersOfBoundarySheet,getHierarchy,validateHeaders} from "../utils/boundaryUtils";
import {getSheetData} from "../api/genericApis";



/**
 * Validate the create request body
 * @param {object} request - Request object
 * @param {object} localizationMap - Localization map
 * @returns {Promise<void>} - Promise object
 * @throws {Error} - Throws an error if the request is invalid
 */
async function validateProcessRequest(request: any, localizationMap?: any) {
    if (!request?.body?.ResourceDetails || Object.keys(request.body.ResourceDetails).length === 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "ResourceDetails is missing or empty or null");
    }
    else {
        // validate process request body 
        validateBodyViaSchema(processRequestSchema, request.body.ResourceDetails);
        // validate
        await validateHierarchyType(request, request?.body?.ResourceDetails?.hierarchyType, request?.body?.ResourceDetails?.tenantId);
        if (request?.body?.ResourceDetails?.tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
            throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId is not matching with userInfo");
        }
        const fileUrl = await validateFile(request);
        await validateFileMetaDataViaFileUrl(fileUrl, getLocaleFromRequest(request), request?.body?.ResourceDetails?.campaignId, request?.body?.ResourceDetails?.action);

        await validateBoundarySheetData(request, fileUrl, localizationMap);

    }
}


async function validateFile(request: any) {
    const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: request?.body?.ResourceDetails?.tenantId, fileStoreIds: request?.body?.ResourceDetails?.fileStoreId }, "get");
    if (!fileResponse || !fileResponse.fileStoreIds || !fileResponse.fileStoreIds[0] || !fileResponse.fileStoreIds[0].url) {
        throwError("FILE", 400, "INVALID_FILE");
    }
    else {
        return (fileResponse?.fileStoreIds?.[0]?.url);
    }
}

/**
 * Validates the boundary sheet data.
 * @param {object} request - Request object
 * @param {string} fileUrl - File URL
 * @param {object} localizationMap - Localization map
 * @returns {Promise<void>} - Promise object
 * @throws {Error} - Throws an error if the request is invalid
 */
async function validateBoundarySheetData(request: any, fileUrl: any, localizationMap?: any) {
    const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
    const headersOfBoundarySheet = await getHeadersOfBoundarySheet(fileUrl, localizedBoundaryTab, false, localizationMap);
    const hierarchy = await getHierarchy(request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    const modifiedHierarchy = hierarchy.map(ele => `${request?.body?.ResourceDetails?.hierarchyType}_${ele}`.toUpperCase())
    const localizedHierarchy = getLocalizedHeaders(modifiedHierarchy, localizationMap);
    await validateHeaders(localizedHierarchy, headersOfBoundarySheet, request, localizationMap)
    const boundaryData = await getSheetData(fileUrl, localizedBoundaryTab, true, undefined, localizationMap);
    //validate for whether root boundary level column should not be empty
    validateForRootElementExists(boundaryData, localizedHierarchy, localizedBoundaryTab);
    // validate for duplicate rows(array of objects)
    validateForDuplicateRows(boundaryData);
}

function validateForRootElementExists(boundaryData: any[], hierachy: any[], sheetName: string) {
    const root = hierachy[0];
    if (!(boundaryData.filter(e => e[root]).length == boundaryData.length)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid Boundary Sheet. Root level Boundary not present in every row  of Sheet ${sheetName}`)
    }
}

function validateForDuplicateRows(boundaryData: any[]) {
    // Step 1: Trim strings in all rows
    boundaryData = boundaryData.map(row =>
        Object.fromEntries(
            Object.entries(row).map(([key, value]) =>
                [key, typeof value === "string" ? value.trim() : value]
            )
        )
    );
    const seen = new Set<string>();
    const duplicateRowNumbers: string[] = [];
    for (const row of boundaryData) {
        const rowNumber = row["!row#number!"];
        const rowCopy = { ...row };
        delete rowCopy["!row#number!"];
        // Serialize row as a string (key), which is much faster than deep object comparison
        const rowKey = JSON.stringify(rowCopy);
        if (seen.has(rowKey)) {
            duplicateRowNumbers.push(rowNumber);
        } else {
            seen.add(rowKey);
        }
    }
    if (duplicateRowNumbers.length > 0) {
        const rowNumbersSeparatedWithCommas = duplicateRowNumbers.join(', ');
        throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary Sheet has duplicate rows at rowNumber ${rowNumbersSeparatedWithCommas}`);
    }
}

export { validateProcessRequest };