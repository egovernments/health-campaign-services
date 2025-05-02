import * as ExcelJS from "exceljs";
import { changeFirstRowColumnColour, throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { logger } from "./logger";
import config from "../config";
import { freezeUnfreezeColumnsForProcessedFile, getColumnIndexByHeader, hideColumnsOfProcessedFile } from "./onGoingCampaignUpdateUtils";
import { getLocalizedName } from "./campaignUtils";
import createAndSearch from "../config/createAndSearch";
import { getLocaleFromRequestInfo } from "./localisationUtils";
import { usageColumnStatus } from "../config/constants";
/**
 * Function to create a new Excel workbook using the ExcelJS library
 * @returns {ExcelJS.Workbook} - A new Excel workbook object
 */
const getNewExcelWorkbook = () => {
  const workbook = new ExcelJS.Workbook();
  return workbook;
};

// Function to retrieve workbook from Excel file URL and sheet name
const getExcelWorkbookFromFileURL = async (
  fileUrl: string,
  sheetName?: string
) => {
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


  const workbook = getNewExcelWorkbook();
  await workbook.xlsx.load(responseFile);
  logger.info("workbook created based on the fileresponse");


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


export async function validateFileMetaDataViaFileUrl(fileUrl: string, expectedLocale: string, expectedCampaignId: string, action: string) {
  if (!fileUrl) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "There is an issue while reading the file as no file URL was found.");
  }
  else if(action === "validate"){
    const workbook = await getExcelWorkbookFromFileURL(fileUrl);
    if (!workbook) {
      throwError("COMMON", 400, "VALIDATION_ERROR", "There is an issue while reading the file as no workbook was found.");
    }
    else {
      validateFileMetadata(workbook, expectedLocale, expectedCampaignId);
    }
  }
}

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


export function enrichTemplateMetaData(updatedWorkbook : any, request : any ){
  if(request?.body?.RequestInfo && request?.query?.campaignId){
    updatedWorkbook.keywords = `${getLocaleFromRequestInfo(request?.body?.RequestInfo)}#${request?.query?.campaignId}`
  }
}

function updateFontNameToRoboto(worksheet: ExcelJS.Worksheet) {
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
}

