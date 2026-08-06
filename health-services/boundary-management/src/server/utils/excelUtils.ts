import * as ExcelJS from "exceljs";
import { throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { logger } from "./logger";
import config from "../config";
import {getLocalizedName} from "../utils/boundaryUtils";
import createAndSearch from "../config/createAndSearch";
import{getColumnIndexByHeader ,freezeUnfreezeColumnsForProcessedFile
  ,hideColumnsOfProcessedFile
} from "./onGoingCampaignUpdateUtil";



// export async function validateFileMetaDataViaFileUrl(fileUrl: string, expectedLocale: string, expectedCampaignId: string, action: string) {
//   if (!fileUrl) {
//     throwError("COMMON", 400, "VALIDATION_ERROR", "There is an issue while reading the file as no file URL was found.");
//   }
//   else if (action === "validate") {
//     const workbook = await getExcelWorkbookFromFileURL(fileUrl);
//     if (!workbook) {
//       throwError("COMMON", 400, "VALIDATION_ERROR", "There is an issue while reading the file as no workbook was found.");
//     }
//     else {
//       validateFileMetadata(workbook, expectedLocale, expectedCampaignId);
//     }
//   }
// }

const getNewExcelWorkbook = () => {
  const workbook = new ExcelJS.Workbook();
  return workbook;
};
// Per-request parse-once cache. Held OFF the request object in a module-level WeakMap keyed by the
// request (auto-GCs with the request), so the parsed Workbook is NEVER a property of request/request.body.
// This is critical: request.body is spread into downstream boundary-service /_search payloads, and an
// ExcelJS Workbook has a circular structure (_worksheets <-> _workbook) that throws
// "Converting circular structure to JSON" when axios stringifies the request body.
const workbookCacheByRequest = new WeakMap<object, Record<string, any>>();

// Function to retrieve workbook from Excel file URL and sheet name.
// Optional `request` enables a per-request parse-once cache: the same uploaded file was previously
// downloaded + DOM-parsed once per phase (validation headers, validation data, auto-generate) — 3
// full parses of the same immutable file in a single request execution. When `request` is supplied
// the workbook is memoised in the WeakMap above (keyed by fileUrl) so the download + xlsx.load
// happens ONCE; later callers reuse the already-parsed read-only workbook. Callers that do not pass
// `request` keep the original download-every-time behaviour, so this is opt-in and cannot regress
// other paths.
const getExcelWorkbookFromFileURL = async (
  fileUrl: string,
  sheetName?: string,
  request?: any
) => {
  let workbook: any;
  const cacheStore = request && typeof request === "object" ? request : undefined;
  // Key on the path only, dropping the query string: filestore returns pre-signed URLs whose
  // signature/expiry query params can differ between the validation and processing resolutions of
  // the SAME fileStoreId, which would otherwise defeat the cache. Within one request there is exactly
  // one uploaded file, so the path uniquely identifies it.
  const cacheKey = typeof fileUrl === "string" ? fileUrl.split("?")[0] : fileUrl;
  const cached = cacheStore ? workbookCacheByRequest.get(cacheStore)?.[cacheKey] : undefined;
  if (cached) {
    logger.info("reusing already-parsed workbook for fileurl (parse-once)");
    workbook = cached;
  } else {
    // Define headers for HTTP request
    const headers = {
      "Content-Type": "application/json",
      Accept: "application/pdf",
    };
    logger.info("loading for the file based on fileurl");
    // Make HTTP request to retrieve Excel file as arraybuffer
    const responseFile = await httpRequest(
      fileUrl,
      null,
      {},
      "get",
      "arraybuffer",
      headers
    );
    logger.info("received the file response");


    workbook = getNewExcelWorkbook();
    await workbook.xlsx.load(responseFile);
    logger.info("workbook created based on the fileresponse");

    if (cacheStore) {
      let perRequest = workbookCacheByRequest.get(cacheStore);
      if (!perRequest) {
        perRequest = {};
        workbookCacheByRequest.set(cacheStore, perRequest);
      }
      perRequest[cacheKey] = workbook;
    }
  }

  if (sheetName) {
    // Check if the specified sheet exists in the workbook
    const worksheet = workbook.getWorksheet(sheetName);
    if (!worksheet) {
      throwError(
        "FILE",
        400,
        "INVALID_SHEETNAME",
        `Sheet with name "${sheetName}" is not present in the file.`
      );
    }
  }

  // Return the workbook
  return workbook;
};

export const validateFileMetadata = (workbook: any, expectedLocale: string, expectedCampaignId: string) => {
  // Retrieve and validate keywords from the workbook's custom properties
  const keywords = workbook?.keywords;
  if (!keywords || !keywords.includes("#")) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      "The template doesn't have campaign metadata. Please upload the generated template only."
    );
  }

  const [templateLocale, templateCampaignId] = keywords.split("#");

  // Ensure there are exactly two parts in the metadata
  if (!templateLocale || !templateCampaignId) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      "The template doesn't have valid campaign metadata. Please upload the generated template only."
    );
  }

  // Validate locale if provided
  if (templateLocale !== expectedLocale) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      `The template doesn't have matching locale metadata. Please upload the generated template for the current locale.`
    );
  }

  // Validate campaignId if provided
  if (templateCampaignId !== expectedCampaignId && config.values.validateCampaignIdInMetadata) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      `The template doesn't have matching campaign metadata. Please upload the generated template for the current campaign only.`
    );
  }
};

