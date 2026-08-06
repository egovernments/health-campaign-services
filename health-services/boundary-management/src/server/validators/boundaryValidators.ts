import { throwError,getLocalizedHeaders } from "../utils/genericUtils";
import { processRequestSchema } from "../config/models/processRequestSchema";
import { validateBodyViaSchema,validateHierarchyType} from "./genericValidator";
import config from "../config";
import { httpRequest } from "../utils/request";
// import { validateFileMetaDataViaFileUrl } from "../utils/excelUtils";
import {getLocalizedName,getBoundaryTabName,getHeadersOfBoundarySheet,getHierarchy,validateHeaders,dropExactDuplicateRows} from "../utils/boundaryUtils";
import { logger } from "../utils/logger";
import {getSheetData} from "../api/genericApis";
import { downloadRequestSchema } from "../config/models/downloadRequestSchema";
import {searchCriteriaSchema} from "../config/models/SearchCriteria";



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
        // await validateFileMetaDataViaFileUrl(fileUrl, getLocaleFromRequest(request), request?.body?.ResourceDetails?.campaignId, request?.body?.ResourceDetails?.action);

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
    const headersOfBoundarySheet = await getHeadersOfBoundarySheet(fileUrl, localizedBoundaryTab, false, localizationMap, request);
    const hierarchy = await getHierarchy(request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
    const modifiedHierarchy = hierarchy.map(ele => `${request?.body?.ResourceDetails?.hierarchyType}_${ele}`.toUpperCase())
    const localizedHierarchy = getLocalizedHeaders(modifiedHierarchy, localizationMap);
    await validateHeaders(localizedHierarchy, headersOfBoundarySheet, request, localizationMap)
    const boundaryData = await getSheetData(fileUrl, localizedBoundaryTab, true, undefined, localizationMap, request);
    //validate for whether root boundary level column should not be empty
    validateForRootElementExists(boundaryData, localizedHierarchy, localizedBoundaryTab);
    // Exact duplicate rows no longer fail the upload: the first occurrence of each is kept and the later
    // identical ones are dropped. This is the only parse that carries Excel row numbers, so it is where
    // the removed rows are named in the log and recorded for the resource-details record; the ingestion
    // read in autoGenerateBoundaryCodes drops the same rows from the data that actually gets persisted.
    const deduplicatedBoundaryData = dropDuplicateRowsFromBoundarySheet(request, boundaryData);
    // validate boundary names contain only allowed characters (reject [] {} <> etc.; allow letters of any
    // language plus the punctuation that occurs in real place names)
    validateBoundaryNameCharacters(deduplicatedBoundaryData, localizedHierarchy);
}

