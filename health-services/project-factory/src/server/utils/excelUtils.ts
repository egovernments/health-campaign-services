import * as ExcelJS from "exceljs";
import { changeFirstRowColumnColour, throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { logger } from "./logger";
import config from "../config";
import { freezeUnfreezeColumnsForProcessedFile, getColumnIndexByHeader, hideColumnsOfProcessedFile } from "./onGoingCampaignUpdateUtils";
import { getLocalizedName } from "./campaignUtils";
import createAndSearch from "../config/createAndSearch";
import { fetchFileFromFilestore } from "../api/coreApis";
import { usageColumnStatus } from "../config/constants";
import { decryptPassword } from "./encryptionUtils";
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


export function enrichTemplateMetaData(updatedWorkbook : any, locale: string, campaignId: string) {
  if(locale && campaignId){
    updatedWorkbook.keywords = `${locale}#${campaignId}`
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
  type: string,
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
  finalizeSheet(type, sheet, frozeCells, frozeWholeSheet, localizationMap, fileUrl, schema);
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
function adjustRowHeight(row: any, cell: any, columnWidth: number) {
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
function finalizeSheet(type: string, sheet: any, frozeCells: boolean, frozeWholeSheet: boolean, localizationMap?: any, fileUrl?: any, schema?: any) {
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

export async function getLocaleFromCampaignFiles(fileId:string, tenantId:string) {
  const url = await fetchFileFromFilestore(fileId, tenantId);
  const workbook = await getExcelWorkbookFromFileURL(url);
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

  return templateLocale;
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

export function fillDataInProcessedUserSheet(
  userWorkSheet: any,
  campaignEmployees: any[],
  campaignMappings: any[]
) {
  logger.info(`Filling data in processed user sheet`);
  const mobieNumberAndBoundaryCodesMapping = campaignMappings.reduce((acc: any, mapping: any) => {
    const key = `N${mapping?.mappingIdentifier?.toString()}N`; // Ensure the key is a string
    if (!acc[key]) {
      acc[key] = [];
    }
    acc[key].push(mapping?.boundaryCode);
    return acc;
  }, {});

  // Get the first row (header) values
  const headerRow = userWorkSheet.getRow(1);
  const headerOfsheetValues = headerRow?.values || [];

  // Find the first empty column in the header
  let emptyColumnIndex = -1;
  for (let i = 1; i < headerOfsheetValues.length; i++) {
    if (!headerOfsheetValues[i]) {
      emptyColumnIndex = i;
      break;
    }
  }

  // Define new header values
  const newHeaderValues = [
    "#status#",
    "#errorDetails#",
    createAndSearch?.["user"]?.uniqueIdentifierColumnName,
    "UserName",
    "Password",
  ];

  // Add new headers and format columns
  emptyColumnIndex = addHeadersAndFormatForCampaignEmployees(userWorkSheet, headerRow, emptyColumnIndex, newHeaderValues);

  // Clear existing data rows while preserving formatting
  const rowCount = userWorkSheet.rowCount;
  for (let rowIndex = 2; rowIndex <= rowCount; rowIndex++) {
    const row = userWorkSheet.getRow(rowIndex);
    row.values = []; // Clear row data while preserving formatting
  }

  // Populate new data rows
  campaignEmployees
    .filter((employee: any) => employee?.userServiceUuid) // Only include employees with a userServiceUuid
    .forEach((employee: any, index: number) => {
      const rowIndex = index + 2; // Start from the second row
      const row = userWorkSheet.getRow(rowIndex);
      const currentMobileNumberKey = `N${employee?.mobileNumber?.toString()}N`;
      const currentBoundaryValues = mobieNumberAndBoundaryCodesMapping[currentMobileNumberKey]?.join(",");
      row.values = [
        employee?.name,
        parseInt(employee?.mobileNumber),
        employee?.role,
        employee?.employeeType,
        currentBoundaryValues,
        employee?.isActive ? usageColumnStatus.active : usageColumnStatus.inactive,
        "CREATED",
        "",
        employee?.userServiceUuid,
        employee?.userName,
        decryptPassword(employee?.tokenString),
      ];
    });

  logger.info("Worksheet values updated successfully.");
}

function addHeadersAndFormatForCampaignEmployees(
  userWorkSheet: any,
  headerRow: any,
  emptyColumnIndex: number,
  newHeaderValues: string[]
) {
  if (emptyColumnIndex === -1) {
    emptyColumnIndex = headerRow.values.length;
  }

  // Add new headers
  for (let i = 0; i < newHeaderValues.length; i++) {
    const headerCell = headerRow.getCell(emptyColumnIndex + i);
    headerCell.value = newHeaderValues[i];
  }

  // Format columns
  const columnsToFormat = [
    { columnIndex: emptyColumnIndex, width: 40, color: "CCCC00" },
    { columnIndex: emptyColumnIndex + 1, width: 40, color: "CCCC00" },
    { columnIndex: emptyColumnIndex + 2, width: 40, color: "FF9248" },
    { columnIndex: emptyColumnIndex + 3, width: 40, color: "FF9248" },
    { columnIndex: emptyColumnIndex + 4, width: 40, color: "FF9248" },
  ];

  columnsToFormat.forEach(({ columnIndex, width, color }) => {
    const column = userWorkSheet.getColumn(columnIndex);
    column.width = width;

    const headerCell = headerRow.getCell(columnIndex);
    headerCell.fill = {
      type: "pattern",
      pattern: "solid",
      fgColor: { argb: color },
    };
    headerCell.font = { bold: true };
  });

  return emptyColumnIndex;
}



export { getNewExcelWorkbook, getExcelWorkbookFromFileURL, formatWorksheet, addDataToSheet, lockTargetFields, updateFontNameToRoboto, formatFirstRow, formatOtherRows, finalizeSheet, protectSheet };
