import { throwError,getLocalizedHeaders } from "../utils/genericUtils";
import { processRequestSchema } from "../config/models/processRequestSchema";
import { validateBodyViaSchema,validateHierarchyType} from "./genericValidator";
import config from "../config";
import { httpRequest } from "../utils/request";
// import { validateFileMetaDataViaFileUrl } from "../utils/excelUtils";
import {getLocalizedName,getBoundaryTabName,getHeadersOfBoundarySheet,getHierarchy,validateHeaders} from "../utils/boundaryUtils";
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
    // Reported in TWO stages, deliberately - not one combined message, and not one message per check.
    //
    // Duplicates go first and ALONE, because the only way to fix them is to DELETE rows, which shifts
    // every row number below the deletion. Any "row 24244, column Village" reported alongside them would
    // be stale the moment the operator removes a duplicate row above it. Everything else is fixed by
    // editing a cell in place, so those row numbers stay valid.
    //
    // Stage 2 then reports the remaining checks TOGETHER against a sheet whose numbering is stable, so
    // the operator is not walked through one round trip per class of error - which is what the original
    // throw-on-first-failure behaviour did (strip duplicates, re-upload, only then hear about illegal
    // characters, re-upload again).
    // validate for duplicate rows(array of objects)
    const duplicateProblems = collectDuplicateRowProblems(boundaryData);
    if (duplicateProblems.length > 0) {
        throwError("COMMON", 400, "VALIDATION_ERROR", duplicateProblems.join(" | "));
    }

    const problems: string[] = [];
    //validate for whether root boundary level column should not be empty
    problems.push(...collectRootElementProblems(boundaryData, localizedHierarchy, localizedBoundaryTab));
    // validate boundary names contain only allowed characters (reject [] {} <> etc.; allow letters of any
    // language plus the punctuation that occurs in real place names)
    problems.push(...collectBoundaryNameCharacterProblems(boundaryData, localizedHierarchy));

    if (problems.length > 0) {
        // One VALIDATION_ERROR carrying every remaining problem. Kept as the registered code so this
        // still surfaces as a clean HTTP 400; an unregistered code resolves to UNKNOWN_ERROR -> 500.
        throwError("COMMON", 400, "VALIDATION_ERROR", problems.join(" | "));
    }
}

/** Root-level problem, if any, returned rather than thrown so it can be reported with the other checks. */
function collectRootElementProblems(boundaryData: any[], hierachy: any[], sheetName: string): string[] {
    const root = hierachy[0];
    if (!(boundaryData.filter(e => e[root]).length == boundaryData.length)) {
        return [`Invalid Boundary Sheet. Root level Boundary not present in every row  of Sheet ${sheetName}`];
    }
    return [];
}

/** Duplicate rows, if any, returned rather than thrown. Behaviour of the check itself is unchanged. */
function collectDuplicateRowProblems(boundaryData: any[]): string[] {
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
    if (duplicateRowNumbers.length === 0) {
        return [];
    }
    // Capped like the name-character check: a wholesale-duplicated 50k-row sheet must not produce a
    // multi-megabyte message, especially now that several problems share one response.
    const shown = duplicateRowNumbers.slice(0, MAX_REPORTED_DUPLICATE_ROWS);
    const extra = duplicateRowNumbers.length > MAX_REPORTED_DUPLICATE_ROWS
        ? ` ... and ${duplicateRowNumbers.length - MAX_REPORTED_DUPLICATE_ROWS} more` : "";
    return [`Boundary Sheet has duplicate rows at rowNumber ${shown.join(', ')}${extra}`];
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

// Same cap for duplicate row numbers - a sheet can be duplicated wholesale.
const MAX_REPORTED_DUPLICATE_ROWS = 20;

/**
 * Every cell whose name contains a disallowed character, as one message, or an empty array.
 * Collects across the whole sheet before returning so all offending cells surface at once.
 */
function collectBoundaryNameCharacterProblems(boundaryData: any[], localizedHierarchy: any[]): string[] {
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
    if (problems.length === 0) {
        return [];
    }
    const shown = problems.slice(0, MAX_REPORTED_NAME_PROBLEMS);
    const extra = problems.length > MAX_REPORTED_NAME_PROBLEMS
        ? ` ... and ${problems.length - MAX_REPORTED_NAME_PROBLEMS} more` : "";
    return [
        `Boundary names contain characters that are not allowed. ` +
        `Allowed: letters (any language), numbers, spaces and ' & - / . ( ) _ , : ; @ + ! ? . ` +
        `Problems: ${shown.join("; ")}${extra}`
    ];
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