function validateForRootElementExists(boundaryData: any[], hierachy: any[], sheetName: string) {
    const root = hierachy[0];
    if (!(boundaryData.filter(e => e[root]).length == boundaryData.length)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid Boundary Sheet. Root level Boundary not present in every row  of Sheet ${sheetName}`)
    }
}

// How many removed row numbers to name in the log line, and how many to keep on the resource-details
// record. A sheet can be duplicated wholesale, so neither the log nor the persisted jsonb may grow with
// the sheet; the total count is always exact and reported either way.
const MAX_REPORTED_DUPLICATE_ROWS = 20;
const MAX_RECORDED_DUPLICATE_ROWS = 100;

/**
 * Removes exact duplicate rows (first occurrence kept) from the rows this validation pass works on, and
 * records what was removed. Previously this condition failed the whole upload with
 * "Boundary Sheet has duplicate rows at rowNumber ..."; the rows named by that error are exactly the
 * rows removed here.
 *
 * The array returned by this validation parse is not what gets ingested - autoGenerateBoundaryCodes
 * re-parses the same (cached) workbook and applies the same removal to the data that is persisted. What
 * this pass adds is the row numbers, which only exist on a getRow = true parse.
 */
function dropDuplicateRowsFromBoundarySheet(request: any, boundaryData: any[]) {
    const { rows, removedRowNumbers, removedCount } = dropExactDuplicateRows(boundaryData);
    if (removedCount > 0) {
        const reportedRowNumbers = removedRowNumbers.slice(0, MAX_REPORTED_DUPLICATE_ROWS);
        const notReported = removedRowNumbers.length - reportedRowNumbers.length;
        logger.warn(
            `Boundary Sheet has ${removedCount} exact duplicate row(s): keeping the first occurrence of each and ` +
            `removing the rest. Removed rowNumber ${reportedRowNumbers.join(', ')}` +
            `${notReported > 0 ? ` ... and ${notReported} more` : ''}`
        );
        if (request?.body) {
            // Additive, informational record of an upload that silently shrank. Kept in two places on
            // purpose: on request.body it is the internal stash the ingestion pass cross-checks its own
            // removal against, and on ResourceDetails.additionalDetails (jsonb, returned as-is by
            // _process-search and by the synchronous _process response) it is what a caller can actually
            // see - including for action = "validate", which never reaches the ingestion pass at all.
            const duplicateRowsRemoved = {
                count: removedCount,
                rowNumbers: removedRowNumbers.slice(0, MAX_RECORDED_DUPLICATE_ROWS),
                // A wholly duplicated sheet would otherwise persist a 100-entry list that reads as complete;
                // count is always exact, so mark when the list itself is only a sample.
                ...(removedRowNumbers.length > MAX_RECORDED_DUPLICATE_ROWS ? { rowNumbersTruncated: true } : {}),
            };
            request.body.duplicateBoundaryRowsRemoved = duplicateRowsRemoved;
            if (request.body.ResourceDetails) {
                request.body.ResourceDetails.additionalDetails = {
                    ...(request.body.ResourceDetails.additionalDetails || {}),
                    duplicateRowsRemoved,
                };
            }
        }
    }
    return rows;
}

// Boundary names may contain letters of ANY script, combining marks, digits, spaces and a small set of
// punctuation that legitimately occurs in official African place / admin-division names. Everything else
// (structural / impossible / ingestion-hazardous characters) is rejected. Policy derived from a continent-
// wide study of African administrative-division names.
//
// ALLOWED:
//   \p{L}  letters of every script  - Latin + diacritics, Arabic, Ge'ez/Amharic, Tifinagh, N'Ko, Vai,
//          Adlam/Osmanya, AND the Khoekhoe click letters ǀ ǁ ǂ ǃ (Unicode classifies these as letters)
//   \p{M}  combining marks          - Yoruba tone marks, romanized Arabic z̧/e̱, Malagasy n̈ (no precomposed form)
//   \p{N}  digits                   - "6th of October", "Region 1"
//   space, apostrophe family ' ’ ‘ ʼ ʻ ʿ ʾ `  (N'Djamena, Murang'a, King's Town),
//   ampersand &, hyphen/dash family - ‐ – —  (Tharaka-Nithi, Haut-Ogooué), slash /  (Chuka/Igambang'ombe),
//   period . parentheses ( ) underscore _  (present in this deployment's real boundary names)
//   comma , colon : semicolon ; at @ plus + exclamation ! question ?  (allowed 2026-07-14: real
//          admin names / free-text descriptors use these, e.g. "X, Y", "Oseni: Ibrahim"; harmless to
//          codes since code-gen already sanitizes /[^\w]/g -> _)
//
// REJECTED: { } [ ] < > | \ # $ % * = ~ ^ "  and all control / zero-width / bidi characters.
//   '#' is rejected deliberately: it corrupts excel-ingestion's parent#child cascading-dropdown lookup keys.
//   '< >' stay rejected (XSS-risk in the console UI + almost always data-entry errors); '"' \ | { } [ ]
//   stay rejected as quote/escape/delimiter hazards for CSV/JSON/lookup-key round-trips.
const ALLOWED_BOUNDARY_NAME_CHARS = new RegExp(
    "^[\\p{L}\\p{M}\\p{N} '’‘ʼʻʿʾ`&,:;@+!?/._()‐–—\\-]+$",
    "u"
);

// Cap how many offending cells we list in one error so a badly-formed 50k-row sheet cannot produce a
// multi-megabyte message; the count of the remainder is still reported.
const MAX_REPORTED_NAME_PROBLEMS = 20;

function validateBoundaryNameCharacters(boundaryData: any[], localizedHierarchy: any[]) {
    const problems: string[] = [];
    for (const row of boundaryData) {
        const rowNumber = row?.["!row#number!"];
        for (const header of localizedHierarchy) {
            const raw = row?.[header];
            if (raw === undefined || raw === null) continue;
            const value = String(raw).trim();
            if (value === "") continue; // empty hierarchy levels are normal (ragged hierarchy) - skip
            if (!ALLOWED_BOUNDARY_NAME_CHARS.test(value)) {
                const badChars = Array.from(
                    new Set(Array.from(value).filter(ch => !ALLOWED_BOUNDARY_NAME_CHARS.test(ch)))
                ).join(" ");
                problems.push(`row ${rowNumber}, column "${header}", value "${value}" (not allowed: ${badChars})`);
            }
        }
    }
    if (problems.length > 0) {
        const shown = problems.slice(0, MAX_REPORTED_NAME_PROBLEMS);
        const extra = problems.length > MAX_REPORTED_NAME_PROBLEMS
            ? ` ... and ${problems.length - MAX_REPORTED_NAME_PROBLEMS} more` : "";
        // Use the registered VALIDATION_ERROR code (like the duplicate-row / root checks) so this surfaces
        // as a clean HTTP 400; a new unregistered code resolves to UNKNOWN_ERROR -> 500 in throwError.
        throwError(
            "COMMON", 400, "VALIDATION_ERROR",
            `Boundary names contain characters that are not allowed. ` +
            `Allowed: letters (any language), numbers, spaces and ' & - / . ( ) _ , : ; @ + ! ? . ` +
            `Problems: ${shown.join("; ")}${extra}`
        );
    }
}

// `rowNumbers` is index-aligned with boundarySheetData and carries the sheet row each entry came from.
// It must be supplied whenever the caller has removed rows (exact duplicates), because the array index
// then no longer maps to the sheet row and the reported position would point at an unrelated - possibly
// deleted - row. Falls back to the old index arithmetic when absent.
function validateBoundarySheetDataInCreateFlow(boundarySheetData: any, localizedHeadersOfBoundarySheet: any, rowNumbers?: any[]) {
    const firstColumnValues = new Set();
    const firstColumn = localizedHeadersOfBoundarySheet[0];

    boundarySheetData.forEach((obj: any, index: number) => {
        let firstEmptyFound = false;
        // Collect value from the first column
        if (obj[firstColumn]) {
            firstColumnValues.add(obj[firstColumn]);
        }
        if (firstColumnValues.size > 1) {
            throwError("BOUNDARY", 400, "BOUNDARY_SHEET_FIRST_COLUMN_INVALID_ERROR",
                `Data is invalid: The "${firstColumn}" column must contain only one unique value across all rows.`);
        }

        for (const header of localizedHeadersOfBoundarySheet) {
            const value = obj[header];

            if (!value) {
                // Mark that an empty value has been found for the first time
                firstEmptyFound = true;
            } else if (firstEmptyFound) {
                // If a non-empty value is found after an empty value in the expected order, throw an error
                throwError("BOUNDARY", 400, "BOUNDARY_SHEET_UPLOADED_INVALID_ERROR",
                    `Data is invalid in object at index ${rowNumbers?.[index] ?? index + 2}: Non-empty value for key "${header}" found after an empty value in the left.`);
            }
        }
    });
}

async function validateDownloadRequest(request: any) {
    const { tenantId, hierarchyType } = request.query;
    validateBodyViaSchema(downloadRequestSchema, request.query);
    if (tenantId != request?.body?.RequestInfo?.userInfo?.tenantId) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId in userInfo and query should be the same");
    }
    await validateHierarchyType(request, hierarchyType, tenantId);
}

async function validateSearchRequest(request: any) {
    const { SearchCriteria } = request.body;
    if (!SearchCriteria) {
        throwError("COMMON", 400, "VALIDATION_ERROR", "SearchCriteria is required");
    }
    validateBodyViaSchema(searchCriteriaSchema, SearchCriteria);
}


export { validateProcessRequest ,validateBoundarySheetDataInCreateFlow,validateDownloadRequest,validateSearchRequest};