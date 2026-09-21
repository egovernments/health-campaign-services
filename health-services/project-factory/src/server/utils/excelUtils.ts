import * as ExcelJS from "exceljs";
import { changeFirstRowColumnColour, throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { logger } from "./logger";
import config from "../config";
import { freezeUnfreezeColumnsForProcessedFile, getColumnIndexByHeader, hideColumnsOfProcessedFile } from "./onGoingCampaignUpdateUtils";
import { getLocalizedName } from "./campaignUtils";
import createAndSearch from "../config/createAndSearch";
import { usageColumnStatus } from "../config/constants";
/**
 * Function to create a new Excel workbook using the ExcelJS library
 * @returns {ExcelJS.Workbook} - A new Excel workbook object
 */
const getNewExcelWorkbook = () => {
  const workbook = new ExcelJS.Workbook();
  return workbook;
};

const getExcelWorkbookFromFileURL = async (
  fileUrl: string,
  sheetName?: string
) => {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/pdf",
  };
  logger.info("loading for the file based on fileurl");
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

  return workbook;
};


/** Loads a workbook from its URL and validates its embedded locale/campaign metadata (only when action is "validate"). */
export async function validateFileMetaDataViaFileUrl(fileUrl: string, expectedLocale: string, expectedCampaignId: string, action: string) {
  if (!fileUrl) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "There is an issue while reading the file as no file URL was found.");
  }
  else if (action === "validate") {
    const workbook = await getExcelWorkbookFromFileURL(fileUrl);
    if (!workbook) {
      throwError("COMMON", 400, "VALIDATION_ERROR", "There is an issue while reading the file as no workbook was found.");
    }
    else {
      validateFileMetadata(workbook, expectedLocale, expectedCampaignId);
    }
  }
}

/** Rejects an uploaded template unless its keywords carry a locale#campaignId matching the current campaign. */
export const validateFileMetadata = (workbook: any, expectedLocale: string, expectedCampaignId: string) => {
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

  if (!templateLocale || !templateCampaignId) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      "The template doesn't have valid campaign metadata. Please upload the generated template only."
    );
  }

  if (templateLocale !== expectedLocale) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      `The template doesn't have matching locale metadata. Please upload the generated template for the current locale.`
    );
  }

  if (templateCampaignId !== expectedCampaignId && config.values.validateCampaignIdInMetadata) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      `The template doesn't have matching campaign metadata. Please upload the generated template for the current campaign only.`
    );
  }
};

/** Validates only the campaignId portion of a template's keyword metadata against the expected campaign. */
export const validateFileCmapaignIdInMetaData = (workbook: any, expectedCampaignId: string) => {
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

  if (!templateLocale || !templateCampaignId) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      "The template doesn't have valid campaign metadata. Please upload the generated template only."
    );
  }

  if (templateCampaignId !== expectedCampaignId && config.values.validateCampaignIdInMetadata) {
    throwError(
      "FILE",
      400,
      "INVALID_TEMPLATE",
      `The template doesn't have matching campaign metadata. Please upload the generated template for the current campaign only.`
    );
  }
};


export function enrichTemplateMetaData(updatedWorkbook: any, locale: string, campaignId: string) {
  logger.info("Enriching template metadata...");
  updatedWorkbook.keywords = `${locale}#${campaignId}`
  logger.info("Enriched template metadata");
}

export function getLocaleFromWorkbook(workbook: any): string | null {
  logger.info("Extracting locale from workbook...");

  if (!workbook?.keywords) {
    logger.warn("No keywords found in workbook. Returning null.");
    return null;
  }

  const locale = workbook.keywords.split("#")[0]?.trim();

  logger.info("Locale extracted:", locale);
  return locale || null;
}