// Function to add data to the sheet
function addDataToSheet(
  request: any,
  sheet: any,
  sheetData: any,
  firstRowColor: string = '93C47D',
  columnWidth: number = 40,
  frozeCells: boolean = false,
  frozeWholeSheet: boolean = false,
  localizationMap?: any,
  fileUrl?: any,
  schema?: any
) {
  sheetData?.forEach((row: any, index: number) => {

    const worksheetRow = sheet.addRow(row);
    if (index === 0) {
      formatFirstRow(worksheetRow, sheet, firstRowColor, columnWidth, frozeCells);
    } else {
      formatOtherRows(worksheetRow, frozeCells);
    }
  });
  finalizeSheet(request, sheet, frozeCells, frozeWholeSheet, localizationMap, fileUrl, schema);
  manageMultiSelect(sheet, schema, localizationMap, fileUrl, sheetData);
}

export function manageMultiSelect(sheet: any, schema: any, localizationMap?: any, fileUrl?: string, sheetData?: any[]) {
  const headerRow = sheet.getRow(1); // Assuming first row is the header
  const rowsLength = sheetData?.length || 0;
  const isChildOfSomeCampaign = Boolean(fileUrl);

  for (const property in schema?.properties) {
    if (schema?.properties[property]?.multiSelectDetails) {
      const multiSelectDetails = schema?.properties[property]?.multiSelectDetails;
      const maxSelections = multiSelectDetails?.maxSelections;
      const currentColumnHeader = getLocalizedName(property, localizationMap);
      const enumsList = multiSelectDetails?.enum;

      // Find column index for the current column
      let currentColumnIndex = -1;
      headerRow.eachCell((cell: any, colNumber: any) => {
        if (cell.value === currentColumnHeader) {
          currentColumnIndex = colNumber;
        }
      });

      if (currentColumnIndex === -1) {
        console.warn(`Column with header ${currentColumnHeader} not found`);
        continue;
      }

      // Apply dropdowns for previous columns
      if (Array.isArray(enumsList) && enumsList.length > 0) {
        applyDropdownsForMultiSelect(sheet, currentColumnIndex, maxSelections, enumsList, isChildOfSomeCampaign, rowsLength);
      }

      // Apply CONCATENATE formula
      applyConcatenateFormula(sheet, currentColumnIndex, maxSelections);

      // Hide the column if specified
      if (schema?.properties[property]?.hideColumn) {
        sheet.getColumn(currentColumnIndex).hidden = true;
      }
    }
  }
}

// -----------------------------
// Function to Apply CONCATENATE Formula
// -----------------------------
function applyConcatenateFormula(sheet: any, currentColumnIndex: number, maxSelections: number) {
  const colLetters = [];
  for (let i = 1; i <= maxSelections; i++) {
    const colIndex = currentColumnIndex - maxSelections + i - 1;
    const colLetter = getColumnLetter(colIndex);
    colLetters.push(colLetter);
  }

  const blankCheck = colLetters.map(col => `ISBLANK(${col}2)`).join(", ");
  const formulaParts = colLetters.map(
    (col, i) => `IF(ISBLANK(${col}2), "", ${col}2 & IF(${i === colLetters.length - 1}, "", ","))`
  );

  const formula = `=IF(AND(${blankCheck}), "", IF(RIGHT(CONCATENATE(${formulaParts.join(",")}),1)=",", LEFT(CONCATENATE(${formulaParts.join(",")}), LEN(CONCATENATE(${formulaParts.join(",")}) )-1), CONCATENATE(${formulaParts.join(",")})))`;


  for (let row = 2; row <= sheet.rowCount; row++) {
    const rowFormula = formula.replace(/2/g, row.toString());
    sheet.getCell(row, currentColumnIndex).value = {
      formula: rowFormula
    };
  }
}

