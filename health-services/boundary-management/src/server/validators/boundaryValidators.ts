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
    //validate for whether root boundary level column should not be empty
    validateForRootElementExists(boundaryData, localizedHierarchy, localizedBoundaryTab);
    // validate for duplicate rows(array of objects)
    validateForDuplicateRows(boundaryData);
    // validate boundary names contain only allowed characters (reject [] {} <> etc.; allow letters of any
    // language plus the punctuation that occurs in real place names)
    validateBoundaryNameCharacters(boundaryData, localizedHierarchy);
    // validate that no boundary above the lowest level is left without a child anywhere in the sheet
    validateForMissingChildEntries(boundaryData, localizedHierarchy, localizedBoundaryTab);
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

// An N-level hierarchy must actually reach level N on every branch: a boundary that sits above the
// lowest level and has NO child anywhere in the sheet is silently lossy downstream, not merely untidy.
// excel-ingestion's BoundaryHierarchySheetGenerator only emits target-sheet rows for boundaries that
// carry a value at the LAST hierarchy level ("Only include boundaries that have data at the last level
// (leaf boundaries)"), so a branch that stops early produces zero target rows - no target can be set and
// no delivery is planned for it, with no error anywhere. Before this check the upload returned 200 /
// status "completed" and the loss was invisible until someone noticed the missing rows.
//
// Completeness is judged per DISTINCT NODE across the whole sheet, not per row. Rows that merely restate
// an ancestor (a, then a>b, then a>b>c) are fine - a and a>b each have a child on a later row. Only a
// node that no row ever extends is reported.
const MAX_REPORTED_MISSING_CHILDREN = 20;

// Same path-aware node identity the codegen uses (boundaryKeyOf / __path in genericUtils): level and
// value joined by \u0000, ancestors joined by \u0001. Two boundaries sharing a name under different
// parents must stay distinct, and the same boundary restated on many rows must collapse to one node.
// Boundary names cannot contain these separators - validateBoundaryNameCharacters (run just above)
// rejects control characters outright.
const NODE_FIELD_SEPARATOR = "\u0000";
const NODE_LEVEL_SEPARATOR = "\u0001";

function validateForMissingChildEntries(boundaryData: any[], localizedHierarchy: any[], sheetName: string) {
    const levelCount = localizedHierarchy?.length || 0;
    // A single-level hierarchy has no "next level", so the rule is vacuous.
    if (levelCount < 2 || !boundaryData?.length) return;

    const parentsWithAChild = new Set<string>();
    // pathKey -> the node that some row bottoms out at. Only these can ever be childless; a node that
    // appears purely as an ancestor column is by construction already in parentsWithAChild.
    const deepestNodes = new Map<string, { levelIndex: number, labels: string[], rowNumber: any }>();

    for (const row of boundaryData) {
        const labels: string[] = [];
        let hasInternalGap = false;
        for (let i = 0; i < levelCount; i++) {
            const raw = row?.[localizedHierarchy[i]];
            // Cells are not canonically trimmed until updateBoundaryData, which runs later and only in the
            // async flow - so " " arrives here truthy and would otherwise become a phantom node distinct
            // from the real one. String() because numeric cells stay JS numbers through the parse path,
            // and a level literally named 0 must count as populated rather than blank.
            const value = (raw === undefined || raw === null) ? "" : String(raw).trim();
            if (value === "") {
                // A populated cell to the RIGHT of a blank one is a different defect, already owned by
                // validateBoundarySheetDataInCreateFlow. Skip the row rather than invent a parent for it.
                for (let j = i + 1; j < levelCount; j++) {
                    const later = row?.[localizedHierarchy[j]];
                    if (later !== undefined && later !== null && String(later).trim() !== "") {
                        hasInternalGap = true;
                        break;
                    }
                }
                break;
            }
            labels.push(value);
        }
        if (labels.length === 0) continue;

        let pathKey = "";
        for (let i = 0; i < labels.length; i++) {
            pathKey += (i > 0 ? NODE_LEVEL_SEPARATOR : "") + localizedHierarchy[i] + NODE_FIELD_SEPARATOR + labels[i];
            if (i < labels.length - 1) parentsWithAChild.add(pathKey);
        }
        // The parent->child facts above are recorded even for a gapped row: a blank further right does
        // not make "a has a child b" any less true, and dropping them let the message accuse a parent
        // whose child is plainly sitting in the sheet. Only the row's TERMINAL node is withheld - a
        // gapped row is a different defect and must not also be reported as a childless leaf here.
        if (hasInternalGap) continue;
        if (!deepestNodes.has(pathKey)) {
            deepestNodes.set(pathKey, {
                levelIndex: labels.length - 1,
                labels: labels.slice(),
                rowNumber: row?.["!row#number!"]
            });
        }
    }

    const problems: string[] = [];
    let problemCount = 0;
    // Map preserves insertion order, so offenders come out in sheet order without a sort. Array.from is
    // required, not stylistic: tsconfig targets es5 with downlevelIteration off, so iterating a Map
    // directly is a TS2802 compile error - same reason genericApis.ts:192 wraps its keys() walk.
    for (const [pathKey, node] of Array.from(deepestNodes.entries())) {
        if (node.levelIndex >= levelCount - 1) continue;   // already at the lowest level - nothing owed
        if (parentsWithAChild.has(pathKey)) continue;      // some other row does extend below it
        problemCount++;
        if (problems.length < MAX_REPORTED_MISSING_CHILDREN) {
            problems.push(
                `Row ${node.rowNumber}: ${localizedHierarchy[node.levelIndex]} "${node.labels[node.levelIndex]}" ` +
                `(${node.labels.join(" > ")}) has no ${localizedHierarchy[node.levelIndex + 1]} under it anywhere in the sheet`
            );
        }
    }

    if (problemCount > 0) {
        const extra = problemCount > MAX_REPORTED_MISSING_CHILDREN
            ? ` ... and ${problemCount - MAX_REPORTED_MISSING_CHILDREN} more` : "";
        throwError(
            "BOUNDARY", 400, "MISSING_CHILD_BOUNDARY",
            `Invalid Boundary Sheet ${sheetName}. The following boundaries have no child at the next level: ` +
            `${problems.join("; ")}${extra}`
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
