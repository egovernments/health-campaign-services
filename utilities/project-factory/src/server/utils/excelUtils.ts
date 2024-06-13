import * as ExcelJS from "exceljs";
import { changeFirstRowColumnColour, throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { logger } from "./logger";
import config from "../config";
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
  sheetName: string
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

  // Create a new workbook instance
  const workbook = getNewExcelWorkbook();
  await workbook.xlsx.load(responseFile);
  logger.info("workbook created based on the fileresponse");

  // Check if the specified sheet exists in the workbook
  const worksheet = workbook.getWorksheet(sheetName);
  if (sheetName && !worksheet) {
    throwError(
      "FILE",
      400,
      "INVALID_SHEETNAME",
      `Sheet with name "${sheetName}" is not present in the file.`
    );
  }

  // Return the workbook
  return workbook;
};

function updateFontNameToRoboto(worksheet: ExcelJS.Worksheet) {
  worksheet.eachRow({ includeEmpty: true }, (row) => {
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
  worksheet.addRow([]);
  worksheet.addRow([]);
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
  worksheet.eachRow((row: any) => {
    row.eachCell((cell: any) => {
      cell.protection = { locked: true };
    });
  });
  worksheet.protect('passwordhere', { selectLockedCells: true });
}

function performUnfreezeCells(sheet: any) {
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
  sheet.protect('passwordhere', { selectLockedCells: true, selectUnlockedCells: true });
}


function performFreezeWholeSheet(sheet: any) {
  logger.info(`Freezing the whole sheet ${sheet.name}`);
  sheet.eachRow((row: any) => {
    row.eachCell((cell: any) => {
      cell.protection = { locked: true };
    });
  });
  sheet.protect('passwordhere', { selectLockedCells: true });
}

function addDataToSheet(sheet: any, sheetData: any, firstRowColor: any = '93C47D', columnWidth = 40, frozeCells = false, frozeWholeSheet = false) {
  sheetData?.forEach((row: any, index: number) => {
    const worksheetRow = sheet.addRow(row);

    // Apply fill color to each cell in the first row and make cells bold
    if (index === 0) {
      worksheetRow.eachCell((cell: any, colNumber: number) => {
        // Set cell fill color
        cell.fill = {
          type: 'pattern',
          pattern: 'solid',
          fgColor: { argb: firstRowColor } // Green color
        };

        // Set font to bold
        cell.font = { bold: true };

        // Enable text wrapping
        cell.alignment = { wrapText: true };

        // Optionally lock the cell
        if (frozeCells) {
          cell.protection = { locked: true };
        }

        // Update column width based on the length of the cell's text
        const currentWidth = sheet.getColumn(colNumber).width || columnWidth; // Default width or current width
        const newWidth = Math.max(currentWidth, cell.value.toString().length + 2); // Add padding
        sheet.getColumn(colNumber).width = newWidth;
      });

    }
    worksheetRow.eachCell((cell: any) => {
      if (frozeCells) {
        cell.protection = { locked: true };
      }
    });
  });

  // Protect the entire sheet to enable cell protection settings
  if (frozeCells) {
    performUnfreezeCells(sheet);
  }
  if (frozeWholeSheet) {
    performFreezeWholeSheet(sheet);
  }
  updateFontNameToRoboto(sheet);
}


function lockTargetFields(newSheet: any, columnsNotToBeFreezed: any, boundaryCodeColumnIndex: any) {
  // Make every cell locked by default
  newSheet.eachRow((row: any) => {
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
        newSheet.eachRow((row: any, rowNumber: number) => {
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


export { getNewExcelWorkbook, getExcelWorkbookFromFileURL, formatWorksheet, addDataToSheet, lockTargetFields, updateFontNameToRoboto };
