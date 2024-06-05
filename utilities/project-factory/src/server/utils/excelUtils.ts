import * as ExcelJS from "exceljs";
import { throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { logger } from "./logger";

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
  if (sheetName&&!worksheet) {
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

export { getNewExcelWorkbook, getExcelWorkbookFromFileURL };
