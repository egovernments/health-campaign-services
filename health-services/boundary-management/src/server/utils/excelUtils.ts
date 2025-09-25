import * as ExcelJS from "exceljs";
import { throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { logger } from "./logger";
import config from "../config";

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

export{getExcelWorkbookFromFileURL};