function updateFontNameToRoboto(worksheet: ExcelJS.Worksheet) {
  logger.info("Updating font name to Roboto...");
  worksheet?.eachRow({ includeEmpty: true }, (row) => {
    row.eachCell({ includeEmpty: true }, (cell) => {
      // Preserve existing font properties, changing only the name
      const existingFont = cell.font || {};
      cell.font = {
        ...existingFont,
        name: 'Roboto'
      };
    });
  });
  logger.info("Font name updated to Roboto.");
}

function formatWorksheet(worksheet: any, datas: any, headerSet: any) {
  worksheet.addRow([]);

  const lineHeight = 15;
  const maxCharactersPerLine = 100;

  datas.forEach((data: any) => {
    const row = worksheet.addRow([data]);
    row.eachCell({ includeEmpty: true }, (cell: any) => {
      cell.alignment = { vertical: 'middle', horizontal: 'left', wrapText: true };
      const numberOfLines = Math.ceil(data.length / maxCharactersPerLine);
      row.height = numberOfLines * lineHeight;

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


/** Adds enum dropdowns (and locks child-campaign cells) to the spread-out _MULTISELECT_N columns for each multi-select schema property. */
export function manageMultiSelect(sheet: any, schema: any, localizationMap?: any, fileUrl?: string, sheetData?: any[]) {
  const headerRow = sheet.getRow(1);
  const rowsLength = sheetData?.length || 0;
  const isChildOfSomeCampaign = Boolean(fileUrl);

  for (const property in schema?.properties) {
    if (schema?.properties[property]?.multiSelectDetails) {
      const multiSelectDetails = schema?.properties[property]?.multiSelectDetails;
      const maxSelections = multiSelectDetails?.maxSelections;
      const lastMultiSelectHeader = getLocalizedName(`${property}_MULTISELECT_${maxSelections}`, localizationMap);
      const enumsList = multiSelectDetails?.enum;

      let lastMultiSelectColIndex = -1;
      headerRow.eachCell((cell: any, colNumber: any) => {
        if (cell.value === lastMultiSelectHeader) {
          lastMultiSelectColIndex = colNumber;
        }
      });

      if (lastMultiSelectColIndex === -1) {
        console.warn(`Column with header ${lastMultiSelectHeader} not found`);
        continue;
      }

      if (Array.isArray(enumsList) && enumsList.length > 0) {
        applyDropdownsForMultiSelect(sheet, lastMultiSelectColIndex + 1, maxSelections, enumsList, isChildOfSomeCampaign, rowsLength);
      }
    }
  }
}

/** Like manageMultiSelect but matches raw (un-localized) header keys — used on templates whose headers are not yet translated. */
export function manageMultiSelectUnlocalised(sheet: any, schema: any, fileUrl?: string, sheetData?: any[]) {
  const headerRow = sheet.getRow(1);
  const rowsLength = sheetData?.length || 0;
  const isChildOfSomeCampaign = Boolean(fileUrl);

  for (const property in schema?.properties) {
    if (schema?.properties[property]?.multiSelectDetails) {
      const multiSelectDetails = schema?.properties[property]?.multiSelectDetails;
      const maxSelections = multiSelectDetails?.maxSelections;
      const enumsList = multiSelectDetails?.enum;
      const lastMultiSelectHeader = `${property}_MULTISELECT_${maxSelections}`;

      let lastMultiSelectColIndex = -1;
      headerRow.eachCell((cell: any, colNumber: any) => {
        if (cell.value === lastMultiSelectHeader) {
          lastMultiSelectColIndex = colNumber;
        }
      });

      if (lastMultiSelectColIndex === -1) {
        console.warn(`Column with header ${lastMultiSelectHeader} not found`);
        continue;
      }

      if (Array.isArray(enumsList) && enumsList.length > 0) {
        applyDropdownsForMultiSelectForUnlocalised(sheet, lastMultiSelectColIndex + 1, maxSelections, enumsList, isChildOfSomeCampaign, rowsLength);
      }
    }
  }
}

function applyDropdownsForMultiSelect(sheet: any, currentColumnIndex: number, maxSelections: number, enumsList: string[], isChildOfSomeCampaign: boolean = false, rowsLength: number = 1) {
  for (let i = 1; i <= maxSelections; i++) {
    const colIndex = currentColumnIndex - maxSelections + i - 1;

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
          allowBlank: true
        };
      }

      if (rowNumber > 1 && rowNumber <= rowsLength && isChildOfSomeCampaign) {
        cell.protection = {
          locked: true,
        };
      }
    });
  }
}

function applyDropdownsForMultiSelectForUnlocalised(sheet: any, currentColumnIndex: number, maxSelections: number, enumsList: string[], isChildOfSomeCampaign: boolean = false, rowsLength: number = 1) {
  for (let i = 1; i <= maxSelections; i++) {
    const colIndex = currentColumnIndex - maxSelections + i - 1;

    sheet.getColumn(colIndex).eachCell({ includeEmpty: true }, (cell: any, rowNumber: number) => {
      if (rowNumber > 2) {
        cell.dataValidation = {
          type: 'list',
          formulae: [`"${enumsList.join(',')}"`],
          showDropDown: true,
          error: 'Please select a value from the dropdown list.',
          errorStyle: 'stop',
          showErrorMessage: true,
          errorTitle: 'Invalid Entry',
          allowBlank: true
        };
      }

      if (rowNumber > 2 && rowNumber <= rowsLength && isChildOfSomeCampaign) {
        cell.protection = {
          locked: true,
        };
      }
    });
  }
}


function formatFirstRow(row: any, sheet: any, firstRowColor: string, columnWidth: number, frozeCells: boolean) {
  row.eachCell((cell: any, colNumber: number) => {
    setFirstRowCellStyles(cell, firstRowColor, frozeCells);
    adjustColumnWidth(sheet, colNumber, columnWidth);
    adjustRowHeight(row, cell, columnWidth);
  });
}

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

function adjustColumnWidth(sheet: any, colNumber: number, columnWidth: number) {
  sheet.getColumn(colNumber).width = columnWidth;
}

/** Grows a row's height to fit wrapped cell text based on content length and column width. */
export function adjustRowHeight(row: any, cell: any, columnWidth: number) {
  const text = cell.value ? cell.value.toString() : '';
  const denominator = Math.max(1, columnWidth - 10);
  const lines = Math.ceil(text.length / denominator);
  row.height = Math.max(row.height ?? 0, lines * 15);
}

function formatOtherRows(row: any, frozeCells: boolean) {
  row.eachCell((cell: any) => {
    if (frozeCells) {
      cell.protection = { locked: true };
    }
  });
}

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
    freezeUnfreezeColumnsForProcessedFile(sheet, columnIndexesToBeFreezed, [columnIndexOfActiveColumn, columnIndexOfBoundaryCodeMandatory]);
    hideColumnsOfProcessedFile(sheet, columnIndexesToBeHidden);
  }
  updateFontNameToRoboto(sheet);
  sheet.views = [{ state: 'frozen', ySplit: 1, zoomScale: 110 }];
}