// Utility function to get column letter from index
function getColumnLetter(index: number): string {
  let letter = '';
  while (index > 0) {
    const remainder = (index - 1) % 26;
    letter = String.fromCharCode(65 + remainder) + letter;
    index = Math.floor((index - 1) / 26);
  }
  return letter;
}

// -----------------------------
// Function to Apply Dropdowns
// -----------------------------
function applyDropdownsForMultiSelect(sheet: any, currentColumnIndex: number, maxSelections: number, enumsList: string[], isChildOfSomeCampaign: boolean = false, rowsLength: number = 1) {
  // Loop through columns for multi-select
  for (let i = 1; i <= maxSelections; i++) {
    const colIndex = currentColumnIndex - maxSelections + i - 1;

    // Apply dropdown validation to each cell (skipping the first row)
    sheet.getColumn(colIndex).eachCell({ includeEmpty: true }, (cell: any, rowNumber: number) => {
      if (rowNumber > 1) {
        cell.dataValidation = {
          type: 'list',
          formulae: [`"${enumsList.join(',')}"`],
          showDropDown: true,
          error: 'Please select a value from the dropdown list.',
          errorStyle: 'stop',
          showErrorMessage: true,
          errorTitle: 'Invalid Entry',
          allowBlank: true // Allow blank entries
        };
      }

      // Freeze the current cell (the multi-select cell itself)
      if (rowNumber > 1 && rowNumber <= rowsLength && isChildOfSomeCampaign) {
        cell.protection = {
          locked: true, // Lock the cell
        };
      }
    });
  }
}

function performUnfreezeCells(sheet: any, localizationMap?: any, fileUrl?: any) {
  logger.info(`Unfreezing the sheet ${sheet.name}`);

  let lastFilledColumn = 1;
  sheet.getRow(1).eachCell((cell: any, colNumber: number) => {
    if (cell.value !== undefined && cell.value !== null && cell.value !== '') {
      lastFilledColumn = colNumber;
    }
  });

  for (let row = 1; row <= parseInt(config.values.unfrozeTillRow); row++) {
    for (let col = 1; col <= lastFilledColumn; col++) {
      const cell = sheet.getCell(row, col);
      if (!cell.value && cell.value !== 0) {
        cell.protection = { locked: false };
      }
    }
  }
  // sheet.protect('passwordhere', { selectLockedCells: true, selectUnlockedCells: true });
}
function performFreezeWholeSheet(sheet: any) {
  logger.info(`Freezing the whole sheet ${sheet.name}`);
  sheet?.eachRow((row: any) => {
    row.eachCell((cell: any) => {
      cell.protection = { locked: true };
    });
  });
  sheet.protect('passwordhere', { selectLockedCells: true });
}