function formatWorksheet(worksheet: any, datas: any, headerSet: any) {
  // Add empty rows after the main header
  // worksheet.addRow([]);
  // worksheet.addRow([]);
  worksheet.addRow([]);

  // Add the data rows with text wrapping
  const lineHeight = 15; // Set an approximate line height
  const maxCharactersPerLine = 100; // Set a maximum number of characters per line for wrapping

  datas.forEach((data: any) => {
    const row = worksheet.addRow([data]);
    row.eachCell({ includeEmpty: true }, (cell: any) => {
      cell.alignment = { vertical: 'middle', horizontal: 'left', wrapText: true }; // Apply text wrapping
      // Calculate the required row height based on content length
      const numberOfLines = Math.ceil(data.length / maxCharactersPerLine);
      row.height = numberOfLines * lineHeight;

      // Make the header text bold
      if (headerSet.has(cell.value)) {
        cell.font = { bold: true };
      }
    });
  });

  worksheet.getColumn(1).width = 130;
  logger.info(`Freezing the whole sheet ${worksheet.name}`);
  worksheet?.eachRow((row: any) => {
    row.eachCell((cell: any) => {
      cell.protection = { locked: true };
    });
  });
  worksheet.protect('passwordhere', { selectLockedCells: true });
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

// Function to adjust column width
function adjustColumnWidth(sheet: any, colNumber: number, columnWidth: number) {
  sheet.getColumn(colNumber).width = columnWidth;
}

// Function to adjust row height based on content
export function adjustRowHeight(row: any, cell: any, columnWidth: number) {
  const text = cell.value ? cell.value.toString() : '';
  const lines = Math.ceil(text.length / (columnWidth - 2)); // Approximate number of lines
  row.height = Math.max(row.height ?? 0, lines * 15);
}

// Function to format cells in other rows
function formatOtherRows(row: any, frozeCells: boolean) {
  row.eachCell((cell: any) => {
    if (frozeCells) {
      cell.protection = { locked: true };
    }
  });
}

// Function to finalize the sheet settings
function finalizeSheet(request: any, sheet: any, frozeCells: boolean, frozeWholeSheet: boolean, localizationMap?: any, fileUrl?: any, schema?: any) {
  const type = (request?.query?.type || request?.body?.ResourceDetails?.type);
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
    columnsToHide = ["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD",...schema?.columnsToHide];
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





function lockTargetFields(newSheet: any, columnsNotToBeFreezed: any, boundaryCodeColumnIndex: any) {
  // Make every cell locked by default
  newSheet?.eachRow((row: any) => {
    row.eachCell((cell: any) => {
      cell.protection = { locked: true };
    });
  });

  // // Get headers in the first row and filter out empty items
  const headers = newSheet.getRow(1).values.filter((header: any) => header);
  logger.info(`Filtered Headers in the first row : ${headers}`);

  // Unlock cells in the target columns
  if (Array.isArray(columnsNotToBeFreezed) && columnsNotToBeFreezed.length > 0) {
    columnsNotToBeFreezed.forEach((header) => {
      const targetColumnNumber = headers.indexOf(header) + 1; // Excel columns are 1-based
      logger.info(`Header: ${header}, Target Column Index: ${targetColumnNumber}`);
      if (targetColumnNumber > -1) {
        newSheet?.eachRow((row: any, rowNumber: number) => {
          changeFirstRowColumnColour(newSheet, 'B6D7A8', targetColumnNumber);
          if (rowNumber === 1) return;

          const cell = row.getCell(targetColumnNumber);
          cell.protection = { locked: false };
        });

      } else {
        console.error(`Header "${header}" not found in the first row`);
      }
    });
  }

  // Hide the boundary code column
  if (boundaryCodeColumnIndex !== -1) {
    newSheet.getColumn(boundaryCodeColumnIndex + 1).hidden = true; // Excel columns are 1-based
  }

  // Protect the sheet with a password (optional)
  newSheet.protect('passwordhere', {
    selectLockedCells: true,
    selectUnlockedCells: true,
  });
}

export function enrichUsageColumnForFacility(worksheet: any, localizationMap: any) {
  const configType = "facility";
  const usageColumn = getLocalizedName(createAndSearch[configType]?.activeColumnName, localizationMap);
  if (usageColumn) {
    const usageColumnIndex = getColumnIndexByHeader(worksheet, usageColumn);
    if (usageColumnIndex !== -1) {
      worksheet?.eachRow((row: any, rowNumber: number) => {
        if (rowNumber === 1) return; // Skip header row
        const cell = row.getCell(usageColumnIndex);
        // Only change the value if it is empty or null
        if (!cell.value) {
          cell.value = usageColumnStatus.inactive;
        }
      });
    }
  }
}

function protectSheet(sheet: any) {
  sheet.protect('passwordhere', {
    selectLockedCells: true,
    selectUnlockedCells: true,
  });
}


export const findColumnByHeader = (header: string, worksheet: any) => {
  for (let col = 1; col <= worksheet.columnCount; col++) {
    if (worksheet.getCell(1, col).value === header) {
      return String.fromCharCode(64 + col); // Convert to Excel column letter (e.g., 1 -> A, 2 -> B)
    }
  }
  return "";
};

export function addHeadersFromSchema(
  worksheet: ExcelJS.Worksheet,
  schema: any,
  localizationMap?: Record<string, string>
) {
  const properties = schema?.properties || {};

  // Step 1: Sort properties based on orderNumber
  const sortedProps : any[] = Object.entries(properties)
    .sort(([_, a]: any, [__, b]: any) => (a.orderNumber || 0) - (b.orderNumber || 0));

  const expandedProps: {
    key: string;
    header: string;
    width: number;
    color?: string;
    hidden?: boolean;
  }[] = [];

  // Step 2: Expand multi-select fields
  for (const [key, prop] of sortedProps) {
    const baseHeader = getLocalizedName(key, localizationMap);
    const width = prop.width || 40;

    if (prop.multiSelectDetails?.maxSelections) {
      const max = prop.multiSelectDetails.maxSelections;

      for (let i = 1; i <= max; i++) {
        expandedProps.push({
          key: `${key}_MULTISELECT_${i}`,
          header: `${baseHeader} ${i}`,
          width,
          color: prop.color,
          hidden: false,
        });
      }
    }

    expandedProps.push({
      key,
      header: baseHeader,
      width,
      color: prop.color,
      hidden: prop.hideColumn,
    });
  }

  // Step 3: Set worksheet columns
  worksheet.columns = expandedProps.map(({ key, header, width, hidden }) => ({
    header,
    key,
    width,
    hidden: hidden || false,
  }));

  // Step 4: Apply styles to header row
  const headerRow = worksheet.getRow(1);
  expandedProps.forEach(({ color, width }, index) => {
    const cell = headerRow.getCell(index + 1);

    if (color) {
      cell.fill = {
        type: 'pattern',
        pattern: 'solid',
        fgColor: { argb: color.replace('#', '') },
      };
    }

    cell.font = { bold: true, size: 12 };
    cell.alignment = { vertical: 'middle', horizontal: 'center', wrapText: true };
    cell.protection = { locked: true };

    adjustRowHeight(headerRow, cell, Number(width));
  });
}


export function freezeUnfreezeColumns(
  worksheet: ExcelJS.Worksheet,
  columnsToFreeze: string[],
  localizationMap?: Record<string, string>
) {
  const headerRow = worksheet.getRow(1);
  const headerMap: Record<string, number> = {};

  // Step 1: Map localized headers to column indices
  headerRow.eachCell((cell: any, col) => {
    const header = getLocalizedName(cell?.value?.toString(), localizationMap);
    if (header) headerMap[header] = col;
  });

  const freezeIndexes = columnsToFreeze
    .map(header => headerMap[header])
    .filter((col): col is number => !!col);

  const rowCount = worksheet.rowCount;
  const maxCol = worksheet.columnCount;
  const unfrozeTillRow = Number(config.values.unfrozeTillRow);
  const unfrozeTillColumn = Number(config.values.unfrozeTillColumn);

  // Step 2: Unlock default editable area, skipping frozen columns
  for (let r = 2; r <= unfrozeTillRow; r++) {
    for (let c = 1; c <= unfrozeTillColumn; c++) {
      if (!freezeIndexes.includes(c)) {
        worksheet.getCell(r, c).protection = { locked: false };
      }
    }
  }

  // Step 3: Lock the first row (header) always
  for (let c = 1; c <= maxCol; c++) {
    worksheet.getCell(1, c).protection = { locked: true };
  }

  // Step 4: Lock only the specified frozen columns (excluding first row)
  for (let r = 2; r <= rowCount; r++) {
    for (const col of freezeIndexes) {
      worksheet.getCell(r, col).protection = { locked: true };
    }
  }

  // Step 5: Apply or remove protection
  if (freezeIndexes.length > 0) {
    worksheet.protect('passwordhere', {
      selectLockedCells: true,
      selectUnlockedCells: true
    });
  } else {
    worksheet.unprotect();
  }
}





export { getNewExcelWorkbook, getExcelWorkbookFromFileURL, formatWorksheet, addDataToSheet, lockTargetFields, updateFontNameToRoboto, formatFirstRow, formatOtherRows, finalizeSheet, protectSheet };