function lockTargetFields(newSheet: any, columnsNotToBeFreezed: any, boundaryCodeColumnIndex: any) {
  newSheet?.eachRow((row: any) => {
    row.eachCell((cell: any) => {
      cell.protection = { locked: true };
    });
  });

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

  if (boundaryCodeColumnIndex !== -1) {
    newSheet.getColumn(boundaryCodeColumnIndex + 1).hidden = true; // Excel columns are 1-based
  }

  newSheet.protect('passwordhere', {
    selectLockedCells: true,
    selectUnlockedCells: true,
  });
}

/** Defaults blank facility usage cells to inactive so downstream validation treats un-filled facilities as inactive. */
export function enrichUsageColumnForFacility(worksheet: any, localizationMap: any) {
  const configType = "facility";
  const usageColumn = getLocalizedName(createAndSearch[configType]?.activeColumnName, localizationMap);
  if (usageColumn) {
    const usageColumnIndex = getColumnIndexByHeader(worksheet, usageColumn);
    if (usageColumnIndex !== -1) {
      worksheet?.eachRow((row: any, rowNumber: number) => {
        if (rowNumber === 1) return;
        const cell = row.getCell(usageColumnIndex);
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


/** Finds a header in row 1 and returns its Excel column letter (A, B, …), or "" if not found. */
export const findColumnByHeader = (header: string, worksheet: any) => {
  for (let col = 1; col <= worksheet.columnCount; col++) {
    if (worksheet.getCell(1, col).value === header) {
      return String.fromCharCode(64 + col); // 1 -> A, 2 -> B, ...
    }
  }
  return "";
};


/** Applies per-column cell locking to an uploaded template: some columns freeze fully, some only up to the last data row, some only when empty. */
export async function freezeUnfreezeColumns(
  worksheet: ExcelJS.Worksheet,
  columnsToFreeze: string[],
  columnsToUnFreezeTillData: string[],
  columnsToFreezeTillData: string[],
  columnsToFreezeIfFilled: string[]
) {
  logger.info(`Freezing columns: ${columnsToFreeze}`);
  const headerRow = worksheet.getRow(1);
  const headerMap: Record<string, number> = {};

  headerRow.eachCell((cell: any, col) => {
    const header = cell.value;
    if (header) headerMap[header] = col;
  });

  const freezeIndexes = columnsToFreeze
    .map(header => headerMap[header])
    .filter((col): col is number => !!col);

  const tillDataIndexes = columnsToUnFreezeTillData
    .map(header => headerMap[header])
    .filter((col): col is number => !!col);

  const freezeTillDataIndexes = columnsToFreezeTillData
    .map(header => headerMap[header])
    .filter((col): col is number => !!col);

  const rowCount = worksheet.rowCount;
  const maxCol = worksheet.columnCount;
  const unfrozeTillRow = Number(config.values.unfrozeTillRow);
  const unfrozeTillColumn = Number(config.values.unfrozeTillColumn);

  // Unlock the default editable area, skipping frozen columns and empty headers
  for (let r = 3; r <= unfrozeTillRow; r++) {
    for (let c = 1; c <= unfrozeTillColumn; c++) {
      const headerValue: any = worksheet.getCell(1, c).value;
      if (!freezeIndexes.includes(c) && !tillDataIndexes.includes(c) && headerValue) {
        if (columnsToFreezeIfFilled.includes(headerValue)) {
          const value = worksheet.getCell(r, c).value;
          if (value === null || value === undefined || value === "") {
            worksheet.getCell(r, c).protection = { locked: false };
          }
        } else {
          worksheet.getCell(r, c).protection = { locked: false };
        }
      }
    }
  }

  // Unlock columnsToUnFreezeTillData only up to the last data row
  for (let r = 3; r <= rowCount; r++) {
    for (const col of tillDataIndexes) {
      worksheet.getCell(r, col).protection = { locked: false };
    }
  }

  // Header and second row are always locked
  for (let c = 1; c <= maxCol; c++) {
    worksheet.getCell(1, c).protection = { locked: true };
    worksheet.getCell(2, c).protection = { locked: true };
  }

  for (let r = 3; r <= rowCount; r++) {
    for (const col of freezeIndexes) {
      worksheet.getCell(r, col).protection = { locked: true };
    }
  }

  // Lock columnsToFreezeTillData up to the last data row
  for (let r = 3; r <= rowCount; r++) {
    for (const col of freezeTillDataIndexes) {
      worksheet.getCell(r, col).protection = { locked: true };
    }
  }

  await worksheet.protect('passwordhere', {
    selectLockedCells: true,
    selectUnlockedCells: true,
  });
}


export { getNewExcelWorkbook, getExcelWorkbookFromFileURL, formatWorksheet, addDataToSheet, lockTargetFields, updateFontNameToRoboto, formatFirstRow, formatOtherRows, finalizeSheet, protectSheet };