// Function to finalize the sheet settings
function finalizeSheet(request: any, sheet: any, frozeCells: boolean, frozeWholeSheet: boolean, localizationMap?: any, fileUrl?: any, schema?: any) {
  const type = 'boundaryManagement';
  const typeWithoutWith = type.includes('With') ? type.split('With')[0] : type;
  const createAndSearchConfig = createAndSearch[typeWithoutWith];
  const columnIndexesToBeFreezed: any = [];
  const columnIndexesToBeHidden: any = [];
  if (frozeCells) {
    performUnfreezeCells(sheet, localizationMap, fileUrl);
  }
  if (frozeWholeSheet) {
    performFreezeWholeSheet(sheet);
  }
  let columnsToBeFreezed: any[] = [];
  let columnsToHide: any[] = [];
  if (fileUrl) {
    columnsToHide = ["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD", ...schema?.columnsToHide];
    columnsToHide.forEach((column: any) => {
      const localizedColumn = getLocalizedName(column, localizationMap);
      const columnIndex = getColumnIndexByHeader(sheet, localizedColumn);
      columnIndexesToBeHidden.push(columnIndex);
    });

    columnsToBeFreezed = ["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD", ...schema?.columnsToBeFreezed]
    columnsToBeFreezed.forEach((column: any) => {
      const localizedColumn = getLocalizedName(column, localizationMap);
      const columnIndex = getColumnIndexByHeader(sheet, localizedColumn);
      columnIndexesToBeFreezed.push(columnIndex);
    });
    const activeColumnWhichIsNotToBeFreezed = createAndSearchConfig?.activeColumnName;
    const boundaryCodeMandatoryColumnWhichIsNotToBeFreezed = getLocalizedName(config?.boundary?.boundaryCodeMandatory, localizationMap);
    const localizedActiveColumnWhichIsNotToBeFreezed = getLocalizedName(activeColumnWhichIsNotToBeFreezed, localizationMap);
    const columnIndexOfActiveColumn = getColumnIndexByHeader(sheet, localizedActiveColumnWhichIsNotToBeFreezed);
    const columnIndexOfBoundaryCodeMandatory = getColumnIndexByHeader(sheet, boundaryCodeMandatoryColumnWhichIsNotToBeFreezed);
    freezeUnfreezeColumnsForProcessedFile(sheet, columnIndexesToBeFreezed, [columnIndexOfActiveColumn, columnIndexOfBoundaryCodeMandatory]); // Example columns to freeze and unfreeze
    hideColumnsOfProcessedFile(sheet, columnIndexesToBeHidden);
  }
  updateFontNameToRoboto(sheet);
  sheet.views = [{ state: 'frozen', ySplit: 1, zoomScale: 110 }];
}

function updateFontNameToRoboto(worksheet: ExcelJS.Worksheet) {
  logger.info("Updating font name to Roboto...");
  worksheet?.eachRow({ includeEmpty: true }, (row) => {
    row.eachCell({ includeEmpty: true }, (cell) => {
      // Preserve existing font properties
      const existingFont = cell.font || {};

      // Update only the font name to Roboto
      cell.font = {
        ...existingFont, // Spread existing properties
        name: 'Roboto'   // Update the font name
      };
    });
  });
  logger.info("Font name updated to Roboto.");
}

// Function to format cells in other rows
function formatOtherRows(row: any, frozeCells: boolean) {
  row.eachCell((cell: any) => {
    if (frozeCells) {
      cell.protection = { locked: true };
    }
  });
}

// Function to format the first row
function formatFirstRow(row: any, sheet: any, firstRowColor: string, columnWidth: number, frozeCells: boolean) {
  row.eachCell((cell: any, colNumber: number) => {
    setFirstRowCellStyles(cell, firstRowColor, frozeCells);
    adjustColumnWidth(sheet, colNumber, columnWidth);
    adjustRowHeight(row, cell, columnWidth);
  });
}

// Function to set styles for the first row's cells
function setFirstRowCellStyles(cell: any, firstRowColor: string, frozeCells: boolean) {
  cell.fill = {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: firstRowColor }
  };

  cell.font = { bold: true };

  if (frozeCells) {
    cell.protection = { locked: true };
  }

  cell.alignment = { vertical: 'top', horizontal: 'left', wrapText: true };
}

export function enrichTemplateMetaData(updatedWorkbook: any, locale: string, campaignId: string) {
  logger.info("Enriching template metadata...");
  updatedWorkbook.keywords = `${locale}#${campaignId}`
  logger.info("Enriched template metadata");
}

// Function to adjust column width
function adjustColumnWidth(sheet: any, colNumber: number, columnWidth: number) {
  sheet.getColumn(colNumber).width = columnWidth;
}

// Function to adjust row height based on content
export function adjustRowHeight(row: any, cell: any, columnWidth: number) {
  const text = cell.value ? cell.value.toString() : '';
  const denominator = Math.max(1, columnWidth - 10);
  const lines = Math.ceil(text.length / denominator);
  row.height = Math.max(row.height ?? 0, lines * 15);
}

