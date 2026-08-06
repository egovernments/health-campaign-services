import { throwError,getLocalizedHeaders } from "../utils/genericUtils";
import { processRequestSchema } from "../config/models/processRequestSchema";
import { validateBodyViaSchema,validateHierarchyType} from "./genericValidator";
import config from "../config";
import { httpRequest } from "../utils/request";
// import { validateFileMetaDataViaFileUrl } from "../utils/excelUtils";
import {getLocalizedName,getBoundaryTabName,getHeadersOfBoundarySheet,getHierarchy,validateHeaders} from "../utils/boundaryUtils";
import {getSheetData, createAndUploadFile} from "../api/genericApis";
import { getExcelWorkbookFromFileURL, markRowsAsDuplicate } from "../utils/excelUtils";
import { logger } from "../utils/logger";
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
    // validate boundary names contain only allowed characters (reject [] {} <> etc.; allow letters of any
    // language plus the punctuation that occurs in real place names)
    validateBoundaryNameCharacters(boundaryData, localizedHierarchy);
    // Duplicate rows are reported by handing the operator their own sheet back with the offending rows
    // highlighted, instead of a message they have to map onto row numbers themselves. Runs LAST of the
    // sheet checks so a sheet with a harder problem (missing root level, illegal characters) still fails
    // with that error rather than being sent back as a duplicates file.
    const duplicateRowNumbers = findDuplicateRows(boundaryData);
    if (duplicateRowNumbers.length > 0) {
        await markDuplicateRowsAndUploadSheet(request, fileUrl, localizedBoundaryTab, duplicateRowNumbers);
    }
}

function validateForRootElementExists(boundaryData: any[], hierachy: any[], sheetName: string) {
    const root = hierachy[0];
    if (!(boundaryData.filter(e => e[root]).length == boundaryData.length)) {
        throwError("COMMON", 400, "VALIDATION_ERROR", `Invalid Boundary Sheet. Root level Boundary not present in every row  of Sheet ${sheetName}`)
    }
}

/**
 * Returns the sheet row numbers of rows that repeat an earlier row in EVERY column (string cells compared
 * trimmed). Only the second and later copies of each distinct row are reported - the first occurrence is
 * the one the operator keeps, so it is deliberately not flagged.
 *
 * Unchanged from the check that used to fail the upload outright; only the reporting changed.
 */
function findDuplicateRows(boundaryData: any[]): number[] {
    // Step 1: Trim strings in all rows
    boundaryData = boundaryData.map(row =>
        Object.fromEntries(
            Object.entries(row).map(([key, value]) =>
                [key, typeof value === "string" ? value.trim() : value]
            )
        )
    );
    const seen = new Set<string>();
    const duplicateRowNumbers: number[] = [];
    for (const row of boundaryData) {
        const rowNumber = row["!row#number!"];
        const rowCopy = { ...row };
        delete rowCopy["!row#number!"];
        // Serialize row as a string (key), which is much faster than deep object comparison
        const rowKey = JSON.stringify(rowCopy);
        if (seen.has(rowKey)) {
            duplicateRowNumbers.push(Number(rowNumber));
        } else {
            seen.add(rowKey);
        }
    }
    return duplicateRowNumbers;
}

// How many duplicate row numbers to name in the message that accompanies the returned file. A sheet can be
// duplicated wholesale, so the list is capped; the highlighted file itself is the complete record.
const MAX_REPORTED_DUPLICATE_ROWS = 20;

/**
 * Hands the operator their own boundary sheet back with every duplicate row highlighted red, instead of
 * failing the upload with row numbers they have to find by hand. Mirrors excel-ingestion's unified-sheet
 * validation: the file is uploaded to filestore and the run is marked invalid, so the console can poll
 * _process-search and offer the marked file for download.
 *
 * Nothing is ingested for such a run - processBoundaryService stops before processRequest when this stash
 * is present, which is what makes this a validation-time outcome rather than a partially-processed upload.
 *
 * If the file cannot be produced or uploaded for any reason, this falls back to the original hard failure:
 * the sheet genuinely has duplicates, and reporting that as a plain validation error is always better than
 * letting an infrastructure problem turn into a silent success.
 */
async function markDuplicateRowsAndUploadSheet(request: any, fileUrl: string, localizedBoundaryTab: string, duplicateRowNumbers: number[]) {
    const reportedRowNumbers = duplicateRowNumbers.slice(0, MAX_REPORTED_DUPLICATE_ROWS);
    const notReported = duplicateRowNumbers.length - reportedRowNumbers.length;
    const rowNumbersForMessage = `${reportedRowNumbers.join(', ')}${notReported > 0 ? ` ... and ${notReported} more` : ''}`;
    try {
        // Reuses the workbook already parsed for this request (excelUtils memoises it per request), so no
        // second download or parse. Safe to colour in place: this run never reaches the ingestion parse.
        const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, localizedBoundaryTab, request);
        const sheet = workbook.getWorksheet(localizedBoundaryTab);
        if (!sheet) throw new Error(`Sheet ${localizedBoundaryTab} not found in the uploaded workbook`);
        const markedRows = markRowsAsDuplicate(sheet, duplicateRowNumbers);
        const uploadedFile: any = await createAndUploadFile(workbook, request);
        const processedFileStoreId = uploadedFile?.[0]?.fileStoreId;
        if (!processedFileStoreId) throw new Error("Filestore did not return an id for the marked boundary sheet");
        logger.info(`Boundary Sheet has ${duplicateRowNumbers.length} duplicate row(s); highlighted ${markedRows} row(s) and uploaded the marked sheet as ${processedFileStoreId}`);
        request.body.duplicateRowValidation = {
            processedFileStoreId,
            count: duplicateRowNumbers.length,
            rowNumbers: duplicateRowNumbers,
            message: `Boundary Sheet has duplicate rows at rowNumber ${rowNumbersForMessage}. The uploaded sheet has been returned with those rows highlighted; remove them and upload again.`,
        };
    } catch (error: any) {
        logger.error(`Could not produce the highlighted duplicate-rows sheet (${error?.message || error}); failing validation with the plain error instead`);
        throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary Sheet has duplicate rows at rowNumber ${rowNumbersForMessage}`);
    }
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

function validateBoundarySheetDataInCreateFlow(boundarySheetData: any, localizedHeadersOfBoundarySheet: any) {
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
                    `Data is invalid in object at index ${index + 2}: Non-empty value for key "${header}" found after an empty value in the left.`);
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