// Renders **bold** spans in a plain string as ExcelJS rich text. The MDMS readme copy marks emphasis with
// the same ** markers project-factory uses (it wraps a block header in ** when isHeaderBold is set), so the
// markers must never reach the user's screen. Ported from project-factory's processBoldFormatting.
function applyBoldMarkersAsRichText(cell: any) {
  if (typeof cell.value !== "string") return;
  const text: string = cell.value;
  // Split on the marker: every ODD segment sat between a pair of ** and is the bold part
  // ("plain**bold**plain" -> ["plain", "bold", "plain"]). An odd number of markers means one is
  // unpaired, in which case the text is left exactly as the config author wrote it.
  const segments = text.split("**");
  if (segments.length < 3 || segments.length % 2 === 0) return;
  const richText = segments
    .map((segment, index) => ({ text: segment, font: { bold: index % 2 === 1 } }))
    .filter(part => part.text !== "");
  if (richText.length > 0) cell.value = { richText };
}

/**
 * Adds the README (instructions) tab built from the MDMS readme config to an existing workbook.
 *
 * Only blocks flagged `inSheet` are rendered - the rest of the master is console-only copy. Each block is
 * separated by two blank rows, then its header, then one row per description line; every header and
 * description string in the master is itself a LOCALISATION KEY, so each is resolved through the
 * localisation map (an unresolved key renders as the raw key rather than blank). Same layout project-factory
 * produces, so both consoles show the operator the same instructions sheet.
 *
 * The sheet is appended LAST on purpose: the console pre-validates a boundary upload by reading
 * `workbook.SheetNames[0]` (DIGIT-Frontend campaign-manager validateBoundaryExcel.js), so the boundary data
 * tab must stay the first sheet or every upload of a downloaded template would be rejected client-side.
 *
 * @returns true when a sheet was added, false when there was nothing to render
 */
function addReadMeSheetToWorkbook(workbook: any, readMeConfig: any, localizationMap?: { [key: string]: string }) {
  const blocks = (readMeConfig?.texts || []).filter((text: any) => text?.inSheet);
  if (blocks.length === 0) {
    logger.warn("README config has no blocks marked inSheet; skipping the README sheet");
    return false;
  }
  const sheetName = getLocalizedName(config.readMe.readMeTab, localizationMap);
  const sheet = workbook.addWorksheet(sheetName);
  const columnWidth = config.readMe.columnWidth;
  sheet.getColumn(1).width = columnWidth;

  const lines: string[] = [];
  for (const block of blocks) {
    if (lines.length > 0) lines.push("", "");
    const header = getLocalizedName(block.header, localizationMap);
    lines.push(block?.isHeaderBold ? `**${header}**` : header);
    for (const description of block?.descriptions || []) {
      lines.push(getLocalizedName(description?.text, localizationMap));
    }
  }

  for (const line of lines) {
    const row = sheet.addRow([line]);
    const cell = row.getCell(1);
    cell.alignment = { vertical: "top", horizontal: "left", wrapText: true };
    applyBoldMarkersAsRichText(cell);
    if (line) adjustRowHeight(row, cell, columnWidth);
  }
  logger.info(`Added README sheet "${sheetName}" with ${blocks.length} instruction block(s)`);
  return true;
}

// Fill applied to every populated cell of a duplicate row in the returned validation file. Solid red, the
// same cell.fill shape setFirstRowCellStyles already uses for the header colour.
const DUPLICATE_ROW_FILL = { type: "pattern", pattern: "solid", fgColor: { argb: "FFFF0000" } };

/**
 * Paints every populated cell of the given sheet rows red, in place, and returns how many rows were painted.
 *
 * Row numbers are the sheet's own 1-based numbers (the "!row#number!" values stamped during parsing), so
 * they index rows directly. Columns are walked by index against the header row rather than with
 * row.eachCell, which skips blank cells - a boundary row legitimately leaves deeper hierarchy levels empty
 * and those cells must still be coloured for the row to read as one block.
 */
function markRowsAsDuplicate(sheet: any, rowNumbers: number[]) {
  const columnCount = sheet?.getRow(1)?.cellCount || 0;
  if (!columnCount) return 0;
  let markedRows = 0;
  for (const rowNumber of rowNumbers) {
    const row = sheet.getRow(rowNumber);
    if (!row) continue;
    for (let columnNumber = 1; columnNumber <= columnCount; columnNumber++) {
      row.getCell(columnNumber).fill = { ...DUPLICATE_ROW_FILL };
    }
    row.commit?.();
    markedRows++;
  }
  return markedRows;
}

export{getExcelWorkbookFromFileURL,getNewExcelWorkbook,addDataToSheet,addReadMeSheetToWorkbook,markRowsAsDuplicate};