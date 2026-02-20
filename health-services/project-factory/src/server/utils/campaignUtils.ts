import { RequestInfo } from "../config/models/requestInfoSchema";
import { defaultheader, httpRequest } from "./request";
import config from "../config/index";
import { v4 as uuidv4 } from "uuid";
import { produceModifiedMessages } from "../kafka/Producer";
import {
  getCampaignSearchResponse,
  getHierarchy,
} from "../api/campaignApis";
import {
  getCampaignNumber,
  createAndUploadFile,
  getTargetSheetDataAfterCode,
  callMdmsTypeSchema,
  getSheetDataFromWorksheet,
  getTargetWorkbook,
  getSheetData,
  searchMDMS
} from "../api/genericApis";
import { getFormattedStringForDebug, logger } from "./logger";
import createAndSearch from "../config/createAndSearch";
import {
  addDataToSheet,
  createBoundaryDataMainSheet,
  createReadMeSheet,
  getConfigurableColumnHeadersFromSchemaForTargetSheet,
  getCurrentProcesses,
  getLocalizedHeaders,
  getLocalizedMessagesHandler,
  getMdmsDataBasedOnCampaignType,
  getRelatedDataWithCampaign,
  prepareProcessesInDb,
  replicateRequest,
  searchAllGeneratedResources,
  throwError,
} from "./genericUtils";
import { executeQuery, getTableName } from "./db";
import { searchResourceDetailsFromDB, toResourceDetailsResponse } from "./resourceDetailsUtils";
import {
  campaignDetailsTransformer,
  genericResourceTransformer,
} from "./transforms/searchResponseConstructor";
import {
  allProcesses,
  campaignStatuses,
  dataRowStatuses,
  generatedResourceStatuses,
  headingMapping,
  processStatuses,
  resourceDataStatuses,
  resourceStatuses,
  resourceTypes,
  usageColumnStatus,
} from "../config/constants";
import { getBoundaryTabName } from "./boundaryUtils";
import {
  getResourceConfigsByPhase,
  getPhase2Types,
  hasDependenciesMet,
} from "../config/resourceTypeRegistry";
import {
  searchProjectTypeCampaignService,
  updateProjectTypeCampaignService,
} from "../service/campaignManageService";
import {
  validateBoundaryOfResouces
} from "../validators/campaignValidators";
import {
  getExcelWorkbookFromFileURL,
  getNewExcelWorkbook,
  lockTargetFields,
  updateFontNameToRoboto,
} from "./excelUtils";
import {
  areBoundariesSame,
  callGenerateIfBoundariesOrCampaignTypeDiffer,
  isGenerationTriggerNeeded,
} from "./generateUtils";
import {
  generateDynamicTargetHeaders,
  isDynamicTargetTemplateForProjectType,
} from "./targetUtils";
import {
  fetchProjectsWithProjectId,
  getBoundariesFromCampaignSearchResponse,
  getColumnIndexByHeader,
  hideColumnsOfProcessedFile,
  modifyNewSheetData,
  unhideColumnsOfProcessedFile,
} from "./onGoingCampaignUpdateUtils";
import { changeCreateDataForMicroplan, lockSheet } from "./microplanUtils";
const _ = require("lodash");
import { searchDataService } from "../service/dataManageService";
import { createMdmsData, searchBoundaryRelationshipData, searchMDMSDataViaV2Api } from "../api/coreApis";
import { createServiceDefinition, searchServiceDefinitions } from "../api/serviceRequestApis";
import {
  fetchFacilityData,
  fetchTargetData,
  fetchUserData,
} from "./microplanIntergration";
import { GenerateTemplateQuery } from "../models/GenerateTemplateQuery";
import { getLocaleFromRequest, getLocaleFromRequestInfo } from "./localisationUtils";
import { generateDataService } from "../service/sheetManageService";
import { CampaignResource, toCampaignResource } from "../config/models/resourceTypes";
import Localisation from "../controllers/localisationController/localisation.controller";
import { triggerUserCredentialEmailFlow } from "./mailUtils";

function updateRange(range: any, worksheet: any) {
  let maxColumnIndex = 0;

  // Iterate through each row to find the last column with data
  for (let row = range.s.r; row <= range.e.r; row++) {
    const rowCells = worksheet.getRow(row + 1); // ExcelJS rows are 1-based
    rowCells.eachCell((cell: any, colNumber: number) => {
      if (cell.value !== undefined && colNumber > maxColumnIndex) {
        maxColumnIndex = colNumber;
      }
    });
  }

  // Update the end column of the range with the maximum column index found
  range.e.c = maxColumnIndex;
}

function findAndChangeColumns(worksheet: any, columns: any) {
  const firstRow = worksheet.getRow(1);
  firstRow.eachCell((cell: any, colNumber: number) => {
    if (cell.value === "#status#") {
      columns.statusColumn = cell.address.replace(/\d+/g, "");
      cell.fill = {
        type: "pattern",
        pattern: "solid",
        fgColor: { argb: "CCCC00" },
      };
      worksheet.eachRow((row: any, rowIndex: number) => {
        if (rowIndex > 1) {
          const statusCell = row.getCell(colNumber);
          statusCell.value = undefined;
        }
      });
    }
    if (cell.value === "#errorDetails#") {
      columns.errorDetailsColumn = cell.address.replace(/\d+/g, "");
      cell.fill = {
        type: "pattern",
        pattern: "solid",
        fgColor: { argb: "CCCC00" },
      };
      worksheet.eachRow((row: any, rowIndex: number) => {
        if (rowIndex > 1) {
          const errorDetailsCell = row.getCell(colNumber);
          errorDetailsCell.value = undefined;
        }
      });
    }
  });
}

function makeColumns(worksheet: any, range: any, columns: any) {
  // If the status column doesn't exist, calculate the next available column
  if (!columns?.statusColumn) {
    const emptyColumnIndex = range.e.c;
    columns.statusColumn = String.fromCharCode(65 + (emptyColumnIndex + 1));
    const statusCell = worksheet.getCell(`${columns.statusColumn}1`);
    statusCell.value = "#status#";
    statusCell.fill = {
      type: "pattern",
      pattern: "solid",
      fgColor: { argb: "CCCC00" },
    };
    statusCell.font = { bold: true };
    worksheet.getColumn(columns.statusColumn).width = 40;
  }

  // Calculate errorDetails column one column to the right of status column
  if (!columns?.errorDetailsColumn) {
    columns.errorDetailsColumn = String.fromCharCode(
      columns?.statusColumn.charCodeAt(0) + 1
    );
    const errorDetailsCell = worksheet.getCell(
      `${columns.errorDetailsColumn}1`
    );
    errorDetailsCell.value = "#errorDetails#";
    errorDetailsCell.fill = {
      type: "pattern",
      pattern: "solid",
      fgColor: { argb: "CCCC00" },
    };
    errorDetailsCell.font = { bold: true };
    worksheet.getColumn(columns.errorDetailsColumn).width = 40;
  }
}

function findColumns(worksheet: any) {
  const range = {
    s: { r: 0, c: 0 },
    e: { r: worksheet.rowCount - 1, c: worksheet.columnCount - 1 },
  };

  // Check if the status column already exists in the first row
  var columns = {};

  findAndChangeColumns(worksheet, columns);

  makeColumns(worksheet, range, columns);

  updateRange(range, worksheet);

  return columns;
}

function enrichErrors(
  errorData: any,
  worksheet: any,
  statusColumn: any,
  errorDetailsColumn: any,
  additionalDetailsErrors: any,
  createAndSearchConfig: any,
  localizationMap?: { [key: string]: string }
) {
  if (errorData) {
    errorData.forEach((error: any) => {
      const rowIndex = error.rowNumber; // ExcelJS rows are 1-based
      const statusCell = worksheet.getCell(`${statusColumn}${rowIndex}`);
      const errorDetailsCell = worksheet.getCell(
        `${errorDetailsColumn}${rowIndex}`
      );
      statusCell.value = error.status;
      errorDetailsCell.value = error.errorDetails;

      if (
        error?.status &&
        !(error?.status === "CREATED" || error?.status === "VALID")
      ) {
        additionalDetailsErrors.push(error);
      }
    });
    if (errorData.some((error: any) => error?.status === "CREATED")) {
      const uniqueIdentifierFirstRowCell = `${createAndSearchConfig?.uniqueIdentifierColumn}1`;
      const columnName = getLocalizedName(
        createAndSearchConfig?.uniqueIdentifierColumnName,
        localizationMap
      );
      const uniqueIdentifierCell = worksheet.getCell(
        uniqueIdentifierFirstRowCell
      );
      uniqueIdentifierCell.value = columnName;

      uniqueIdentifierCell.fill = {
        type: "pattern",
        pattern: "solid",
        fgColor: { argb: "ff9248" },
      };
      uniqueIdentifierCell.font = { bold: true };
      worksheet.getColumn(
        createAndSearchConfig?.uniqueIdentifierColumn
      ).hidden = true;
    }
    errorData.forEach((error: any) => {
      const rowIndex = error.rowNumber;
      if (error.isUniqueIdentifier) {
        const uniqueIdentifierCell = worksheet.getCell(
          `${createAndSearchConfig.uniqueIdentifierColumn}${rowIndex}`
        );
        uniqueIdentifierCell.value = error.uniqueIdentifier;
        if (createAndSearchConfig?.activeColumn) {
          const activeCell = worksheet.getCell(
            `${createAndSearchConfig.activeColumn}${rowIndex}`
          );
          activeCell.value = usageColumnStatus.active;
        }
      }
    });
  }
}

function enrichActiveAndUUidColumn(
  worksheet: any,
  createAndSearchConfig: any,
  request: any
) {
  if (
    createAndSearchConfig?.activeColumn &&
    request?.body?.dataToCreate &&
    request?.body?.dataToCreate?.length > 0 &&
    request?.body?.ResourceDetails?.type == "user"
  ) {
    const dataToCreate = request.body.dataToCreate;
    for (const data of dataToCreate) {
      const rowNumber = data["!row#number!"];
      const activeCell = worksheet.getCell(
        `${createAndSearchConfig?.activeColumn}${rowNumber}`
      );
      const uniqueIdentifierCell = worksheet.getCell(
        `${createAndSearchConfig?.uniqueIdentifierColumn}${rowNumber}`
      );
      activeCell.value = usageColumnStatus.active;
      uniqueIdentifierCell.value = data["userServiceUuid"] || data?.user?.["userServiceUuid"];
    }
  }
}

function deterMineLastColumnAndEnrichUserDetails(
  worksheet: any,
  userNameAndPassword:
    | { rowNumber: number; userName: string; password: string }[]
    | undefined,
  request: any
): string {

  // Default columns (production user schema: UserName=G, Password=N)
  let usernameColumn = "G";
  let passwordColumn = "N";

  // Update columns if the request indicates a different source
  if (
    request?.body?.ResourceDetails?.additionalDetails?.source == "microplan"
  ) {
    // Microplan schema (no BOUNDARY_CODE_MANDATORY): UserName=F (col 6), Password=M (col 13)
    usernameColumn = "F";
    passwordColumn = "M";
  }

  if (userNameAndPassword) {
    const setCellHeader = (cell: string) => {
      worksheet.getCell(cell).value =
        cell === usernameColumn + "1" ? "UserName" : "Password";
      worksheet.getCell(cell).fill = {
        type: "pattern",
        pattern: "solid",
        fgColor: { argb: "ff9248" },
      };
      worksheet.getCell(cell).font = { bold: true };
      const columnLetter = cell.replace(/\d+$/, "");
      worksheet.getColumn(columnLetter).width = 40;
    };

    setCellHeader(usernameColumn + "1");
    setCellHeader(passwordColumn + "1");

    userNameAndPassword.forEach((data) => {
      const rowIndex = data.rowNumber;
      worksheet.getCell(`${usernameColumn}${rowIndex}`).value = data.userName;
      worksheet.getCell(`${passwordColumn}${rowIndex}`).value = data.password;
    });
  }

  return passwordColumn;
}

function adjustRef(worksheet: any, lastColumn: any) {
  const range = getSheetDataFromWorksheet(worksheet).filter(
    (row: any) => row
  ).length;
  worksheet.views = [
    { state: "frozen", ySplit: 1, topLeftCell: "A2", activeCell: "A2" },
  ];
  worksheet.autoFilter = {
    from: {
      row: 1,
      column: 1,
    },
    to: {
      row: range,
      column: worksheet.getColumn(lastColumn).number,
    },
  };
}

function processErrorData(
  request: any,
  createAndSearchConfig: any,
  workbook: any,
  sheetName: any,
  localizationMap?: { [key: string]: string }
) {
  const worksheet = workbook.getWorksheet(sheetName);
  var errorData = request.body.sheetErrorDetails;
  const userNameAndPassword = request.body.userNameAndPassword;
  const lastColumn = deterMineLastColumnAndEnrichUserDetails(
    worksheet,
    userNameAndPassword,
    request
  );
  const columns: any = findColumns(worksheet);
  const statusColumn = columns.statusColumn;
  const errorDetailsColumn = columns.errorDetailsColumn;
  const additionalDetailsErrors: any[] = [];
  errorData = mergeErrors(errorData);
  enrichErrors(
    errorData,
    worksheet,
    statusColumn,
    errorDetailsColumn,
    additionalDetailsErrors,
    createAndSearchConfig,
    localizationMap
  );
  enrichActiveAndUUidColumn(worksheet, createAndSearchConfig, request);

  request.body.additionalDetailsErrors = request?.body?.additionalDetailsErrors
    ? request?.body?.additionalDetailsErrors.concat(additionalDetailsErrors)
    : additionalDetailsErrors;

  adjustRef(worksheet, lastColumn);
  updateFontNameToRoboto(worksheet);

  workbook.xlsx.writeBuffer();
}

function mergeErrors(errorData: any) {
  const errorMap: any = {};

  errorData.forEach((item: any) => {
    const { rowNumber, sheetName, status, errorDetails, ...rest } = item;

    if (errorMap[rowNumber]) {
      errorMap[rowNumber].errorDetails += "; " + errorDetails;
    } else {
      errorMap[rowNumber] = {
        rowNumber,
        sheetName,
        status,
        errorDetails,
        ...rest,
      };
    }
  });

  return Object.values(errorMap);
}

function processErrorDataForEachSheets(
  request: any,
  createAndSearchConfig: any,
  workbook: any,
  sheetName: any
) {
  const desiredSheet = workbook.getWorksheet(sheetName);
  const columns: any = findColumns(desiredSheet);
  const statusColumn = columns.statusColumn;
  const errorDetailsColumn = columns.errorDetailsColumn;
  const userNameAndPassword = request?.body?.userNameAndPassword;

  var errorData = request.body.sheetErrorDetails.filter(
    (error: any) => error.sheetName === sheetName
  );
  const additionalDetailsErrors: any = [];
  errorData = mergeErrors(errorData);
  if (errorData) {
    errorData.forEach((error: any) => {
      const rowIndex = error.rowNumber;
      if (error.isUniqueIdentifier) {
        const uniqueIdentifierCell =
          createAndSearchConfig.uniqueIdentifierColumn + rowIndex;
        desiredSheet.getCell(uniqueIdentifierCell).value =
          error.uniqueIdentifier;
      }

      const statusCell = statusColumn + rowIndex;
      const errorDetailsCell = errorDetailsColumn + rowIndex;
      desiredSheet.getCell(statusCell).value = error.status;
      desiredSheet.getCell(errorDetailsCell).value = error.errorDetails;

      if (!(error.status === "CREATED" || error.status === "VALID")) {
        additionalDetailsErrors.push(error);
      }
    });
  }
  if (userNameAndPassword) {
    var newUserNameAndPassword: any = [];
    for (const data of userNameAndPassword) {
      const rowArray = data.rowNumber;
      for (let i = 0; i < rowArray.length; i++) {
        if (rowArray[i].sheetName == sheetName) {
          newUserNameAndPassword.push({ ...data, rowNumber: rowArray[i].row });
        }
      }
    }
  }
  deterMineLastColumnAndEnrichUserDetails(
    desiredSheet,
    newUserNameAndPassword,
    request
  );
  request.body.additionalDetailsErrors = request?.body?.additionalDetailsErrors
    ? request?.body?.additionalDetailsErrors.concat(additionalDetailsErrors)
    : additionalDetailsErrors;
  updateFontNameToRoboto(desiredSheet);
  workbook.worksheets[sheetName] = desiredSheet;
}

async function updateStatusFile(
  request: any,
  localizationMap?: { [key: string]: string }
) {
  const fileStoreId = request?.body?.ResourceDetails?.fileStoreId;
  const tenantId = request?.body?.ResourceDetails?.tenantId;
  const createAndSearchConfig =
    createAndSearch[request?.body?.ResourceDetails?.type];
  const fileResponse = await httpRequest(
    config.host.filestore + config.paths.filestore + "/url",
    {},
    { tenantId: tenantId, fileStoreIds: fileStoreId },
    "get"
  );
  const isLockSheetNeeded =
    request?.body?.ResourceDetails?.additionalDetails?.source == "microplan"
      ? true
      : false;

  if (!fileResponse?.fileStoreIds?.[0]?.url) {
    throwError("FILE", 500, "INVALID_FILE");
  }
  const fileUrl = fileResponse?.fileStoreIds?.[0]?.url;
  const sheetName = createAndSearchConfig?.parseArrayConfig?.sheetName;
  const localizedSheetName = getLocalizedName(sheetName, localizationMap);
  const workbook: any = await getExcelWorkbookFromFileURL(
    fileUrl,
    localizedSheetName
  );
  const worksheet: any = workbook.getWorksheet(localizedSheetName);
  if (request?.body?.ResourceDetails?.type == "user") {
    const columnsToUnhide = ["L", "N", "M", "O", "P"];
    unhideColumnsOfProcessedFile(worksheet, columnsToUnhide);
  }
  processErrorData(
    request,
    createAndSearchConfig,
    workbook,
    localizedSheetName,
    localizationMap
  );
  await hideMultiSelectMainColumns(worksheet, request, localizationMap);

  const columnWidths = Array(12).fill({ width: 30 });
  columnWidths.forEach((colWidth, index) => {
    if (worksheet.getColumn(index + 1)) {
      worksheet.getColumn(index + 1).width = colWidth.width;
    }
  });
  if (isLockSheetNeeded) lockSheet(request, workbook);
  const responseData = await createAndUploadFile(workbook, request);
  logger.info("File updated successfully:" + JSON.stringify(responseData));
  if (responseData?.[0]?.fileStoreId) {
    request.body.ResourceDetails.processedFileStoreId =
      responseData?.[0]?.fileStoreId;
  } else {
    throwError("FILE", 500, "STATUS_FILE_CREATION_ERROR");
  }
}

async function hideMultiSelectMainColumns(
  worksheet: any,
  request: any,
  localizationMap?: { [key: string]: string }) {
  const type = request?.body?.ResourceDetails?.type;
  const tenantId = request?.body?.ResourceDetails?.tenantId;
  const isUpdate = request?.body?.parentCampaignObject ? true : false;
  const isSourceMicroplan = checkIfSourceIsMicroplan(request?.body?.ResourceDetails);
  const schema = await getSchema(tenantId, isUpdate, type, isSourceMicroplan);
  const properties = schema?.properties;
  if (properties && Object.keys(properties)?.length > 0) {
    const headerRow = worksheet.getRow(1); // Assuming header is in the first row

    for (const key in properties) {
      if (properties[key]?.multiSelectDetails) {
        const columnName = getLocalizedName(key, localizationMap);

        const columnIndex = headerRow.values.indexOf(columnName);
        if (columnIndex > -1) {
          worksheet.getColumn(columnIndex).hidden = true;
        } else {
          console.warn(`Column with header "${columnName}" not found in worksheet.`);
        }
      }
    }
  }
}

/** Fetch the MDMS type schema for facility/user, picking the microplan variant when applicable. */
export async function getSchema(tenantId: string, isUpdate: boolean, type: string, isSourceMicroplan: boolean) {
  if (type === "facility" || type === "user") {
    return isSourceMicroplan
      ? await callMdmsTypeSchema(tenantId, isUpdate, type, "microplan")
      : await callMdmsTypeSchema(tenantId, isUpdate, type);
  }
  return null;
}

async function updateStatusFileForEachSheets(
  request: any,
  localizationMap?: { [key: string]: string }
) {
  const fileStoreId = request?.body?.ResourceDetails?.fileStoreId;
  const tenantId = request?.body?.ResourceDetails?.tenantId;
  const createAndSearchConfig =
    createAndSearch[request?.body?.ResourceDetails?.type];
  const fileResponse = await httpRequest(
    config.host.filestore + config.paths.filestore + "/url",
    {},
    { tenantId: tenantId, fileStoreIds: fileStoreId },
    "get"
  );
  const isLockSheetNeeded =
    request?.body?.ResourceDetails?.additionalDetails?.source == "microplan"
      ? true
      : false;

  if (!fileResponse?.fileStoreIds?.[0]?.url) {
    throwError("FILE", 500, "INVALID_FILE");
  }

  const fileUrl = fileResponse?.fileStoreIds?.[0]?.url;

  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, "");

  const sheetNames = workbook.worksheets.map(
    (worksheet: any) => worksheet.name
  );
  const localizedSheetNames = getLocalizedHeaders(sheetNames, localizationMap);

  const sheetErrorDetails = request?.body?.sheetErrorDetails;
  if (sheetErrorDetails && sheetErrorDetails?.length > 0) {
    const firstError = sheetErrorDetails[0];
    if (Array.isArray(firstError?.rowNumber)) {
      var newSheetErrorDetails: any = [];
      for (const error of sheetErrorDetails) {
        for (let i = 0; i < error.rowNumber.length; i++) {
          newSheetErrorDetails.push({
            ...error,
            rowNumber: error.rowNumber[i]?.row,
            sheetName: error.rowNumber[i]?.sheetName,
          });
        }
      }
      request.body.sheetErrorDetails = newSheetErrorDetails;
    }
  }

  localizedSheetNames.forEach((sheetName: any) => {
    if (sheetName.startsWith('_h_') && sheetName.endsWith('_h_')) return;
    if (
      sheetName !==
      getLocalizedName(config?.boundary?.boundaryTab, localizationMap) &&
      sheetName !==
      getLocalizedName(config.values.readMeTab, localizationMap) &&
      sheetName !==
      getLocalizedName("USER_MICROPLAN_SHEET_ROLES", localizationMap)
    ) {
      processErrorDataForEachSheets(
        request,
        createAndSearchConfig,
        workbook,
        sheetName
      );
    }
  });
  if (isLockSheetNeeded) lockSheet(request, workbook);
  const responseData = await createAndUploadFile(workbook, request);
  logger.info("File updated successfully:" + JSON.stringify(responseData));
  if (responseData?.[0]?.fileStoreId) {
    request.body.ResourceDetails.processedFileStoreId =
      responseData?.[0]?.fileStoreId;
  } else {
    throwError("FILE", 500, "STATUS_FILE_CREATION_ERROR");
  }
}

function convertToType(dataToSet: any, type: any) {
  switch (type) {
    case "string":
      return String(dataToSet);
    case "number":
      return Number(dataToSet);
    case "boolean":
      // Convert to boolean assuming any truthy value should be true and falsy should be false
      return Boolean(dataToSet);
    // Add more cases if needed for other types
    default:
      // If type is not recognized, keep dataToSet as it is
      return dataToSet;
  }
}

function setTenantId(
  resultantElement: any,
  requestBody: any,
  createAndSearchConfig: any
) {
  if (createAndSearchConfig?.parseArrayConfig?.tenantId) {
    const tenantId = _.get(
      requestBody,
      createAndSearchConfig?.parseArrayConfig?.tenantId?.getValueViaPath
    );
    _.set(
      resultantElement,
      createAndSearchConfig?.parseArrayConfig?.tenantId?.resultantPath,
      tenantId
    );
  }
}

async function processData(
  request: any,
  dataFromSheet: any[],
  createAndSearchConfig: any,
  localizationMap?: { [key: string]: string }
) {
  const parseLogic = createAndSearchConfig?.parseArrayConfig?.parseLogic;
  const latLongColumnsList = config.values.latLongColumns
    ? config.values.latLongColumns.split(",").map((item: any) => item.trim()).filter(Boolean)
    : [];
  const requiresToSearchFromSheet =
    createAndSearchConfig?.requiresToSearchFromSheet;
  const isSourceMicroplan =
    request?.body?.ResourceDetails?.additionalDetails?.source == "microplan";
  var createData = [],
    searchData = [];
  for (const data of dataFromSheet) {
    const resultantElement: any = {};
    for (const element of parseLogic) {
      if (element?.resultantPath) {
        const localizedSheetColumnName = getLocalizedName(
          element.sheetColumnName,
          localizationMap
        );
        let dataToSet = _.get(data, localizedSheetColumnName);
        if (element.conversionCondition) {
          dataToSet = element.conversionCondition[dataToSet];
        }
        if (element.type) {
          dataToSet = convertToType(dataToSet, element.type);
        }
        _.set(resultantElement, element.resultantPath, dataToSet);
      }
    }
    resultantElement["!row#number!"] = data["!row#number!"];
    var addToCreate = true;
    if (requiresToSearchFromSheet) {
      for (const key of requiresToSearchFromSheet) {
        const localizedSheetColumnName = getLocalizedName(
          key.sheetColumnName,
          localizationMap
        );
        if (data[localizedSheetColumnName]) {
          if (isSourceMicroplan) {
            changeCreateDataForMicroplan(
              request,
              resultantElement,
              data,
              latLongColumnsList,
              localizationMap
            );
          }
          searchData.push(resultantElement);
          addToCreate = false;
          break;
        }
      }
    }
    if (addToCreate) {
      if (isSourceMicroplan) {
        changeCreateDataForMicroplan(
          request,
          resultantElement,
          data,
          latLongColumnsList,
          localizationMap
        );
      }
      createData.push(resultantElement);
    }
  }
  return { searchData, createData };
}

function setTenantIdAndSegregate(
  processedData: any,
  createAndSearchConfig: any,
  requestBody: any
) {
  for (const resultantElement of processedData.createData) {
    setTenantId(resultantElement, requestBody, createAndSearchConfig);
  }
  for (const resultantElement of processedData.searchData) {
    setTenantId(resultantElement, requestBody, createAndSearchConfig);
  }
  return processedData;
}

async function convertToTypeData(
  request: any,
  dataFromSheet: any[],
  createAndSearchConfig: any,
  requestBody: any,
  localizationMap?: { [key: string]: string }
) {
  const processedData = await processData(
    request,
    dataFromSheet,
    createAndSearchConfig,
    localizationMap
  );
  return setTenantIdAndSegregate(
    processedData,
    createAndSearchConfig,
    requestBody
  );
}

function updateActivityResourceId(request: any) {
  if (request?.body?.Activities && Array.isArray(request?.body?.Activities)) {
    for (const activity of request?.body?.Activities) {
      activity.resourceDetailsId = request?.body?.ResourceDetails?.id;
    }
  }
}

async function generateProcessedFileAndPersist(
  request: any,
  localizationMap?: { [key: string]: string }
) {
  if (
    request.body.ResourceDetails.type == "boundaryWithTarget" ||
    (request?.body?.ResourceDetails?.additionalDetails?.source == "microplan" &&
      request.body.ResourceDetails.type == "user")
  ) {
    await updateStatusFileForEachSheets(request, localizationMap);
  } else {
    if (
      request.body.ResourceDetails.type !== "boundary"
    ) {
      await updateStatusFile(request, localizationMap);
    }
  }
  updateActivityResourceId(request);
  request.body.ResourceDetails = {
    ...request?.body?.ResourceDetails,
    status:
      request.body.ResourceDetails.status != resourceDataStatuses.invalid
        ? resourceDataStatuses.completed
        : resourceDataStatuses.invalid,
    auditDetails: {
      ...request?.body?.ResourceDetails?.auditDetails,
      lastModifiedBy: request?.body?.RequestInfo?.userInfo?.uuid,
      lastModifiedTime: Date.now(),
    },
    additionalDetails: {
      ...request?.body?.ResourceDetails?.additionalDetails,
      sheetErrors: request?.body?.additionalDetailsErrors,
      source:
        request?.body?.ResourceDetails?.additionalDetails?.source == "microplan"
          ? "microplan"
          : null,
    },
  };
  request.body.ResourceDetails.processedFileStoreId = request?.body?.ResourceDetails?.processedFileStoreId || null;
  const persistMessage: any = { ResourceDetails: request.body.ResourceDetails };
  if (request?.body?.ResourceDetails?.action == "create") {
    persistMessage.ResourceDetails.additionalDetails = {
      source:
        request?.body?.ResourceDetails?.additionalDetails?.source == "microplan"
          ? "microplan"
          : null,
      fileName:
        request?.body?.ResourceDetails?.additionalDetails?.fileName || null,
    };
  }
  await produceModifiedMessages(
    persistMessage,
    config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC,
    request?.body?.ResourceDetails?.tenantId
  );
  logger.info(
    `ResourceDetails to persist : ${request.body.ResourceDetails.type}`
  );
  if (
    request?.body?.Activities &&
    Array.isArray(request?.body?.Activities) &&
    request?.body?.Activities.length > 0
  ) {
    logger.info("Activities to persist : ");
    logger.debug(getFormattedStringForDebug(request?.body?.Activities));
    logger.info(`Waiting for 2 seconds`);
    await new Promise((resolve) => setTimeout(resolve, 2000));
    const activities = request?.body?.Activities;
    const activityBatchSize = config.resource.activityBatchSize;
    for (let i = 0; i < activities.length; i += activityBatchSize) {
      const chunk = activities.slice(i, Math.min(i + activityBatchSize, activities.length));
      const activityObject: any = { Activities: chunk };
      await produceModifiedMessages(
        activityObject,
        config.kafka.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC,
        activityObject?.Activities?.[0]?.tenantId
      );
    }
  }
}

function getRootBoundaryCode(boundaries: any[] = []) {
  if(boundaries?.length == 0) return "";
  for (const boundary of boundaries) {
    if (boundary.isRoot) {
      return boundary.code;
    }
  }
  return "";
}

async function enrichRootProjectIdAndBoundaryCode(campaignDetails: any) {
  campaignDetails.boundaryCode = campaignDetails?.boundaryCode || getRootBoundaryCode(campaignDetails?.boundaries) || null;
  campaignDetails.projectId = campaignDetails?.projectId || await getRootProjectIdViaCampaignNumber(campaignDetails?.campaignNumber, campaignDetails?.boundaryCode, campaignDetails?.tenantId);
  campaignDetails.projectId = campaignDetails.projectId || null;
}

async function enrichAndPersistCampaignWithError(requestBody: any, error: any) {
  requestBody.CampaignDetails = requestBody?.CampaignDetails || {};
  requestBody.CampaignDetails.campaignNumber =
    requestBody?.CampaignDetails?.campaignNumber || null;
  requestBody.CampaignDetails.campaignDetails = requestBody?.CampaignDetails
    ?.campaignDetails || {
    deliveryRules: requestBody?.CampaignDetails?.deliveryRules,
    resources: requestBody?.CampaignDetails?.resources || [],
    boundaries: requestBody?.CampaignDetails?.boundaries || [],
  };
  requestBody.CampaignDetails.status = campaignStatuses?.failed;
  requestBody.CampaignDetails.projectType =
    requestBody?.CampaignDetails?.projectType || null;
  requestBody.CampaignDetails.hierarchyType =
    requestBody?.CampaignDetails?.hierarchyType || null;
  requestBody.CampaignDetails.parentId = requestBody?.CampaignDetails?.parentId || null;
  requestBody.CampaignDetails.additionalDetails =
    requestBody?.CampaignDetails?.additionalDetails || {};
  requestBody.CampaignDetails.startDate =
    requestBody?.CampaignDetails?.startDate || null;
  requestBody.CampaignDetails.endDate =
    requestBody?.CampaignDetails?.endDate || null;
  requestBody.CampaignDetails.auditDetails = {
    createdBy: requestBody?.RequestInfo?.userInfo?.uuid,
    createdTime: Date.now(),
    lastModifiedBy: requestBody?.RequestInfo?.userInfo?.uuid,
    lastModifiedTime: Date.now(),
  };
  await enrichRootProjectIdAndBoundaryCode(requestBody?.CampaignDetails);
  requestBody.CampaignDetails.additionalDetails = {
    ...requestBody?.CampaignDetails?.additionalDetails,
    error: error?.code || "INTERNAL_SERVER_ERROR",
    errorMessage: error?.message && error?.description
      ? `${error.message} : ${error.description}`
      : error?.message || error?.description || "Internal server error",
  };
  const topic = config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC;
  // wait for 2 seconds
  logger.info(`Waiting for 2 seconds to persist errors`);
  await new Promise((resolve) => setTimeout(resolve, 2000));
  const produceMessage: any = { 
    RequestInfo: requestBody?.RequestInfo,
    CampaignDetails: requestBody.CampaignDetails 
  };
  await produceModifiedMessages(produceMessage, topic, requestBody?.CampaignDetails?.tenantId);
  delete requestBody.CampaignDetails.campaignDetails;
}

/** Persist a resource-task failure: record the error and reactivate the parent, without failing an already-inprogress campaign. */
export async function enrichAndPersistCampaignWithErrorProcessingTask(campaignDetails: any, parentCampaign: any, requestInfo: RequestInfo, error: any) {
  const RequestInfo = requestInfo || {};
  const useruuid: string = RequestInfo?.userInfo?.uuid as string;
  if (parentCampaign) {
    parentCampaign.isActive = true;
    parentCampaign.parentId = parentCampaign?.parentId || null;
    parentCampaign.campaignDetails = {
      deliveryRules: parentCampaign?.deliveryRules || [],
      boundaries: parentCampaign?.boundaries || [],
    };
    parentCampaign.auditDetails.lastModifiedTime = Date.now();
    parentCampaign.auditDetails.lastModifiedBy = useruuid;
    const produceMessage: any = {
      RequestInfo,
      CampaignDetails: parentCampaign,
    };
    await produceModifiedMessages(
      produceMessage,
      config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
      parentCampaign?.tenantId
    );
  }
  campaignDetails.parentId = campaignDetails?.parentId || null;
  // Only set status to failed if the campaign is still in progress (creating).
  // Preserve "created" (inprogress) status — resource task failure should not break the campaign.
  if (campaignDetails?.status === campaignStatuses?.started) {
    campaignDetails.status = campaignStatuses?.failed;
  }
  campaignDetails.campaignDetails = {
    deliveryRules: campaignDetails?.deliveryRules || [],
    boundaries: campaignDetails?.boundaries || [],
  };
  const currTime = Date.now();
  campaignDetails.auditDetails = {
    createdBy: useruuid,
    createdTime: currTime,
    lastModifiedBy: useruuid,
    lastModifiedTime: currTime,
  };
  campaignDetails.additionalDetails = {
    ...campaignDetails?.additionalDetails,
    error: error?.code || "INTERNAL_SERVER_ERROR",
    errorMessage: error?.message && error?.description
      ? `${error.message} : ${error.description}`
      : error?.message || error?.description || "Internal server error",
  };
  const topic = config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC;
  // wait for 2 seconds
  logger.info(`Waiting for 2 seconds to persist errors`);
  await new Promise((resolve) => setTimeout(resolve, 2000));
  const produceMessage: any = { 
    RequestInfo,
    CampaignDetails: campaignDetails 
  };
  await produceModifiedMessages(produceMessage, topic, campaignDetails?.tenantId);
}

async function enrichAndPersistCampaignForCreate(
  request: any,
  firstPersist: boolean = false
) {
  const action = request?.body?.CampaignDetails?.action;
  if (firstPersist) {
    if (!request?.body?.parentCampaign) {
      request.body.CampaignDetails.campaignNumber = await getCampaignNumber(
        request.body,
        "CMP-[cy:yyyy-MM-dd]-[SEQ_EG_CMP_ID]",
        "campaign.number",
        request?.body?.CampaignDetails?.tenantId
      );
    } else {
      request.body.CampaignDetails.campaignNumber =
        request.body.parentCampaign?.campaignNumber;
      request.body.CampaignDetails.campaignName =
        request.body.parentCampaign?.campaignName;
      request.body.CampaignDetails.isActive = true;
    }
    processClonedChecklist(request?.body?.CampaignDetails, request?.body?.RequestInfo)
    processAppConfig(request?.body?.CampaignDetails, request?.body?.RequestInfo);
  }
  request.body.CampaignDetails.campaignDetails = {
    deliveryRules: request?.body?.CampaignDetails?.deliveryRules || [],
    resources: request?.body?.CampaignDetails?.resources || [],
    boundaries: request?.body?.CampaignDetails?.boundaries || [],
  };
  request.body.CampaignDetails.status =
    action == "create" ? campaignStatuses.started : campaignStatuses.drafted;
  request.body.CampaignDetails.projectType =
    request?.body?.CampaignDetails?.projectType || null;
  request.body.CampaignDetails.hierarchyType =
    request?.body?.CampaignDetails?.hierarchyType || null;
  request.body.CampaignDetails.parentId =
    request?.body?.CampaignDetails?.parentId || null;
  const existingAdditionalForCreate = request?.body?.CampaignDetails?.additionalDetails || {};
  request.body.CampaignDetails.additionalDetails = {
    ...existingAdditionalForCreate,
    locale: existingAdditionalForCreate.locale || getLocaleFromRequestInfo(request?.body?.RequestInfo),
  };
  request.body.CampaignDetails.startDate =
    request?.body?.CampaignDetails?.startDate || null;
  request.body.CampaignDetails.endDate =
    request?.body?.CampaignDetails?.endDate || null;
  request.body.CampaignDetails.auditDetails = {
    createdBy: request?.body?.RequestInfo?.userInfo?.uuid,
    createdTime: Date.now(),
    lastModifiedBy: request?.body?.RequestInfo?.userInfo?.uuid,
    lastModifiedTime: Date.now(),
  };
  await enrichRootProjectIdAndBoundaryCode(request.body?.CampaignDetails);
  const topic = firstPersist
    ? config?.kafka?.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC
    : config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC;
  delete request.body.CampaignDetails.codesTargetMapping;
  const produceMessage: any = {
    RequestInfo: request?.body?.RequestInfo,
    CampaignDetails: request?.body?.CampaignDetails,
  };
  await produceModifiedMessages(produceMessage, topic, request?.body?.CampaignDetails?.tenantId);
  delete request.body.CampaignDetails.campaignDetails;
}

async function processAppConfig(campaignDetails: any, RequestInfo: any) {
  try {
    if (!campaignDetails?.parentId) {
      if (campaignDetails?.additionalDetails?.cloneFrom) {
        await createAppConfigFromClone(campaignDetails?.tenantId, campaignDetails?.campaignNumber, campaignDetails?.additionalDetails?.cloneFrom, RequestInfo, campaignDetails?.projectType, campaignDetails?.campaignName);
      }
      else {
        await createAppConfig(campaignDetails?.tenantId, campaignDetails?.campaignNumber, campaignDetails?.projectType, RequestInfo);
      }
    }
  } catch (error) {
    logger.error("Error while processing app config", error);
  }
}
async function processClonedChecklist(campaignDetails: any, RequestInfo: any) {
  try {
    if (!campaignDetails?.parentId) {
      if (campaignDetails?.additionalDetails?.cloneFrom) {
        // Clone the checklist from parent campaign
        const clonedChecklists = await fetchCloneChecklist(
          campaignDetails?.projectType,
          campaignDetails?.additionalDetails?.cloneFrom,
          campaignDetails?.tenantId
        );

        if (clonedChecklists.length) {
          // Creation of cloned checklist
          await createClonedChecklist(
            clonedChecklists,
            campaignDetails?.campaignName,
            campaignDetails?.tenantId
          );

          // Upsert localisation for cloned checklist
          const [locales, localisation] = await Promise.all([
            getLocalesFromStateInfo(campaignDetails?.tenantId),
            Localisation.getInstance(),
          ]);

          await upsertChecklistLocalization(
            campaignDetails?.campaignNumber,
            campaignDetails?.campaignName,
            campaignDetails?.additionalDetails?.cloneFrom,
            campaignDetails?.tenantId,
            locales,
            localisation,
            RequestInfo
          );
        }
      }
    }
  } catch (error) {
    logger.warn("Error while processing cloned checklist", error);
  }
}


/** Flow-2 create persist: mark campaign inprogress/started and deactivate the parent once a child goes inprogress. */
export async function enrichAndPersistCampaignForCreateViaFlow2(
  campaignDetails: any,
  RequestInfo: any,
  parentCampaign: any,
  useruuid: any
) {
  campaignDetails.campaignDetails = {
    deliveryRules: campaignDetails?.deliveryRules || [],
    boundaries: campaignDetails?.boundaries || [],
  };
  campaignDetails.parentId = campaignDetails?.parentId || null;
  campaignDetails.boundaryCode = campaignDetails?.boundaryCode || getRootBoundaryCode(campaignDetails?.boundaries) || null;
  campaignDetails.projectId = campaignDetails?.projectId || await getRootProjectIdViaCampaignNumber(campaignDetails?.campaignNumber, campaignDetails?.boundaryCode, campaignDetails?.tenantId);

  campaignDetails.status =
    campaignDetails.action == "create" ? campaignStatuses.inprogress : campaignStatuses.started;
  
  campaignDetails.auditDetails.lastModifiedTime = Date.now();
  campaignDetails.auditDetails.lastModifiedBy = useruuid;
  
  const topic = config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC;
  const produceMessage: any = {
    RequestInfo,
    CampaignDetails: campaignDetails,
  };
  await produceModifiedMessages(produceMessage, topic, campaignDetails?.tenantId);
  if(parentCampaign && campaignDetails?.status === campaignStatuses.inprogress) {
    await makeParentInactiveOrActive(parentCampaign, useruuid, false, RequestInfo);
  }
}

async function getRootProjectIdViaCampaignNumber(campaignNumber: string, boundaryCode: string, tenantId: string) {
  if (!campaignNumber || !boundaryCode) {
    return null;
  }
  const rootProjectData = await getRelatedDataWithCampaign("boundary", campaignNumber, tenantId, dataRowStatuses.completed, boundaryCode);
  return rootProjectData?.length ? rootProjectData[0]?.uniqueIdAfterProcess : null;
}

function enrichInnerCampaignDetails(
  requestBody: any,
  updatedInnerCampaignDetails: any
) {
  // resources are stored in eg_cm_resource_details — do NOT write to JSONB
  updatedInnerCampaignDetails.deliveryRules =
    requestBody?.CampaignDetails?.deliveryRules || [];
  updatedInnerCampaignDetails.boundaries =
    requestBody?.CampaignDetails?.boundaries || [];
}

async function enrichAndPersistCampaignForUpdate(
  request: any,
  firstPersist: boolean = false
) {
  const action = request?.body?.CampaignDetails?.action;
  const ExistingCampaignDetails = request?.body?.ExistingCampaignDetails;
  var updatedInnerCampaignDetails = {};
  enrichInnerCampaignDetails(request?.body, updatedInnerCampaignDetails);
  request.body.CampaignDetails.campaignNumber =
    ExistingCampaignDetails?.campaignNumber;
  request.body.CampaignDetails.campaignDetails = updatedInnerCampaignDetails;
  request.body.CampaignDetails.parentId = request?.body?.CampaignDetails?.parentId || null;
  request.body.CampaignDetails.status =
    action == "create"
      ? campaignStatuses.started
      : campaignStatuses.drafted;
  request.body.CampaignDetails.startDate =
    request?.body?.CampaignDetails?.startDate ||
    ExistingCampaignDetails?.startDate ||
    null;
  request.body.CampaignDetails.endDate =
    request?.body?.CampaignDetails?.endDate ||
    ExistingCampaignDetails?.endDate ||
    null;
  request.body.CampaignDetails.projectType = request?.body?.CampaignDetails
    ?.projectType
    ? request?.body?.CampaignDetails?.projectType
    : ExistingCampaignDetails?.projectType;
  request.body.CampaignDetails.hierarchyType = request?.body?.CampaignDetails
    ?.hierarchyType
    ? request?.body?.CampaignDetails?.hierarchyType
    : ExistingCampaignDetails?.hierarchyType;
  const existingAdditionalForUpdate = request?.body?.CampaignDetails?.additionalDetails
    ?? ExistingCampaignDetails?.additionalDetails
    ?? {};
  request.body.CampaignDetails.additionalDetails = {
    ...existingAdditionalForUpdate,
    locale: existingAdditionalForUpdate.locale || getLocaleFromRequestInfo(request?.body?.RequestInfo),
  };
  request.body.CampaignDetails.auditDetails = {
    createdBy: ExistingCampaignDetails?.createdBy,
    createdTime: ExistingCampaignDetails?.createdTime,
    lastModifiedBy: request?.body?.RequestInfo?.userInfo?.uuid,
    lastModifiedTime: Date.now(),
  };
  await enrichRootProjectIdAndBoundaryCode(request.body?.CampaignDetails);
  delete request.body.CampaignDetails.codesTargetMapping;
  const producerMessage: any = {
    RequestInfo: request?.body?.RequestInfo,
    CampaignDetails: request?.body?.CampaignDetails,
  };
  await produceModifiedMessages(
    producerMessage,
    config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
    request?.body?.CampaignDetails?.tenantId
  );
  delete request.body.CampaignDetails.campaignDetails;
}

async function makeParentInactiveOrActive(parentCampaign: any, userUuid: string, active: boolean, requestInfo?: RequestInfo) {
  parentCampaign.isActive = active;
  parentCampaign.parentId = parentCampaign?.parentId || null;
  parentCampaign.campaignDetails = {
    deliveryRules: parentCampaign?.deliveryRules || [],
    resources: parentCampaign?.resources || [],
    boundaries: parentCampaign?.boundaries || [],
  };
  parentCampaign.auditDetails.lastModifiedTime = Date.now();
  parentCampaign.auditDetails.lastModifiedBy = userUuid;
  const produceMessage: any = {
    RequestInfo: { ...(requestInfo || {}), userInfo: { ...((requestInfo as any)?.userInfo || {}), uuid: userUuid } },
    CampaignDetails: parentCampaign,
  };
  await produceModifiedMessages(
    produceMessage,
    config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
    parentCampaign?.tenantId
  );
}

function removeBoundariesFromRequest(request: any) {
  const boundaries = request?.body?.CampaignDetails?.boundaries;
  if (boundaries && Array.isArray(boundaries) && boundaries?.length > 0) {
    request.body.CampaignDetails.boundaries = boundaries?.filter(
      (boundary: any) => !boundary?.insertedAfter
    );
  }
}

async function enrichAndPersistProjectCampaignForFirst(
  request: any,
  actionInUrl: any,
  firstPersist: boolean = false,
  localizationMap?: any
) {
  removeBoundariesFromRequest(request);
  if (actionInUrl == "create") {
    await enrichAndPersistCampaignForCreate(request, firstPersist);
  } else if (actionInUrl == "update") {
    await enrichAndPersistCampaignForUpdate(request, firstPersist);
  }
}

async function getTotalCount(campaignDetails: any) {
  const { tenantId, ids, ...searchFields } = campaignDetails;
  let conditions = [];
  let values = [tenantId];
  let index = 2;
  const campaignsIncludeDates = searchFields?.campaignsIncludeDates;

  for (const field in searchFields) {
    const value = searchFields[field];
    if (
      value !== undefined &&
      field !== "campaignsIncludeDates" &&
      field !== "isLikeSearch" &&
      field !== "isOverrideDatesFromProject" &&
      field !== "pagination"
    ) {
      if (field === "startDate") {
        const startDateSign = campaignsIncludeDates ? "<=" : ">=";
        conditions.push(`startDate ${startDateSign} $${index}`);
        values.push(value);
        index++;
      } else if (field === "endDate") {
        const endDateSign = campaignsIncludeDates ? ">=" : "<=";
        conditions.push(`endDate ${endDateSign} $${index}`);
        values.push(value);
        index++;
      } else if (field === "campaignName") {
        if (searchFields?.isLikeSearch) {
          conditions.push(`campaignName ILIKE '%' || $${index} || '%'`);
        } else {
          conditions.push(`campaignName = $${index}`);
        }
        values.push(value);
        index++;
      } else if (field === "isChildCampaign") {
        if (value === true) {
          conditions.push(`parentId IS NOT NULL`);
        } else {
          conditions.push(`parentId IS NULL`);
        }
      } else if (field === "parentId") {
        conditions.push(`parentId = $${index}`);
        values.push(value);
        index++;
      } else if (field !== "status") {
        conditions.push(`${field} = $${index}`);
        values.push(value);
        index++;
      }
    }
  }

  const tableName = getTableName(config?.DB_CONFIG.DB_CAMPAIGN_DETAILS_TABLE_NAME, tenantId);

  let query = `
    SELECT count(*)
    FROM ${tableName}
    WHERE tenantId = $1
  `;

  if (ids && ids.length > 0) {
    const idParams = ids.map((_ : any, i : number) => `$${index + i}`);
    query += ` AND id IN (${idParams.join(", ")})`;
    values.push(...ids);
    index += ids.length;
  } else if (searchFields.isActive === undefined) {
    query += ` AND isActive = true`;
  }

  const status = searchFields?.status;
  if (status) {
    const statusArray = Array.isArray(status) ? status : [status];
    const statusParams = statusArray.map((_, i) => `$${index + i}`);
    query += ` AND status IN (${statusParams.join(", ")})`;
    values.push(...statusArray);
    index += statusArray.length;
  }

  if (conditions.length > 0) {
    query += ` AND ${conditions.join(" AND ")}`;
  }

  const queryResult = await executeQuery(query, values);
  const totalCount = parseInt(queryResult.rows[0].count, 10);
  return totalCount;
}

async function searchProjectCampaignResourcData(campaignDetails: any, request?: any) {
  const { tenantId, pagination, ids, ...searchFields } = campaignDetails;
  const queryData = buildSearchQuery(tenantId, pagination, ids, searchFields);
  const totalCount = await getTotalCount(campaignDetails);
  const responseData: any[] = await executeSearchQuery(
    queryData.query,
    queryData.values
  );

  const projectIds = Array.from(
    new Set(responseData.map(d => d?.projectId).filter(Boolean))
  );

  // Step 2: Bulk fetch all project details
  let projectsMap = new Map<string, { startDate: number, endDate: number }>();

  if (searchFields?.isOverrideDatesFromProject) {
    if (projectIds.length > 0) {
      try {
        const projects = await fetchProjectsWithProjectId(request, projectIds, tenantId, false); // returns array of projects

        // Step 3: Build map of referenceId -> { startDate, endDate }
        for (const project of projects) {
          if (project?.referenceID) {
            projectsMap.set(project.referenceID, {
              startDate: project.startDate,
              endDate: project.endDate,
            });
          }
        }
      } catch (err) {
        console.error("Bulk project fetch failed while searching campaign with config overrideDatesFromProject enabled", err);
      }
    }
  }
  // Batch-enrich resources from eg_cm_resource_details table
  const campaignIds = responseData.map((d: any) => d.id).filter(Boolean);
  const resourcesMap = new Map<string, any[]>();
  if (campaignIds.length > 0 && tenantId) {
    try {
      await Promise.all(campaignIds.map(async (cid: string) => {
        const rows = await searchResourceDetailsFromDB({ tenantId, campaignId: cid, isActive: true, excludeTypes: ['attendanceRegisterAttendee'] });
        if (rows.length > 0) {
          resourcesMap.set(cid, rows.map(r => toCampaignResource(toResourceDetailsResponse(r))));
        }
      }));
    } catch (err) {
      logger.error(`Failed to enrich resources from table, falling back to JSONB: ${err}`);
    }
  }

  // TODO @ashish check the below code looks like duplicate
  for (const data of responseData) {
    // Use enriched resources from table; fall back to JSONB if not available (backward compat)
    const rawResources: any[] = resourcesMap.has(data.id)
      ? resourcesMap.get(data.id)!
      : (data?.campaignDetails?.resources || []);
    data.resources = rawResources
      .filter((r: any) => r?.type !== 'attendanceRegisterAttendee')
      .map((r: any) => ({
        ...r,
        status: r?.status ?? 'completed',
        additionalDetails: r?.additionalDetails ?? {},
      }));
    data.boundaries = data?.campaignDetails?.boundaries;
    data.deliveryRules = data?.campaignDetails?.deliveryRules;
    delete data.campaignDetails;
    data.auditDetails = {
      createdBy: data?.createdBy,
      lastModifiedBy: data?.lastModifiedBy,
      createdTime: data?.createdTime,
      lastModifiedTime: data?.lastModifiedTime,
    };
    delete data.createdBy;
    delete data.lastModifiedBy;
    delete data.createdTime;
    delete data.lastModifiedTime;

    if (searchFields?.isOverrideDatesFromProject) {
      const projDates = projectsMap.get(data?.campaignNumber);
      if (projDates) {
        if (projDates.startDate !== undefined && data.startDate !== undefined && projDates.startDate !== data.startDate) {
          data.startDate = projDates.startDate;
        }
        if (projDates.endDate !== undefined && data.endDate !== undefined && projDates.endDate !== data.endDate) {
          data.endDate = projDates.endDate;
        }
      }
    }
  }
  return { responseData, totalCount };
}

function buildSearchQuery(
  tenantId: string,
  pagination: any,
  ids: string[],
  searchFields: any
): { query: string; values: any[] } {
  let conditions = [];
  let values = [tenantId];
  let index = 2;
  const campaignsIncludeDates = searchFields?.campaignsIncludeDates;

  for (const field in searchFields) {
    const value = searchFields[field];
    if (
      value !== undefined &&
      field !== "campaignsIncludeDates" &&
      field !== "isLikeSearch" &&
      field !== "isOverrideDatesFromProject" &&
      field !== "pagination"
    ) {
      if (field === "startDate") {
        const startDateSign = campaignsIncludeDates ? "<=" : ">=";
        conditions.push(`startDate ${startDateSign} $${index}`);
        values.push(value);
        index++;
      } else if (field === "endDate") {
        const endDateSign = campaignsIncludeDates ? ">=" : "<=";
        conditions.push(`endDate ${endDateSign} $${index}`);
        values.push(value);
        index++;
      } else if (field === "campaignName") {
        if (searchFields?.isLikeSearch) {
          conditions.push(`campaignName ILIKE '%' || $${index} || '%'`);
        } else {
          conditions.push(`campaignName = $${index}`);
        }
        values.push(value);
        index++;
      } else if (field === "isChildCampaign") {
        if (value === true) {
          conditions.push(`parentId IS NOT NULL`);
        } else {
          conditions.push(`parentId IS NULL`);
        }
      } else if (field === "parentId") {
        conditions.push(`parentId = $${index}`);
        values.push(value);
        index++;
      } else if (field !== "status") {
        conditions.push(`${field} = $${index}`);
        values.push(value);
        index++;
      }
    }
  }

  const tableName = getTableName(config?.DB_CONFIG.DB_CAMPAIGN_DETAILS_TABLE_NAME, tenantId);

  let query = `
    SELECT *
    FROM ${tableName}
    WHERE tenantId = $1
  `;

  if (ids && ids.length > 0) {
    const idParams = ids.map((_, i) => `$${index + i}`);
    query += ` AND id IN (${idParams.join(", ")})`;
    values.push(...ids);
    index += ids.length;
  } else if (searchFields.isActive === undefined) {
    query += ` AND isActive = true`;
  }

  const status = searchFields?.status;
  if (status) {
    const statusArray = Array.isArray(status) ? status : [status];
    const statusParams = statusArray.map((_, i) => `$${index + i}`);
    query += ` AND status IN (${statusParams.join(", ")})`;
    values.push(...statusArray);
    index += statusArray.length;
  }

  if (conditions.length > 0) {
    query += ` AND ${conditions.join(" AND ")}`;
  }

  if (pagination) {
    query += "\n";
    if (pagination.sortBy) {
      query += `ORDER BY ${pagination.sortBy}`;
      if (pagination.sortOrder) {
        query += ` ${pagination.sortOrder.toUpperCase()}`;
      }
      query += "\n";
    }
    if (pagination.limit !== undefined) {
      query += `LIMIT ${pagination.limit}`;
      if (pagination.offset !== undefined) {
        query += ` OFFSET ${pagination.offset}`;
      }
      query += "\n";
    }
  }

  return { query, values };
}


async function executeSearchQuery(query: string, values: any[]) {
  const queryResult = await executeQuery(query, values);
  return campaignDetailsTransformer(queryResult?.rows);
}

async function processDataSearchRequest(request: any) {
  const { SearchCriteria } = request.body;
  const query = buildWhereClauseForDataSearch(SearchCriteria);
  const queryResult = await executeQuery(query.query, query.values);
  request.body.ResourceDetails = genericResourceTransformer(queryResult?.rows);
}

function buildWhereClauseForDataSearch(SearchCriteria: any): {
  query: string;
  values: any[];
} {
  const { id, tenantId, type, status, hierarchyType } = SearchCriteria;
  let conditions = [];
  let values = [];

  if (id && id.length > 0) {
    conditions.push(`id = ANY($${values.length + 1})`);
    values.push(id);
  }

  if (tenantId) {
    conditions.push(`tenantId = $${values.length + 1}`);
    values.push(tenantId);
  }

  if (type) {
    conditions.push(`type = $${values.length + 1}`);
    values.push(type);
  }

  if (status) {
    conditions.push(`status = $${values.length + 1}`);
    values.push(status);
  }

  if (hierarchyType) {
    conditions.push(`hierarchyType = $${values.length + 1}`);
    values.push(hierarchyType);
  }

  const whereClause =
    conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";

  const tableName = getTableName(config?.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  return {
    query: `
            SELECT *
            FROM ${tableName}
            ${whereClause};`,
    values,
  };
}

function mapBoundariesParent(boundaryResponse: any, request: any, parent: any) {
  if (!boundaryResponse) return;

  request.body.boundaryProjectMapping[boundaryResponse.code] = {
    parent: parent || null,
    projectId: null,
  };
  if (
    boundaryResponse?.children &&
    Array.isArray(boundaryResponse?.children) &&
    boundaryResponse?.children?.length > 0
  ) {
    for (const child of boundaryResponse.children) {
      mapBoundariesParent(child, request, boundaryResponse.code);
    }
  }
}

function mapTargets(boundaryResponses: any, codesTargetMapping: any) {
  if (!boundaryResponses || !codesTargetMapping) return;

  // Roll leaf targets up to each ancestor by summing children.
  const mapBoundary = (boundary: any) => {
    if (!boundary.children || boundary.children.length === 0) {
      const targetValue = codesTargetMapping[boundary.code];
      return targetValue || {};
    }

    let totalTargetValue: any = {};

    for (const child of boundary.children) {
      const childTargetValue = mapBoundary(child);

      for (const key in childTargetValue) {
        if (childTargetValue.hasOwnProperty(key)) {
          totalTargetValue[key] = (totalTargetValue[key] || 0) + childTargetValue[key];
        }
      }
    }

    codesTargetMapping[boundary.code] = totalTargetValue;
    return totalTargetValue;
  };

  for (const boundaryResponse of boundaryResponses) {
    mapBoundary(boundaryResponse);
  }
}

/** Walk the boundary tree adding any missing nodes (with parent links) to `boundaries`, respecting includeAllChildren. */
export async function populateBoundariesRecursively(
  boundaryResponse: any,
  boundaries: any,
  includeAllChildren: any,
  boundaryCodes: any,
  boundaryChildren: any,
  parent: any = null
) {
  if (!boundaryResponse) return;

  if (!boundaryCodes.has(boundaryResponse.code)) {
    boundaries.push({
      code: boundaryResponse?.code,
      type: boundaryResponse?.boundaryType,
      insertedAfter: true,
      parent: parent
    });
    boundaryCodes.add(boundaryResponse?.code);
  }

  if (
    includeAllChildren &&
    boundaryResponse?.children &&
    Array.isArray(boundaryResponse?.children) &&
    boundaryResponse?.children?.length > 0
  ) {
    for (const child of boundaryResponse.children) {
      await populateBoundariesRecursively(
        child,
        boundaries,
        true,
        boundaryCodes,
        boundaryChildren,
        boundaryResponse.code
      );
    }
  } else if (
    boundaryResponse?.children &&
    Array.isArray(boundaryResponse?.children) &&
    boundaryResponse?.children?.length > 0
  ) {
    for (const child of boundaryResponse.children) {
      if (boundaryCodes.has(child.code) && boundaryChildren[child.code]) {
        await populateBoundariesRecursively(
          child,
          boundaries,
          true,
          boundaryCodes,
          boundaryChildren,
          boundaryResponse.code
        );
      } else if (boundaryCodes.has(child.code)) {
        await populateBoundariesRecursively(
          child,
          boundaries,
          false,
          boundaryCodes,
          boundaryChildren,
          boundaryResponse.code
        );
      }
    }
  }
}


async function processBoundary(
  boundaryResponse: any,
  boundaries: any,
  includeAllChildren: any,
  boundaryCodes: any,
  boundaryChildren: any
) {
  if (!boundaryResponse) return;
  if (!boundaryCodes.has(boundaryResponse.code)) {
    boundaries.push({
      code: boundaryResponse?.code,
      type: boundaryResponse?.boundaryType,
      insertedAfter: true
    });
    boundaryCodes.add(boundaryResponse?.code);
  }
  if (
    includeAllChildren &&
    boundaryResponse?.children &&
    Array.isArray(boundaryResponse?.children) &&
    boundaryResponse?.children?.length > 0
  ) {
    for (const child of boundaryResponse.children) {
      processBoundary(child, boundaries, true, boundaryCodes, boundaryChildren);
    }
  } else if (
    boundaryResponse?.children &&
    Array.isArray(boundaryResponse?.children) &&
    boundaryResponse?.children?.length > 0
  ) {
    for (const child of boundaryResponse.children) {
      if (boundaryCodes.has(child.code) && boundaryChildren[child.code]) {
        processBoundary(
          child,
          boundaries,
          true,
          boundaryCodes,
          boundaryChildren
        );
      } else if (boundaryCodes.has(child.code)) {
        processBoundary(
          child,
          boundaries,
          false,
          boundaryCodes,
          boundaryChildren
        );
      }
    }
  }
}

async function addBoundaries(
  request: any,
  boundaryResponse: any,
  boundaryChildren: any
) {
  var boundaries = request?.body?.boundariesCombined;
  var boundaryCodes = new Set(boundaries.map((boundary: any) => boundary.code));
  await processBoundary(
    boundaryResponse,
    boundaries,
    boundaryChildren[boundaryResponse?.code],
    boundaryCodes,
    boundaryChildren
  );
  request.body.boundariesCombined = boundaries;
}

async function addBoundariesForData(request: any, CampaignDetails: any) {
  var boundaries = await getBoundariesFromCampaignSearchResponse(
    request,
    CampaignDetails
  );
  const rootBoundary = getRootBoundaryCode(boundaries);
  if (rootBoundary) {
    const params = {
      tenantId: request?.body?.ResourceDetails?.tenantId,
      codes: rootBoundary,
      hierarchyType: request?.body?.ResourceDetails?.hierarchyType,
      includeChildren: true,
    };
    const header = {
      ...defaultheader,
      cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId
        }${params.codes || ""}${params?.includeChildren || ""}`,
    };
    const boundaryResponse = await httpRequest(
      config.host.boundaryHost + config.paths.boundaryRelationship,
      request.body,
      params,
      undefined,
      undefined,
      header
    );
    if (boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]) {
      var boundaryChildren = boundaries.reduce((acc: any, boundary: any) => {
        acc[boundary.code] = boundary?.includeAllChildren;
        return acc;
      }, {});
      var boundaryCodes = new Set(
        boundaries.map((boundary: any) => boundary.code)
      );
      await processBoundary(
        boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0],
        boundaries,
        boundaryChildren[
        boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]?.code
        ],
        boundaryCodes,
        boundaryChildren
      );
      CampaignDetails.boundaries = boundaries;
    } else {
      throwError(
        "COMMON",
        500,
        "INTERNAL_SERVER_ERROR",
        "Some internal server error occured during boundary validation."
      );
    }
  } else {
    throwError(
      "COMMON",
      500,
      "INTERNAL_SERVER_ERROR",
      "There is no root boundary for this campaign."
    );
  }
}

function reorderBoundariesWithParentFirst(
  boundaries: any[],
  boundaryProjectMapping: any
) {
  const startTime = Date.now();

  const boundaryGraph = new Map();
  const inDegree = new Map();

  logger.info(`Started processing ${boundaries.length} boundaries...`);

  boundaries.forEach((boundary) => {
    const code = boundary.code;
    boundaryGraph.set(code, []);
    inDegree.set(code, 0);
  });

  boundaries.forEach((boundary) => {
    const code = boundary.code;
    const parentCode = boundaryProjectMapping[code]?.parent;

    if (parentCode) {
      boundaryGraph.get(parentCode).push(code);
      inDegree.set(code, inDegree.get(code) + 1);
    }
  });

  const graphConstructionTime = Date.now();
  logger.info(
    `Graph construction completed. Time taken: ${(
      (graphConstructionTime - startTime) / 1000
    ).toFixed(2)} seconds.`
  );

  // Topological sort (Kahn's Algorithm) so parents always precede children.
  const queue: any = [];
  const sortedBoundaries = [];

  boundaries.forEach((boundary) => {
    if (inDegree.get(boundary.code) === 0) {
      queue.push(boundary);
    }
  });

  let nodesProcessed = 0;
  while (queue.length > 0) {
    const currentBoundary = queue.shift();
    sortedBoundaries.push(currentBoundary);
    nodesProcessed++;

    if (nodesProcessed % 500 === 0) {
      const elapsed = (Date.now() - startTime) / 1000;
      const avgTimePerBoundary = elapsed / nodesProcessed;
      const estimatedRemaining = avgTimePerBoundary * (boundaries.length - nodesProcessed);
      logger.info(
        `Processed ${nodesProcessed} boundaries. Elapsed: ${elapsed.toFixed(
          2
        )} seconds. Estimated time remaining: ${estimatedRemaining.toFixed(2)} seconds.`
      );
    }

    const children = boundaryGraph.get(currentBoundary.code) || [];
    children.forEach((childCode: any) => {
      inDegree.set(childCode, inDegree.get(childCode) - 1);
      if (inDegree.get(childCode) === 0) {
        queue.push(
          boundaries.find((boundary) => boundary.code === childCode)
        );
      }
    });
  }

  // Fewer sorted than input means a cycle left some nodes with non-zero in-degree.
  if (sortedBoundaries.length !== boundaries.length) {
    throw new Error(
      "Cycle detected in the boundary-parent relationships. Reordering failed."
    );
  }

  const endTime = Date.now();
  logger.info(
    `Reordering completed. Processed ${boundaries.length} boundaries in ${(
      (endTime - startTime) / 1000
    ).toFixed(2)} seconds.`
  );

  return sortedBoundaries;
}

async function reorderBoundariesOfDataAndValidate(
  request: any,
  localizationMap?: any
) {
  if (request?.body?.ResourceDetails?.campaignId) {
    const CampaignDetails = {
      ids: [request?.body?.ResourceDetails?.campaignId],
      tenantId: request?.body?.ResourceDetails?.tenantId,
    }
    const response = await searchProjectTypeCampaignService(CampaignDetails);
    if (response?.CampaignDetails?.[0]) {
      const CampaignDetails = response?.CampaignDetails?.[0];
      await addBoundariesForData(request, CampaignDetails);
      logger.debug(
        "Boundaries after addition " +
        getFormattedStringForDebug(CampaignDetails?.boundaries)
      );
      await validateBoundaryOfResouces(
        CampaignDetails,
        request,
        localizationMap
      );
    } else {
      throwError(
        "CAMPAIGN",
        400,
        "CAMPAIGN_NOT_FOUND",
        "Campaign not found while Validating sheet boundaries"
      );
    }
  }
}

async function reorderBoundaries(request: any, localizationMap?: any) {
  var boundaries = request?.body?.boundariesCombined;
  const rootBoundary = getRootBoundaryCode(boundaries);
  request.body.boundaryProjectMapping = {};
  if (rootBoundary) {
    const params = {
      tenantId: request?.body?.CampaignDetails?.tenantId,
      codes: rootBoundary,
      hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
      includeChildren: true,
    };
    const header = {
      ...defaultheader,
      cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId
        }${params.codes || ""}${params?.includeChildren || ""}`,
    };
    const boundaryResponse = await httpRequest(
      config.host.boundaryHost + config.paths.boundaryRelationship,
      request.body,
      params,
      undefined,
      undefined,
      header
    );
    if (boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]) {
      const codesTargetMapping = await getCodesTarget(request, localizationMap);
      if (codesTargetMapping) {
        mapTargets(
          boundaryResponse?.TenantBoundary?.[0]?.boundary,
          codesTargetMapping
        );
        request.body.CampaignDetails.codesTargetMapping = codesTargetMapping;
        logger.debug(
          "codesTargetMapping mapping :: " +
          getFormattedStringForDebug(codesTargetMapping)
        );
      }
      mapBoundariesParent(
        boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0],
        request,
        null
      );
      var boundaryChildren = boundaries.reduce((acc: any, boundary: any) => {
        acc[boundary.code] = boundary?.includeAllChildren;
        return acc;
      }, {});
      await addBoundaries(
        request,
        boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0],
        boundaryChildren
      );
    } else {
      throwError(
        "COMMON",
        500,
        "INTERNAL_SERVER_ERROR",
        "Some internal server error occured during boundary validation."
      );
    }
  } else {
    throwError(
      "COMMON",
      500,
      "INTERNAL_SERVER_ERROR",
      "There is no root boundary for this campaign."
    );
  }
  logger.info("Boundaries for campaign creation in received");
  logger.debug(
    "Boundaries after addition " +
    getFormattedStringForDebug(request?.body?.boundariesCombined)
  );
  const start = Date.now();
  const sortedBoundaries = reorderBoundariesWithParentFirst(
    request?.body?.boundariesCombined,
    request?.body?.boundaryProjectMapping
  );
  request.body.boundariesCombined = sortedBoundaries;
  const end = Date.now();
  logger.info(`Execution time: ${(end - start) / 1000} seconds`);
  logger.info("Reordered the Boundaries for mapping");
  logger.debug(
    "Reordered Boundaries " +
    getFormattedStringForDebug(request?.body?.boundariesCombined)
  );
  return request.body.boundariesCombined;
}

async function getCodesTarget(request: any, localizationMap?: any) {
  let boundaryCodesWhoseTargetsHasToBeUpdated: any = [];
  const { tenantId, resources } = request?.body?.CampaignDetails;
  const boundaryWithTargetResource = resources?.filter(
    (resource: CampaignResource) => resource?.type == "boundaryWithTarget"
  );
  if (boundaryWithTargetResource && boundaryWithTargetResource.length > 0) {
    const fileId = boundaryWithTargetResource[0]?.filestoreId;
    const fileResponse = await httpRequest(
      config.host.filestore + config.paths.filestore + "/url",
      {},
      { tenantId: tenantId, fileStoreIds: fileId },
      "get"
    );
    if (!fileResponse?.fileStoreIds?.[0]?.url) {
      throwError("FILE", 500, "DOWNLOAD_URL_NOT_FOUND");
    }
    const codeColumnName = getLocalizedName(
      createAndSearch?.boundaryWithTarget?.boundaryValidation?.column,
      localizationMap
    );
    const targetData = await getTargetSheetDataAfterCode(
      request,
      fileResponse?.fileStoreIds?.[0]?.url,
      true,
      true,
      codeColumnName,
      localizationMap
    );
    const boundaryTargetMapping: any = {};
    for (const key in targetData) {
      targetData[key].forEach((entry: any) => {
        if (
          entry[codeColumnName] !== undefined &&
          entry["Target at the Selected Boundary level"] !== undefined
        ) {
          boundaryTargetMapping[entry[codeColumnName]] =
            entry["Target at the Selected Boundary level"];
          if (
            Object.keys(entry["Parent Target at the Selected Boundary level"]).length > 0 &&
            !_.isEqual(entry["Parent Target at the Selected Boundary level"], entry["Target at the Selected Boundary level"])
          ) {
            boundaryCodesWhoseTargetsHasToBeUpdated.push(entry[codeColumnName]);
          }
        }
      });
    }
    logger.info(
      "Boundary target mapping count" +
      Object.keys(boundaryTargetMapping)?.length
    );
    request.body.boundaryCodesWhoseTargetsHasToBeUpdated =
      boundaryCodesWhoseTargetsHasToBeUpdated;
    return boundaryTargetMapping;
  } else return null;
}

/**
 * Check if campaign is a unified template campaign
 * Unified campaigns have resources with type "unified-console-resources"
 */
function isUnfiedTemplateCamapign(campaignDetails: any): boolean {
  if (!campaignDetails?.resources || !Array.isArray(campaignDetails.resources)) {
    throw new Error('Campaign resources not found or invalid');
  }

  return campaignDetails.resources.some((resource: any) =>
    resource?.type === "unified-console-resources"
  );
}

/**
 * Process unified template campaign by calling excel-ingestion process API
 */
async function processUnifiedTemplateCampaign(request: any): Promise<void> {
  const campaignDetails = request?.body?.CampaignDetails;
  const useruuid = request?.body?.RequestInfo?.userInfo?.uuid || campaignDetails?.auditDetails?.createdBy;
  const emailId = request?.body?.RequestInfo?.userInfo?.emailId || null;

  const unifiedResource = campaignDetails.resources.find((resource: CampaignResource) =>
    resource?.type === "unified-console-resources"
  );

  if (!unifiedResource?.filestoreId) {
    throw new Error('FileStoreId not found for unified-console-resources');
  }

  logger.info(`Calling excel-ingestion process API for unified campaign: ${campaignDetails.campaignNumber}`);

  const processRequestBody = {
    RequestInfo: {
      apiId: "project-factory",
      ver: "1.0",
      ts: Date.now(),
      action: "process",
      msgId: `pf-${Date.now()}`,
      correlationId: `pf-correlation-${Date.now()}`,
      userInfo: { uuid: useruuid ,emailId: emailId}
    },
    ResourceDetails: {
      type: "unified-console-parse",
      tenantId: campaignDetails.tenantId,
      locale: getLocaleFromRequest(request),
      referenceId: campaignDetails.id,
      referenceType: 'campaign',
      fileStoreId: unifiedResource.filestoreId,
      campaignId: campaignDetails.id,
      hierarchyType: campaignDetails.hierarchyType,
      additionalDetails: {
        projectType: campaignDetails.projectType
      }
    }
  };
  
  const processUrl = `${config.host.excelIngestionHost}${config.paths.excelIngestionProcess}`;
  
  try {
    await httpRequest(
      processUrl,
      processRequestBody,
      {},
      'post',
      '',
      {
        'Content-Type': 'application/json'
      }
    );
    logger.info(`Excel-ingestion process API called successfully for campaign: ${campaignDetails.campaignNumber}`);
  } catch (error) {
    logger.error(`Error calling excel-ingestion process API for campaign: ${campaignDetails.campaignNumber}`, error);
    throw error;
  }
}

/**
 * Process regular campaign with background resource creation flow
 */
async function processRegularCampaign(request: any): Promise<void> {
  const locale = getLocaleFromRequest(request);
  const campaignDetails = request?.body?.CampaignDetails;
  const campaignNumber = campaignDetails?.campaignNumber;
  const tenantId = campaignDetails?.tenantId;
  const useruuid =
    request?.body?.RequestInfo?.userInfo?.uuid ||
    campaignDetails?.auditDetails?.createdBy;
  const requestInfo = request?.body?.RequestInfo;

  await prepareProcessesInDb(campaignNumber, tenantId, useruuid);

  const resources = campaignDetails?.resources || [];
  for (const resource of resources) {
    if (resource?.type) {
      updateResourceStatus(campaignDetails, resource.type, resourceStatuses.creating, tenantId, useruuid);
    }
  }

  // ✅ Offload the long chain into background (non-blocking)
  setImmediate(async () => {
    try {
      await createAllResources(campaignDetails, request?.body?.parentCampaign || null, useruuid, requestInfo);
      await createAllMappings(campaignDetails, request?.body?.parentCampaign || null, useruuid, requestInfo);
      await userCredGeneration(campaignDetails, useruuid, locale);
      await enrichAndPersistCampaignForCreateViaFlow2(campaignDetails, request?.body?.RequestInfo, request?.body?.parentCampaign || null, useruuid);
      triggerUserCredentialEmailFlow(request?.body); // not awaited = background
    } catch (e) {
      console.log(e);
      logger.error("Async Background Flow Error:", e);
      await enrichAndPersistCampaignWithError(request?.body, e);
    }
  });

  // ✅ Immediately return so main thread isn't blocked
  logger.info(`Started async background flow for campaign: ${campaignNumber}`);
}

/**
 * Wait for the child campaign row to appear in the DB (persisted via Kafka persister).
 * This must complete before copying resources so the FK from resource_details → campaign is valid.
 */
async function waitForCampaignToBePersisted(
  campaignId: string,
  tenantId: string,
  maxAttempts: number = 20,
  waitMs: number = 3000
): Promise<void> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    await new Promise(resolve => setTimeout(resolve, waitMs));
    const resp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
    if (resp?.CampaignDetails?.[0]?.id) {
      logger.info(`Campaign ${campaignId} found in DB after ${attempt + 1} attempt(s).`);
      return;
    }
    logger.info(`Attempt ${attempt + 1}/${maxAttempts}: Campaign ${campaignId} not yet in DB. Waiting ${waitMs / 1000}s...`);
  }
  logger.warn(`Campaign ${campaignId} not found in DB after ${maxAttempts} attempts. Proceeding anyway.`);
}

/**
 * Copy ALL completed resources from parent campaign to child campaign directly in DB.
 *
 * Steps:
 * 1. Deactivate existing toCreate resources on child (idempotency for update+create re-entry).
 * 2. Bulk INSERT SELECT from parent's completed resources — single SQL, handles 10k-20k+ rows.
 * 3. Override filestoreId in DB for types provided in CampaignDetails.resources.
 * 4. Read back unified-console-resources filestoreId from DB, update in-memory campaignDetails.resources.
 *
 * Returns true if unified-console-resources was among the copied rows.
 */
async function copyResourcesFromParentToChildInDB(
  campaignDetails: any,
  userUuid: string
): Promise<boolean> {
  const tenantId = campaignDetails?.tenantId;
  const childCampaignId = campaignDetails?.id;
  const parentCampaignId = campaignDetails?.parentId;
  const tableName = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  const now = Date.now();

  // Bulk INSERT SELECT — single SQL for all resource types, handles 10k-20k+ rows efficiently.
  // status, filestoreid, and processedfilestoreid are all copied directly from the parent row.
  await executeQuery(
    `INSERT INTO ${tableName} (id, tenantid, campaignid, type, parentresourceid, filestoreid, processedfilestoreid, filename, status, action, isactive, hierarchytype, additionaldetails, createdby, lastmodifiedby, createdtime, lastmodifiedtime)
     SELECT gen_random_uuid()::text, $1, $2, type, parentresourceid, filestoreid, processedfilestoreid, filename, status, 'create', true, null, additionaldetails, $3, $3, $4, $4
     FROM ${tableName}
     WHERE campaignid = $5 AND tenantid = $7 AND isactive = true AND status = $6`,
    [tenantId, childCampaignId, userUuid, now, parentCampaignId, resourceStatuses.completed, tenantId]
  );

  logger.info(`Bulk copied completed resources from parent campaign ${parentCampaignId} to child campaign ${childCampaignId} in DB.`);

  // Override filestoreId in DB for types explicitly provided in CampaignDetails.resources
  const childResources: any[] = campaignDetails?.resources || [];
  for (const resource of childResources) {
    if (!resource?.type || !resource?.filestoreId) continue;
    await executeQuery(
      `UPDATE ${tableName} SET filestoreid = $1, lastmodifiedby = $2, lastmodifiedtime = $3 WHERE campaignid = $4 AND tenantid = $5 AND type = $6 AND status = $7 AND isactive = true`,
      [resource.filestoreId, userUuid, now, childCampaignId, tenantId, resource.type, resourceStatuses.completed]
    );
    logger.info(`Overrode filestoreId for type=${resource.type} on child campaign ${childCampaignId}.`);
  }

  // Read back unified-console-resources and sync filestoreId into in-memory resources
  // so processUnifiedTemplateCampaign can read it from campaignDetails.resources
  const unifiedRows = await searchResourceDetailsFromDB({
    tenantId,
    campaignId: childCampaignId,
    type: [resourceTypes.unifiedConsoleResources],
    status: [resourceStatuses.completed],
    isActive: true,
  });

  if (unifiedRows && unifiedRows.length > 0) {
    const unifiedRow = unifiedRows[0];
    const existingUnified = childResources.find((r: any) => r?.type === resourceTypes.unifiedConsoleResources);
    if (existingUnified) {
      existingUnified.filestoreId = unifiedRow.filestoreid;
    } else {
      campaignDetails.resources = [
        ...childResources,
        {
          type: resourceTypes.unifiedConsoleResources,
          filestoreId: unifiedRow.filestoreid,
          filename: unifiedRow.filename || null,
          additionalDetails: unifiedRow.additionaldetails || {},
        },
      ];
    }
    logger.info(`Unified-console-resources ready in DB for child campaign ${childCampaignId}, filestoreId=${unifiedRow.filestoreid}`);
    return true;
  }

  return false;
}

/**
 * Fetches the unified-console-resources filestoreId from the cloneFrom campaign and injects it
 * into campaignDetails.resources so the clone can be processed as a unified template campaign.
 * Returns true if a filestoreId was successfully borrowed, false otherwise.
 */
async function borrowUnifiedSheetFromCloneCampaign(campaignDetails: any): Promise<boolean> {
  const cloneFromNumber = campaignDetails?.additionalDetails?.cloneFrom;
  if (!cloneFromNumber) return false;

  const resp = await searchProjectTypeCampaignService({ tenantId: campaignDetails.tenantId, campaignNumber: cloneFromNumber });
  const cloneFromCampaign = resp?.CampaignDetails?.[0];
  const cloneFromUnified = cloneFromCampaign?.resources?.find((r: any) => r?.type === resourceTypes.unifiedConsoleResources);

  if (!cloneFromUnified?.filestoreId) {
    logger.warn(`Clone source campaign ${cloneFromNumber} has no unified-console-resources; cannot borrow sheet`);
    return false;
  }

  if (!Array.isArray(campaignDetails.resources)) {
    campaignDetails.resources = [];
  }
  const existing = campaignDetails.resources.find((r: any) => r?.type === resourceTypes.unifiedConsoleResources);
  if (existing) {
    existing.filestoreId = cloneFromUnified.filestoreId;
  } else {
    campaignDetails.resources.push({
      type: resourceTypes.unifiedConsoleResources,
      filestoreId: cloneFromUnified.filestoreId,
      filename: cloneFromUnified.filename || null,
      additionalDetails: {},
    });
  }

  logger.info(`Borrowed unified sheet from clone source ${cloneFromNumber}: filestoreId=${cloneFromUnified.filestoreId}`);
  return true;
}

/**
 * Post-persist entry point: routes a created campaign to the unified-template or regular
 * resource-creation flow, copying parent resources first for child campaigns.
 */
export async function processAfterPersistNew(request: any, actionInUrl: any) {
  try {
    if (request?.body?.CampaignDetails?.action == "create") {
      const campaignDetails = request?.body?.CampaignDetails;
      const userUuid = request?.body?.RequestInfo?.userInfo?.uuid || campaignDetails?.auditDetails?.createdBy;

      if (campaignDetails?.parentId) {
        // Child campaign: wait for its row to persist, then copy parent's completed resources
        // in DB (INSERT SELECT) — only on initial create, not on subsequent updates.
        if (actionInUrl === "create") {
          await waitForCampaignToBePersisted(campaignDetails.id, campaignDetails.tenantId);
          await copyResourcesFromParentToChildInDB(campaignDetails, userUuid);
        }
        const isUnified = Array.isArray(campaignDetails?.resources) &&
          campaignDetails.resources.some((r: any) => r?.type === resourceTypes.unifiedConsoleResources);
        if (isUnified) {
          await processUnifiedTemplateCampaign(request);
        } else {
          await processRegularCampaign(request);
        }
      } else if (isUnfiedTemplateCamapign(campaignDetails)) {
        await processUnifiedTemplateCampaign(request);
      } else if (campaignDetails?.additionalDetails?.cloneFrom) {
        const borrowed = await borrowUnifiedSheetFromCloneCampaign(campaignDetails);
        if (borrowed) {
          await processUnifiedTemplateCampaign(request);
        } else {
          await processRegularCampaign(request);
        }
      } else {
        await processRegularCampaign(request);
      }
    }
    delete request.body?.boundariesCombined;
  } catch (error: any) {
    console.log(error);
    logger.error(error);
    await enrichAndPersistCampaignWithError(request?.body, error);
  }
}


async function userCredGeneration(campaignDetails: any, useruuid: string, locale: string = config.localisation.defaultLocale) {
  logger.info(`Starting user cred generation...`);
  let userCredGenerationProcess = allProcesses.userCredGeneration;
  let allCurrentProcesses = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId);
  let task = allCurrentProcesses.find((process: any) => process?.processName == userCredGenerationProcess);
  if (task && task?.status == processStatuses.pending || task?.status == processStatuses.failed) {
    const generateTemplateQuery: GenerateTemplateQuery = {
      type: "userCredential",
      campaignId: campaignDetails?.id,
      tenantId: campaignDetails?.tenantId,
      hierarchyType: campaignDetails?.hierarchyType
    }
    const response = await generateDataService(generateTemplateQuery, useruuid, locale);
    if (response && response?.id) {
      logger.info(`Waiting for 10 seconds for user cred to template to persist...`);
      await new Promise(resolve => setTimeout(resolve, 10000));
      let status = response?.status;
      let attempts = 0
      while (
        status === generatedResourceStatuses.inprogress &&
        attempts < Math.max(
          (config?.resourceCreationConfig?.maxAttemptsForResourceCreationOrMapping ?? 0) / 5,
          20
        )
      )
      {
        const generatedResources: any = await searchAllGeneratedResources({ id: response?.id, tenantId: campaignDetails?.tenantId }, undefined);
        if (generatedResources?.length > 0) {
          status = generatedResources[0]?.status;
          if (status == generatedResourceStatuses.completed) {
            break;
          }
        }
        else {
          throwError("COMMON", 400, "USER_CREDENTIAL_GENERATION_ERROR", "User credential generation failed");
        }
        logger.info(`Attempts : ${attempts + 1} | Status : ${status} | Waiting for 20 seconds for user cred generation to get completed...`);
        await new Promise(resolve => setTimeout(resolve, 20000));
        const campaignResp = await searchProjectTypeCampaignService({ tenantId: campaignDetails?.tenantId, ids: [campaignDetails?.id] });
        const campaignDetailsStatus = campaignResp?.CampaignDetails?.[0]?.status;
        if (campaignDetailsStatus == campaignStatuses.failed || !campaignDetailsStatus) {
          throwError("COMMON", 400, "USER_CREDENTIAL_GENERATION_ERROR", "Campaign creation failed during user credential generation");
        }
        attempts++;
      }
      if (status == generatedResourceStatuses.completed) {
        logger.info(`User credential generation completed successfully.`);
        const currentTime = Date.now();
        task.status = processStatuses.completed;
        task.auditDetails = {
          createdBy: task.auditDetails?.createdBy || useruuid,
          createdTime: task.auditDetails?.createdTime || currentTime,
          lastModifiedBy: useruuid,
          lastModifiedTime: currentTime
        };
        await produceModifiedMessages({ processes: [task] }, config?.kafka?.KAFKA_UPDATE_PROCESS_DATA_TOPIC, campaignDetails?.tenantId);
      }
      if (status != generatedResourceStatuses.completed) {
        throwError("COMMON", 400, "USER_CREDENTIAL_GENERATION_ERROR", "User credential generation failed");
      }
    }
    else {
      throwError("COMMON", 400, "USER_CREDENTIAL_GENERATION_ERROR", "User credential generation failed");
    }
  }
}

async function createAllResources(campaignDetails: any, parentCampaign: any, useruuid: string, requestInfo?: RequestInfo) {
  const { maxAttemptsForResourceCreationOrMapping, waitTimeOfEachAttemptOfResourceCreationOrMappping } = config?.resourceCreationConfig;

  // Phase 1: Registry-driven resource creation
  const phase1Configs = getResourceConfigsByPhase(1);
  let allCurrentProcesses = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId);

  for (const cfg of phase1Configs) {
    const task = allCurrentProcesses.find((process: any) => process?.processName == cfg.processName);
    if (task && task?.status == processStatuses.pending) {
      await produceModifiedMessages({
        task,
        CampaignDetails: campaignDetails,
        parentCampaign,
        useruuid,
        requestInfo
      }, config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC, campaignDetails?.tenantId, cfg.kafkaKey);
    }
  }

  let allTaskCompleted = false;
  let anyTaskFailed = false;
  let attempts = 0;
  const taskStatusMap: Record<string, any> = {};
  const startTime = Date.now();

  while (allTaskCompleted == false && anyTaskFailed == false && attempts < maxAttemptsForResourceCreationOrMapping) {
    logger.info(`Attempt ${attempts + 1}/${maxAttemptsForResourceCreationOrMapping}`);
    logger.info(`Waiting ${waitTimeOfEachAttemptOfResourceCreationOrMappping / 1000}s before polling resource statuses...`);
    await new Promise(resolve => setTimeout(resolve, waitTimeOfEachAttemptOfResourceCreationOrMappping));

    for (const cfg of phase1Configs) {
      const taskArray = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId, cfg.processName);
      taskStatusMap[cfg.processName] = taskArray[0];
    }

    allTaskCompleted = phase1Configs.every(c => taskStatusMap[c.processName]?.status == processStatuses.completed);
    anyTaskFailed = phase1Configs.some(c => taskStatusMap[c.processName]?.status == processStatuses.failed);

    const campaignResp = await searchProjectTypeCampaignService({ tenantId: campaignDetails?.tenantId, ids: [campaignDetails?.id] });
    const campaignDetailsStatus = campaignResp?.CampaignDetails?.[0]?.status;
    if (campaignDetailsStatus == campaignStatuses.failed || campaignDetailsStatus == campaignStatuses.cancelled || !campaignDetailsStatus) {
      throwError("COMMON", 400, "RESOURCE_CREATION_ERROR", "Campaign creation failed during resources creation.");
    }
    attempts++;
  }

  const totalTimeTakenInMs = Date.now() - startTime;
  const totalTimeInSeconds = (totalTimeTakenInMs / 1000).toFixed(2);
  const totalTimeInMinutes = (totalTimeTakenInMs / (1000 * 60)).toFixed(2);

  logger.info(`⏱️ Total time taken for resource creation: ${totalTimeInSeconds}s (~${totalTimeInMinutes} minutes)`);

  for (const cfg of phase1Configs) {
    const status = taskStatusMap[cfg.processName]?.status;
    if (status == processStatuses.completed) {
      updateResourceStatus(campaignDetails, cfg.type, resourceStatuses.completed, campaignDetails?.tenantId, useruuid);
    } else if (status == processStatuses.failed) {
      updateResourceStatus(campaignDetails, cfg.type, resourceStatuses.failed, campaignDetails?.tenantId, useruuid);
    }
  }

  if (anyTaskFailed) {
    const failedTasks = phase1Configs
      .filter(c => taskStatusMap[c.processName]?.status == processStatuses.failed)
      .map(c => c.processName);
    throwError("COMMON", 400, "RESOURCE_CREATION_ERROR", `${failedTasks.join(", ")} tasks failed.`);
  }
  else if (!allTaskCompleted) {
    throwError("COMMON", 400, "RESOURCE_CREATION_TIMED_OUT", "Resources creation timed out.");
  }
  logger.info(`Waiting for 20 seconds for all resources to get persisted...`);
  await new Promise(resolve => setTimeout(resolve, 20000));

  // Phase 2: Trigger dependent resource creation (e.g., attendanceRegister)
  await createPhase2Resources(campaignDetails, parentCampaign, useruuid, requestInfo);
}

async function createPhase2Resources(campaignDetails: any, parentCampaign: any, useruuid: string, requestInfo?: RequestInfo) {
  const phase2Configs = getPhase2Types();
  if (phase2Configs.length === 0) {
    logger.info("No Phase 2 resource types configured. Skipping.");
    return;
  }

  // Determine phase 2 types from eg_cm_resource_details table
  // Fall back to in-memory campaignDetails.resources if table is empty (backward compat)
  let campaignResourceTypes = new Set(
    (campaignDetails?.resources || []).map((r: any) => r?.type)
  );
  if (campaignDetails?.id && campaignDetails?.tenantId && campaignResourceTypes.size === 0) {
    try {
      const phase2TypeNames = phase2Configs.map(c => c.type);
      const tableRows = await searchResourceDetailsFromDB({
        tenantId: campaignDetails.tenantId,
        campaignId: campaignDetails.id,
        type: phase2TypeNames,
        isActive: true
      });
      campaignResourceTypes = new Set(tableRows.map((r: any) => r.type));
      // Also populate resources array so task messages carry filestoreId for handlers
      campaignDetails.resources = tableRows.map(r => toCampaignResource(toResourceDetailsResponse(r)));
      logger.info(`Loaded phase 2 resource types from table: ${Array.from(campaignResourceTypes).join(", ")}`);
    } catch (err) {
      logger.warn(`Could not fetch phase 2 types from table: ${err}`);
    }
  }
  const applicableConfigs = phase2Configs.filter(cfg => campaignResourceTypes.has(cfg.type));

  if (applicableConfigs.length === 0) {
    logger.info("No Phase 2 resources present in campaign. Skipping.");
    return;
  }

  const { maxAttemptsForResourceCreationOrMapping, waitTimeOfEachAttemptOfResourceCreationOrMappping } = config?.resourceCreationConfig;

  // All Phase 1 processes are completed at this point (dependencies met)
  const completedProcessNames = new Set(
    getResourceConfigsByPhase(1).map(c => c.processName)
  );

  let allCurrentProcesses = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId);

  for (const cfg of applicableConfigs) {
    if (!hasDependenciesMet(cfg.type, completedProcessNames)) {
      logger.warn(`Phase 2 resource ${cfg.type} dependencies not met. Skipping.`);
      continue;
    }

    const task = allCurrentProcesses.find((process: any) => process?.processName == cfg.processName);
    if (task && task?.status == processStatuses.pending) {
      logger.info(`Triggering Phase 2 resource creation: ${cfg.type} (${cfg.processName})`);
      await produceModifiedMessages({
        task,
        CampaignDetails: campaignDetails,
        parentCampaign,
        useruuid,
        requestInfo
      }, config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC, campaignDetails?.tenantId, cfg.kafkaKey);
    }
  }

  let allCompleted = false;
  let anyFailed = false;
  let attempts = 0;
  const taskStatusMap: Record<string, any> = {};
  const startTime = Date.now();

  while (!allCompleted && !anyFailed && attempts < maxAttemptsForResourceCreationOrMapping) {
    logger.info(`Phase 2 poll attempt ${attempts + 1}/${maxAttemptsForResourceCreationOrMapping}`);
    await new Promise(resolve => setTimeout(resolve, waitTimeOfEachAttemptOfResourceCreationOrMappping));

    for (const cfg of applicableConfigs) {
      const taskArray = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId, cfg.processName);
      taskStatusMap[cfg.processName] = taskArray[0];
    }

    allCompleted = applicableConfigs.every(c => taskStatusMap[c.processName]?.status == processStatuses.completed);
    anyFailed = applicableConfigs.some(c => taskStatusMap[c.processName]?.status == processStatuses.failed);

    const campaignResp = await searchProjectTypeCampaignService({ tenantId: campaignDetails?.tenantId, ids: [campaignDetails?.id] });
    const campaignDetailsStatus = campaignResp?.CampaignDetails?.[0]?.status;
    if (campaignDetailsStatus == campaignStatuses.failed || campaignDetailsStatus == campaignStatuses.cancelled || !campaignDetailsStatus) {
      throwError("COMMON", 400, "RESOURCE_CREATION_ERROR", "Campaign creation failed during Phase 2 resources creation.");
    }
    attempts++;
  }

  const totalTimeTakenInMs = Date.now() - startTime;
  logger.info(`⏱️ Phase 2 resource creation took: ${(totalTimeTakenInMs / 1000).toFixed(2)}s`);

  for (const cfg of applicableConfigs) {
    const status = taskStatusMap[cfg.processName]?.status;
    if (status == processStatuses.completed) {
      updateResourceStatus(campaignDetails, cfg.type, resourceStatuses.completed, campaignDetails?.tenantId, useruuid);
    } else if (status == processStatuses.failed) {
      updateResourceStatus(campaignDetails, cfg.type, resourceStatuses.failed, campaignDetails?.tenantId, useruuid);
    }
  }

  if (anyFailed) {
    const failedTasks = applicableConfigs
      .filter(c => taskStatusMap[c.processName]?.status == processStatuses.failed)
      .map(c => c.processName);
    throwError("COMMON", 400, "RESOURCE_CREATION_ERROR", `Phase 2 tasks failed: ${failedTasks.join(", ")}`);
  }
  else if (!allCompleted) {
    throwError("COMMON", 400, "RESOURCE_CREATION_TIMED_OUT", "Phase 2 resources creation timed out.");
  }
  logger.info("Phase 2 resource creation completed successfully.");
}

async function createAllMappings(campaignDetails: any, parentCampaign: any, useruuid: string, requestInfo?: RequestInfo) {
  const { maxAttemptsForResourceCreationOrMapping, waitTimeOfEachAttemptOfResourceCreationOrMappping } = config?.resourceCreationConfig;
  logger.info(`Starting mappings...`);
  const mappingTasks = [
    {
      processName: allProcesses.facilityMapping,
      kafkaKey: `facilityMapping_f13f7b6d-f5e4-4c27-9c94-ea6c77f7a32a` // → Partition 0
    },
    {
      processName: allProcesses.userMapping,
      kafkaKey: `userMapping_92df19d1-e1f1-41d9-abc2-b6ff06301b49` // → Partition 1
    },
    {
      processName: allProcesses.resourceMapping,
      kafkaKey: `resourceMapping_ab2eea59-45d4-4699-a116-1d4edfc25136` // → Partition 2
    }
  ];
  let allCurrentProcesses = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId);
  for (let i = 0; i < mappingTasks?.length; i++) {
      const { processName, kafkaKey } = mappingTasks[i];
    const task: any = allCurrentProcesses.find((process: any) => process?.processName == processName);
    if (task && task?.status == processStatuses.pending) {
      await produceModifiedMessages({
        task,
        CampaignDetails: campaignDetails,
        parentCampaign,
        useruuid,
        requestInfo
      }, config.kafka.KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC, campaignDetails?.tenantId, kafkaKey);
    }
  }
  let allTaskCompleted = false;
  let anyTaskFailed = false;
  let attempts = 0;
  let facilityMappingTask: any, userMappingTask: any, resourceMappingTask: any;
  const startTime = Date.now();
  while (allTaskCompleted == false && anyTaskFailed == false && attempts < maxAttemptsForResourceCreationOrMapping) {
    logger.info(`Attempt ${attempts + 1}/${maxAttemptsForResourceCreationOrMapping}`);
    logger.info(`Waiting ${waitTimeOfEachAttemptOfResourceCreationOrMappping / 1000}s before polling mapping statuses...`);
    await new Promise(resolve => setTimeout(resolve, waitTimeOfEachAttemptOfResourceCreationOrMappping));
    let facilityMappingTaskArray = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId, allProcesses.facilityMapping);
    facilityMappingTask = facilityMappingTaskArray[0];
    let userMappingTaskArray = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId, allProcesses.userMapping);
    userMappingTask = userMappingTaskArray[0];
    let resourceMappingTaskArray = await getCurrentProcesses(campaignDetails?.campaignNumber, campaignDetails?.tenantId, allProcesses.resourceMapping);
    resourceMappingTask = resourceMappingTaskArray[0];
    if (facilityMappingTask?.status == processStatuses.completed && userMappingTask?.status == processStatuses.completed && resourceMappingTask?.status == processStatuses.completed) {
      allTaskCompleted = true;
    }
    if (facilityMappingTask?.status == processStatuses.failed || userMappingTask?.status == processStatuses.failed || resourceMappingTask?.status == processStatuses.failed) {
      anyTaskFailed = true;
    }
    const campaignResp = await searchProjectTypeCampaignService({ tenantId: campaignDetails?.tenantId, ids: [campaignDetails?.id] });
    const campaignDetailsStatus = campaignResp?.CampaignDetails?.[0]?.status;
    if (campaignDetailsStatus == campaignStatuses.failed || campaignDetailsStatus == campaignStatuses.cancelled || !campaignDetailsStatus) {
      throwError("COMMON", 400, "RESOURCE_MAPPING_ERROR", "Campaign creation failed during mappings creation.");
    }
    attempts++;
  }

  const totalTimeTakenInMs = Date.now() - startTime;
  const totalTimeInSeconds = (totalTimeTakenInMs / 1000).toFixed(2);
  const totalTimeInMinutes = (totalTimeTakenInMs / (1000 * 60)).toFixed(2);
  logger.debug(
    `⏱️ Total time taken for mappings creation: ${totalTimeInSeconds}s (~${totalTimeInMinutes} minutes)`
  );

  if (anyTaskFailed) {
    let failedTasks = [];
    if (facilityMappingTask?.status == processStatuses.failed) {
      failedTasks.push(allProcesses.facilityMapping);
    }
    if (userMappingTask?.status == processStatuses.failed) {
      failedTasks.push(allProcesses.userMapping);
    }
    if (resourceMappingTask?.status == processStatuses.failed) {
      failedTasks.push(allProcesses.resourceMapping);
    }
    throwError("COMMON", 400, "RESOURCE_MAPPING_ERROR", `${failedTasks.join(", ")} tasks failed.`);
  }
  else if (!allTaskCompleted) {
    throwError("COMMON", 400, "RESOURCE_MAPPING_TIMED_OUT", "Mappings creation timed out.");
  }
  campaignDetails.status = campaignStatuses.completed;
}

async function processBasedOnAction(request: any, actionInUrl: any) {
  if (actionInUrl === "create") {
    request.body.CampaignDetails.id = uuidv4();
  }

  const generationCheck = isGenerationTriggerNeeded(request);
  await enrichAndPersistProjectCampaignForFirst(request, actionInUrl, true);

  const shouldTriggerGeneration =
    request?.body?.CampaignDetails?.action === "draft" &&
    generationCheck?.trigger &&
    request?.body?.CampaignDetails?.boundaries?.length;

  if (shouldTriggerGeneration) {
    await tryTriggerGenerateIfBoundariesSynced(request, generationCheck.newBoundaries);
  }

  processAfterPersistNew(request, actionInUrl);
}

async function tryTriggerGenerateIfBoundariesSynced(request: any, expectedBoundaries: any[]) {
  const maxRetries = 4;
  const retryDelayMs = config.boundary.syncRetryDelayMs;
  const campaignId = request?.body?.CampaignDetails?.id;
  const tenantId = request?.body?.CampaignDetails?.tenantId;

  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      logger.info(`Checking persisted campaign boundaries. Attempt ${attempt}`);
      const searchResponse = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
      const persistedBoundaries = searchResponse?.CampaignDetails?.[0]?.boundaries ?? [];

      if (areBoundariesSame(persistedBoundaries, expectedBoundaries)) {
        logger.info("Persisted boundaries match request. Triggering generation.");
        callGenerateIfBoundariesOrCampaignTypeDiffer(request);
        return;
      }

      logger.warn(`Persisted boundaries differ on attempt ${attempt}. Retrying...`);
    } catch (error: any) {
      logger.error(`Error checking persisted campaign boundaries on attempt ${attempt}: ${error.message}`);
    }

    if (attempt < maxRetries) {
      await new Promise(resolve => setTimeout(resolve, retryDelayMs));
    }
  }

  logger.error("Persisted boundaries did not match after 4 retries. Possible DB sync issue.");
  throwError("PERSISTENCE", 500, "BOUNDARY_SYNC_ERROR", "Boundaries could not be synced from DB after multiple retries.");
}

async function getLocalesFromStateInfo(tenantId: string): Promise<string[]> {
  const criteria = {
    tenantId,
    schemaCode: "common-masters.StateInfo",
    isActive: true,
  };

  const response = await searchMDMSDataViaV2Api({ MdmsCriteria: criteria });

  if (!response?.mdms?.length) {
    throw new Error("StateInfo data not found in MDMS");
  }

  const languageValues =
    response.mdms?.[0]?.data?.languages?.map((lang: any) => lang.value) || [];
  return languageValues;
}

async function getTemplateModules(
  tenantId: string,
  campaignType: string,
  schemaCode: string
): Promise<any[]> {
  const criteria = {
    tenantId,
    schemaCode,
    filters: { project: campaignType },
    isActive: true,
  };

  logger.info(
    `MDMS SEARCH [getTemplateModules] schemaCode=${schemaCode} | request body: ${JSON.stringify({ MdmsCriteria: criteria })}`
  );

  const response = await searchMDMSDataViaV2Api({ MdmsCriteria: criteria });

  return (response?.mdms || [])
    .map((item: any) => item?.data || [])
    .flat()
    .filter((module: any) => !module?.disabled);
}

async function upsertLocalisations(
  tenantId: string,
  baseModuleKey: string,
  updatedModuleKey: string,
  locales: string[],
  localisation: any,
  RequestInfo: any
): Promise<void> {
  const chunkSize = config.localisation.messageChunkSize;

  for (const locale of locales) {
    let messages: any[] = [];

    try {
      messages = await localisation.getLocalizationResponseMessages(
        baseModuleKey,
        locale,
        tenantId
      );
    } catch (e: any) {
      logger.error(`Failed to fetch localisation for ${baseModuleKey} (${locale}): ${e?.message}`);
      continue;
    }

    const updatedMessages = messages.map((entry: any) => ({
      ...entry,
      locale,
      module: updatedModuleKey,
    }));

    for (let i = 0; i < updatedMessages.length; i += chunkSize) {
      const chunk = updatedMessages.slice(i, i + chunkSize);
      await localisation.createLocalisation(chunk, tenantId, RequestInfo);
      logger.info(`Localisation added for ${updatedModuleKey} (${locale}) — chunk ${i / chunkSize + 1}`);
    }
  }
}

async function processAndInsertModules(
  tenantId: string,
  campaignType: string,
  campaignNumber: string,
  templateModules: any[],
  locales: string[],
  localisation: any,
  schemaCode: string,
  RequestInfo: any
): Promise<void> {
  const baseType = campaignType.toLowerCase();
  const useruuid = RequestInfo?.userInfo?.uuid;
  if (!useruuid) {
    throw new Error("User uuid not found in request");
  }
  if (!templateModules?.length) {
    logger.warn("No template modules found");
    return;
  }

  await Promise.all(
    templateModules.map((template) => {
      logger.info(`Inserting template module: ${template?.name} for campaign number ${campaignNumber}`);

      const moduleData = {
        ...template,
        project: campaignNumber,
        isSelected: true,
      };

      return createMdmsData(tenantId, schemaCode, moduleData, RequestInfo);
    })
  );

  for (const template of templateModules) {
    const moduleName = template?.name;
    if (!moduleName) continue;
    const baseKey = `hcm-base-${moduleName.toLowerCase()}-${baseType}`;
    const updatedKey = `hcm-${moduleName.toLowerCase()}-${campaignNumber}`;
    await upsertLocalisations(
      tenantId,
      baseKey,
      updatedKey,
      locales,
      localisation,
      RequestInfo
    );
  }
}

/**
 * Provision the app-config MDMS modules and their localisations for a new campaign from its type template.
 */
export async function createAppConfig(
  tenantId: string,
  campaignNumber: string,
  campaignType: string,
  RequestInfo: any
): Promise<void> {
  try {
    if (!campaignNumber) {
      throw new Error(`createAppConfig: campaignNumber is required but got: ${campaignNumber}`);
    }
    if (!campaignType) {
      throw new Error(`createAppConfig: campaignType is required but got: ${campaignType}`);
    }
    logger.info("Creating app configuration...");

    const moduleName = config.values.moduleName;
    const FormConfigTemplate = config.values.formConfigTemplateName;
    const FormConfig = config.values.formConfigName;
    const templateSchema = `${moduleName}.${FormConfigTemplate}`;
    const configSchema = `${moduleName}.${FormConfig}`;
    logger.debug(`Template Schema: ${templateSchema}`);
    logger.debug(`Config Schema: ${configSchema}`);

    const [locales, localisation] = await Promise.all([
      getLocalesFromStateInfo(tenantId),
      Localisation.getInstance(),
    ]);

    const templateModules = await getTemplateModules(tenantId, campaignType, templateSchema);

    await processAndInsertModules(
      tenantId,
      campaignType,
      campaignNumber,
      templateModules,
      locales,
      localisation,
      configSchema,
      RequestInfo
    );

    logger.info("App configuration created successfully.");
  } catch (err: any) {
    logger.error(`Failed to create app config: ${err?.message}`);
    throw err;
  }
}

/**
 * Copy the app-config MDMS modules and localisations from an existing campaign into a new one.
 */
export async function createAppConfigFromClone(
  tenantId: string,
  newCampaignNumber: string,
  cloneFromCampaignNumber: string,
  RequestInfo: any
): Promise<void> {
  try {
    if (!newCampaignNumber) {
      throw new Error(`createAppConfigFromClone: newCampaignNumber is required but got: ${newCampaignNumber}`);
    }
    if (!cloneFromCampaignNumber) {
      throw new Error(`createAppConfigFromClone: cloneFromCampaignNumber is required but got: ${cloneFromCampaignNumber}`);
    }
    logger.info("Started creating app config from clone...");

    const moduleName = config.values.moduleName;
    const FormConfig = config.values.formConfigName;
    const configSchema = `${moduleName}.${FormConfig}`;

    logger.debug(`Config Schema: ${configSchema}`);
    
    const useruuid = RequestInfo?.userInfo?.uuid;
    if (!useruuid) {
      throw new Error("User uuid not found in request");
    }

    const [locales, localisation] = await Promise.all([
      getLocalesFromStateInfo(tenantId),
      Localisation.getInstance(),
    ]);

    const cloneResponse = await fetchCloneModules(
      tenantId,
      configSchema,
      cloneFromCampaignNumber
    );

    const modulesToClone = (cloneResponse?.mdms || [])
      .map((item: any) => item?.data)
      .flat()
      .filter(Boolean);

    if (!modulesToClone.length) {
      logger.warn("No modules found to clone.");
      return;
    }

    for (const module of modulesToClone) {
      const moduleName = module?.name;
      if (!moduleName) continue;

      const baseKey = `hcm-${moduleName.toLowerCase()}-${cloneFromCampaignNumber}`;
      const newKey = `hcm-${moduleName.toLowerCase()}-${newCampaignNumber}`;

      await upsertLocalisations(tenantId, baseKey, newKey, locales, localisation, RequestInfo);

      const newModule = {
        ...module,
        project: newCampaignNumber,
        isSelected: true,
      };

      await createMdmsData(tenantId, configSchema, newModule, RequestInfo);
    }

    logger.info("App configuration cloned successfully.");
  } catch (err: any) {
    logger.error(`Failed to clone app config: ${err?.message}`);
    throw err;
  }
}

async function fetchCloneModules(
  tenantId: string,
  configSchema: string,
  cloneFromCampaignNumber: string
): Promise<any> {
  if (!cloneFromCampaignNumber) {
    throw new Error(`fetchCloneModules: cloneFromCampaignNumber is required but got: ${cloneFromCampaignNumber}`);
  }

  const cloneCriteria = {
    tenantId,
    schemaCode: configSchema,
    filters: { project: cloneFromCampaignNumber },
    isActive: true,
  };

  logger.info(
    `MDMS SEARCH [fetchCloneModules] schemaCode=${configSchema} | request body: ${JSON.stringify({ MdmsCriteria: cloneCriteria })}`
  );

  return await searchMDMSDataViaV2Api({ MdmsCriteria: cloneCriteria });
}

async function getLocalizedHierarchy(request: any, localizationMap: any) {
  var hierarchy = await getHierarchy(
    request?.query?.tenantId,
    request?.query?.hierarchyType
  );
  var modifiedHierarchy = hierarchy.map((ele) =>
    `${request?.query?.hierarchyType}_${ele}`.toUpperCase()
  );
  var resultHierarchy = getLocalizedHeaders(modifiedHierarchy, localizationMap);
  return resultHierarchy;
}

function generateChecklistKeys(
  mdmsData: any,
  cloneFromCampaignNumber: string
): string[] {
  if (!Array.isArray(mdmsData)) return [];

  const checklistArray = mdmsData
    .filter((item: any) => item?.data?.checklistType && item?.data?.role)
    .map((item: any) => {
      return `${cloneFromCampaignNumber}.${item.data.checklistType}.${item.data.role}`;
    });

  return checklistArray;
}

function sanitizeServiceDefinitions(serviceDefinitions: any[]): any[] {
  return serviceDefinitions.map((def: any) => {
    const { id, auditDetails, ...rest } = def;
    return rest;
  });
}


async function upsertChecklistLocalization(
  newCampaignNumber: string,
  newCampaignName: string,
  cloneFromCampaignNumber: string,
  tenantId: string,
  locales: string[],
  localisation: any,
  RequestInfo: any
): Promise<void> {
  const cloneModule = `hcm-checklist-${cloneFromCampaignNumber}`;
  const newModule = `hcm-checklist-${newCampaignNumber}`;
  const chunkSize = 100;

  for (const locale of locales) {
    let messages: any[] = [];
    try {
      messages = await localisation.getLocalizationResponseMessages(cloneModule, locale, tenantId);
    } catch (e: any) {
      logger.error(`upsertChecklistLocalization: failed to fetch localization for ${cloneModule} (${locale}): ${e?.message}`);
      continue;
    }

    // Step 1: Replace the campaign name prefix in each code with newCampaignName
    const updatedMessages = messages.map((entry: any) => {
      const dotIndex = (entry.code as string).indexOf(".");
      const newCode = dotIndex !== -1
        ? `${newCampaignName}${(entry.code as string).slice(dotIndex)}`
        : entry.code;
      return { ...entry, code: newCode, module: newModule, locale };
    });

    // Step 2: Upsert to new module in chunks
    for (let i = 0; i < updatedMessages.length; i += chunkSize) {
      const chunk = updatedMessages.slice(i, i + chunkSize);
      await localisation.createLocalisation(chunk, tenantId, RequestInfo);
      logger.info(`upsertChecklistLocalization: upserted ${chunk.length} messages to ${newModule} (${locale}) — chunk ${Math.floor(i / chunkSize) + 1}`);
    }
  }
}

async function createClonedChecklist(
  clonedServiceDefinitions: any[],
  newCampaignName: string,
  tenantId: string
): Promise<void> {
  if (!clonedServiceDefinitions.length) return;

  for (const def of clonedServiceDefinitions) {
    const oldCode: string = def.code;
    const dotIndex = oldCode.indexOf(".");
    const newCode = dotIndex !== -1
      ? `${newCampaignName}${oldCode.slice(dotIndex)}`
      : oldCode;
    const newDef = { ...def, code: newCode };
    await createServiceDefinition(tenantId, newDef);
    logger.info(`createClonedChecklist: created service definition ${newCode}`);
  }
}

async function fetchCloneChecklist(
  projectType: string,
  cloneFromCampaignNumber: string,
  tenantId: string
): Promise<any[]> {
  // Step 1: Fetch checklist templates from MDMS v2 filtered by campaignType
  logger.info(`fetchCloneChecklist: fetching MDMS checklist templates for projectType=${projectType}, tenant=${tenantId}`);
  const mdmsCriteria = {
    tenantId,
    schemaCode: "HCM-ADMIN-CONSOLE.ChecklistTemplates",
    filters: {
      campaignType: projectType,
    },
  };
  const mdmsResponse: any = await searchMDMSDataViaV2Api({ MdmsCriteria: mdmsCriteria });
  const mdmsData = mdmsResponse?.mdms || [];
  if (!mdmsData.length) {
    logger.warn(`fetchCloneChecklist: no MDMS checklist templates found for projectType=${projectType}, skipping further steps`);
    return [];
  }

  //search the campaign name for the cloneFromCampaignNumber to generate the checklist keys
  const campaignSearchResponse = await searchProjectTypeCampaignService({ tenantId, campaignNumber: cloneFromCampaignNumber });
  const cloneFromCampaignName: string = campaignSearchResponse?.CampaignDetails?.[0]?.campaignName;
  if (!cloneFromCampaignName) {
    logger.warn(`fetchCloneChecklist: could not resolve campaignName for campaignNumber=${cloneFromCampaignNumber}`);
    return [];
  }

  // Step 3: Generate checklist keys using the resolved campaign name
  logger.info(`fetchCloneChecklist: generating checklist keys from ${mdmsData.length} MDMS records for campaignName=${cloneFromCampaignName}`);
  const checklistKeys: string[] = generateChecklistKeys(mdmsData, cloneFromCampaignName);

  if (!checklistKeys.length) {
    logger.warn(`fetchCloneChecklist: no checklist keys generated for projectType=${projectType}, campaignName=${cloneFromCampaignName}`);
    return [];
  }

  // Step 4: Search service definitions using the generated checklist keys
  logger.info(`fetchCloneChecklist: searching service definitions for ${checklistKeys.length} keys`);
  const serviceDefinitions = await searchServiceDefinitions(tenantId, checklistKeys, true);

  // Step 5: Strip top-level id and auditDetails from each service definition
  const sanitized = sanitizeServiceDefinitions(serviceDefinitions);

  logger.info(`fetchCloneChecklist: found ${sanitized.length} service definitions`);
  return sanitized;
}

async function appendSheetsToWorkbook(
  request: any,
  boundaryData: any[],
  differentTabsBasedOnLevel: any,
  localizationMap?: any,
  fileUrl?: any
) {
  try {
    logger.info(
      "Received Boundary data for generating  different tabs based on configured boundary level"
    );
    const hierarchy: any[] = await getLocalizedHierarchy(
      request,
      localizationMap
    );
    const workbook = getNewExcelWorkbook();
    const type = request?.query?.type;
    const headingInSheet = headingMapping?.[type];
    const localisedHeading = getLocalizedName(headingInSheet, localizationMap);
    await createReadMeSheet(
      request,
      workbook,
      localisedHeading,
      localizationMap
    );
    const [
      mainSheetData,
      uniqueDistrictsForMainSheet,
      districtLevelRowBoundaryCodeMap,
    ] = createBoundaryDataMainSheet(
      request,
      boundaryData,
      differentTabsBasedOnLevel,
      hierarchy,
      localizationMap
    );
    const responseFromCampaignSearch = await getCampaignSearchResponse(request);
    const campaignObject = responseFromCampaignSearch?.CampaignDetails?.[0];
    const mainSheet = workbook.addWorksheet(
      getLocalizedName(getBoundaryTabName(), localizationMap)
    );
    const columnWidths = Array(12).fill(30);
    mainSheet.columns = columnWidths.map((width) => ({ width }));
    addDataToSheet(
      request,
      mainSheet,
      mainSheetData,
      "F3842D",
      30,
      false,
      true
    );
    mainSheet.state = "hidden";
    logger.info("appending different districts tab in the sheet started");
    await appendDistricts(
      request,
      workbook,
      uniqueDistrictsForMainSheet,
      differentTabsBasedOnLevel,
      boundaryData,
      localizationMap,
      districtLevelRowBoundaryCodeMap,
      hierarchy,
      campaignObject,
      fileUrl
    );
    logger.info("Sheet with different tabs generated successfully");
    return workbook;
  } catch (error) {
    console.log(error);
    throw Error("An error occurred while creating tabs based on district:");
  }
}

async function appendDistricts(
  request: any,
  workbook: any,
  uniqueDistrictsForMainSheet: any,
  differentTabsBasedOnLevel: any,
  boundaryData: any,
  localizationMap: any,
  districtLevelRowBoundaryCodeMap: any,
  hierarchy: any,
  campaignObject: any,
  fileUrl?: any
) {
  const configurableColumnHeadersFromSchemaForTargetSheet =
    await getConfigurableColumnHeadersFromSchemaForTargetSheet(
      request,
      hierarchy,
      boundaryData,
      differentTabsBasedOnLevel,
      campaignObject,
      localizationMap
    );
  let sheetNamesOfProcessedFile: any;
  if (fileUrl) {
    const processedWorkbook = await getTargetWorkbook(fileUrl, localizationMap);
    sheetNamesOfProcessedFile = processedWorkbook.worksheets.map(
      (sheet: any) => sheet.name
    );
  }
  for (const uniqueData of uniqueDistrictsForMainSheet) {
    const uniqueDataFromLevelForDifferentTabs = uniqueData.slice(
      uniqueData.lastIndexOf("#") + 1
    );
    logger.info(
      `generating the boundary data for ${uniqueDataFromLevelForDifferentTabs} - ${differentTabsBasedOnLevel}`
    );
    const districtDataFiltered = boundaryData.filter(
      (boundary: any) =>
        boundary[differentTabsBasedOnLevel] ===
        uniqueDataFromLevelForDifferentTabs &&
        boundary[hierarchy[hierarchy.length - 1]]
    );
    const modifiedFilteredData = modifyFilteredData(
      districtDataFiltered,
      districtLevelRowBoundaryCodeMap.get(uniqueData),
      differentTabsBasedOnLevel,
      localizationMap
    );
    if (modifiedFilteredData?.[0]) {
      const newSheetData = [configurableColumnHeadersFromSchemaForTargetSheet];
      for (const data of modifiedFilteredData) {
        var rowData: any[] = [];
        for (const header of configurableColumnHeadersFromSchemaForTargetSheet) {
          rowData.push(data[header] || "");
        }
        newSheetData.push(rowData);
      }

      await createNewSheet(
        request,
        workbook,
        newSheetData,
        uniqueData,
        localizationMap,
        districtLevelRowBoundaryCodeMap,
        configurableColumnHeadersFromSchemaForTargetSheet,
        campaignObject,
        sheetNamesOfProcessedFile,
        fileUrl
      );
      logger.info(
        `${uniqueDataFromLevelForDifferentTabs} - ${differentTabsBasedOnLevel} boundary data generation completed`
      );
    }
  }
}

async function createNewSheet(
  request: any,
  workbook: any,
  newSheetData: any,
  uniqueData: any,
  localizationMap: any,
  districtLevelRowBoundaryCodeMap: any,
  localizedHeaders: any,
  campaignObject: any,
  sheetNamesOfProcessedFile: any,
  fileUrl?: any
) {
  const newSheet = workbook.addWorksheet(
    getLocalizedName(
      districtLevelRowBoundaryCodeMap.get(uniqueData),
      localizationMap
    )
  );
  let modifiedNewSheetData: any = newSheetData;
  const oldTargetColumnsToHide: any[] = [];
  if (fileUrl) {
    let processedDistrictSheetData: any;
    if (
      sheetNamesOfProcessedFile.includes(
        getLocalizedName(
          districtLevelRowBoundaryCodeMap.get(uniqueData),
          localizationMap
        )
      )
    ) {
      processedDistrictSheetData = await getSheetData(
        fileUrl,
        getLocalizedName(
          districtLevelRowBoundaryCodeMap.get(uniqueData),
          localizationMap
        ),
        false,
        undefined,
        localizationMap
      );
    }
    modifiedNewSheetData = modifyNewSheetData(
      processedDistrictSheetData,
      newSheetData,
      localizedHeaders,
      oldTargetColumnsToHide,
      localizationMap
    );
  }
  addDataToSheet(request, newSheet, modifiedNewSheetData, "F3842D", 40);
  if (oldTargetColumnsToHide && oldTargetColumnsToHide.length > 0) {
    const columnIndexesToBeHidden: any[] = [];
    oldTargetColumnsToHide.forEach((column: any) => {
      const localizedColumn = getLocalizedName(column, localizationMap);
      const columnIndex = getColumnIndexByHeader(newSheet, localizedColumn);
      columnIndexesToBeHidden.push(columnIndex);
    });
    hideColumnsOfProcessedFile(newSheet, columnIndexesToBeHidden);
  }
  let columnsNotToBeFreezed: any;
  const boundaryCodeColumnIndex = localizedHeaders.findIndex(
    (header: any) =>
      header ===
      getLocalizedName(config?.boundary?.boundaryCode, localizationMap)
  );
  if (
    isDynamicTargetTemplateForProjectType(campaignObject?.projectType) &&
    campaignObject.deliveryRules &&
    campaignObject.deliveryRules.length > 0
  ) {
    columnsNotToBeFreezed = localizedHeaders.slice(boundaryCodeColumnIndex + 1);
  } else {
    const mdmsResponse = await getMdmsDataBasedOnCampaignType(
      request,
      localizationMap
    );
    columnsNotToBeFreezed = mdmsResponse?.columnsNotToBeFreezed;
  }
  const localizedColumnsNotToBeFreezed = getLocalizedHeaders(
    columnsNotToBeFreezed,
    localizationMap
  );
  lockTargetFields(
    newSheet,
    localizedColumnsNotToBeFreezed,
    boundaryCodeColumnIndex
  );
}

function modifyFilteredData(
  districtDataFiltered: any,
  targetBoundaryCode: any,
  differentTabsBasedOnLevel: any,
  localizationMap?: any
): any {
  const desiredBoundaryCode = getLocalizedName(
    targetBoundaryCode,
    localizationMap
  );
  const modifiedFilteredData = districtDataFiltered.filter((row: any) => {
    return row[differentTabsBasedOnLevel] == desiredBoundaryCode;
  });
  return modifiedFilteredData;
}

async function generateFilteredBoundaryData(
  request: any,
  FiltersFromCampaignId: any
) {
  const rootBoundary: any = (FiltersFromCampaignId?.Filters?.boundaries).filter(
    (boundary: any) => boundary.isRoot
  );
  const boundaryRelationshipResponse = await searchBoundaryRelationshipData(request?.query?.tenantId, request?.query?.hierarchyType, true, true, true, rootBoundary?.[0]?.code);
  const boundaryDataFromRootOnwards = boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary;
  logger.info(`filtering the boundaries`);
  const filteredBoundaryList = filterBoundaries(
    boundaryDataFromRootOnwards,
    FiltersFromCampaignId?.Filters
  );
  logger.info(`filtered the boundaries based on given criteria`);
  return filteredBoundaryList;
}

function filterBoundaries(boundaryData: any[], filters: any): any {
  function filterRecursive(boundary: any): any {
    const boundaryFilters = filters && filters.boundaries;
    const filter = boundaryFilters?.find(
      (f: any) =>
        f.code === boundary.code && f.boundaryType === boundary.boundaryType
    );

    if (!filter) {
      return {
        ...boundary,
        children: boundary.children.map(filterRecursive),
      };
    }

    if (!boundary.children.length) {
      if (!filter.includeAllChildren) {
        logger.warn(
          "Boundary cannot have includeAllChildren filter false if it does not have any children"
        );
      }
      return {
        ...boundary,
        children: [],
      };
    }

    if (filter.includeAllChildren) {
      return {
        ...boundary,
        children: boundary.children.map(filterRecursive),
      };
    }

    const filteredChildren: any[] = [];
    boundary.children.forEach((child: any) => {
      const matchingFilter = boundaryFilters.find(
        (f: any) =>
          f.code === child.code && f.boundaryType === child.boundaryType
      );
      if (matchingFilter) {
        filteredChildren.push(filterRecursive(child));
      }
    });
    return {
      ...boundary,
      children: filteredChildren,
    };
  }
  const filteredData = boundaryData.map(filterRecursive);
  return filteredData;
}

function generateHierarchy(boundaries: any[]) {
  const parentMap: any = {};

  for (const boundary of boundaries) {
    parentMap[boundary.boundaryType] = boundary.parentBoundaryType;
  }

  const hierarchyList = [];
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === null) {
        hierarchyList.push(boundaryType);
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
  return hierarchyList;
}

function traverseChildren(parent: any, parentMap: any, hierarchyList: any[]) {
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === parent) {
        hierarchyList.push(boundaryType);
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
}

function createBoundaryMap(
  boundaries: any[],
  boundaryMap: Map<string, string>
): void {
  for (const boundary of boundaries) {
    boundaryMap.set(boundary.code, boundary.boundaryType);
    if (boundary.children.length > 0) {
      createBoundaryMap(boundary.children, boundaryMap);
    }
  }
}


/**
 * Compute parent- and current-level target totals per boundary row from MDMS beneficiary config.
 */
export async function processDataForTargetCalculation(request: any, jsonData: any, codeColumnName: string, localizationMap?: any) {
  const targetConfigs = await searchMDMS([request?.body?.CampaignDetails?.projectType], "HCM-ADMIN-CONSOLE.targetConfigs", request?.body?.RequestInfo);

  const resultantData = jsonData.map((row: any) => {

    let rowData: any = { [codeColumnName]: row[codeColumnName] };

    rowData['Parent Target at the Selected Boundary level'] = {};
    rowData['Target at the Selected Boundary level'] = {};
    const beneficiaries = targetConfigs?.mdms?.[0]?.data?.beneficiaries;
    calculateTargetsAtParentLevel(request, row, rowData, beneficiaries, localizationMap);
    calculateTargetsAtCurrentLevel(row, rowData, beneficiaries, localizationMap);

    // Return the processed row data
    return rowData;
  }).filter(Boolean); // skip the header row (null entries)

  return resultantData;
}

/**
 * Sum each beneficiary's parent-campaign target columns (the "(OLD)" columns) into rowData.
 */
export function calculateTargetsAtParentLevel(request: any, row: any, rowData: any, beneficiaries: any, localizationMap?: any) {
  if (request?.body?.parentCampaign) {
    if (Array.isArray(beneficiaries) && beneficiaries?.length > 0) {
      for (const beneficiary of beneficiaries) {
        const beneficiaryType = beneficiary?.beneficiaryType;
        const columns = beneficiary?.columns;
        let totalParentValue = 0;

        for (const col of columns) {
          const parentValue = row[`${getLocalizedName(col, localizationMap)}(OLD)`];
          if (typeof parentValue === 'number' && Number.isInteger(parentValue)) {
            totalParentValue += parentValue;
          }
        }
        rowData['Parent Target at the Selected Boundary level'][beneficiaryType] = totalParentValue;
      }
    }
    else {
      logger.warn("No beneficiaries config found for the specified campaign type");
    }
  }
}

/**
 * Sum each beneficiary's current-campaign target columns into rowData.
 */
export function calculateTargetsAtCurrentLevel(row: any, rowData: any, beneficiaries: any, localizationMap?: any) {
  if (Array.isArray(beneficiaries) && beneficiaries?.length > 0) {
    for (const beneficiary of beneficiaries) {
      const beneficiaryType = beneficiary?.beneficiaryType;
      const columns = beneficiary?.columns;
      let totalCurrentValue = 0;

      for (const col of columns) {
        const currentValue = row[getLocalizedName(col, localizationMap)];
        if (typeof currentValue === 'number' && Number.isInteger(currentValue)) {
          totalCurrentValue += currentValue;
        }
      }
      rowData['Target at the Selected Boundary level'][beneficiaryType] = totalCurrentValue;
    }
  }
  else {
    logger.warn("No beneficiaries config found for the specified campaign type");
  }
}

async function getResourceDetails(request: any) {
  const { tenantId, type, hierarchyType } =
    request?.body?.ResourceDetails || request?.query;
  const resourceDetails = request?.body?.ResourceDetails;

  request.body.SearchCriteria = request.body.SearchCriteria || {};

  request.body.SearchCriteria = {
    tenantId: tenantId,
    type: type,
    hierarchyType: hierarchyType,
    status: resourceDataStatuses.completed,
  };

  const response = await searchDataService(request);
  request.body.ResourceDetails = resourceDetails;
  if (response.length > 0) {
    response.sort(
      (a: any, b: any) =>
        b.auditDetails.lastModifiedTime - a.auditDetails.lastModifiedTime
    );
    return response[0];
  } else {
    return null;
  }
}

async function convertSheetToDifferentTabs(
  request: any,
  boundaryData: any,
  differentTabsBasedOnLevel: any,
  localizationMap?: any,
  fileUrl?: any
) {
  const updatedWorkbook = await appendSheetsToWorkbook(
    request,
    boundaryData,
    differentTabsBasedOnLevel,
    localizationMap,
    fileUrl
  );
  const boundaryDetails = await createAndUploadFile(updatedWorkbook, request);
  return boundaryDetails;
}

async function getBoundaryDataAfterGeneration(
  result: any,
  request: any,
  localizationMap?: any
) {
  const fileStoreId = result[0].fileStoreId;
  const fileResponse = await httpRequest(
    config.host.filestore + config.paths.filestore + "/url",
    {},
    { tenantId: request?.query?.tenantId, fileStoreIds: fileStoreId },
    "get"
  );
  if (!fileResponse?.fileStoreIds?.[0]?.url) {
    throwError("FILE", 400, "INVALID_FILE");
  }
  const boundaryData = await getSheetData(
    fileResponse?.fileStoreIds?.[0]?.url,
    getBoundaryTabName(),
    false,
    undefined,
    localizationMap
  );
  return boundaryData;
}

function getLocalizedName(
  expectedName: string,
  localizationMap?: { [key: string]: string }
) {
  if (!localizationMap || !(expectedName in localizationMap)) {
    return expectedName;
  }
  const localizedName = localizationMap[expectedName];
  // Fall back to the code when the localized value is missing or blank, so a
  // not-yet-localized (e.g. non-ASCII) name never renders as an empty cell.
  return localizedName && localizedName.trim() !== "" ? localizedName : expectedName;
}


async function getTargetBoundariesRelatedToCampaignId(
  request: any,
  localizationMap?: any
) {
  let CampaignDetailsNew: any;
  if (request?.body?.ResourceDetails?.campaignId) {
    const CampaignDetails = {
      ids: [request?.body?.ResourceDetails?.campaignId],
      tenantId: request?.body?.ResourceDetails?.tenantId,
    }
    const response = await searchProjectTypeCampaignService(CampaignDetails);
    if (response?.CampaignDetails?.[0]) {
      CampaignDetailsNew = response?.CampaignDetails?.[0];
      await addBoundariesForData(request, CampaignDetailsNew);
    } else {
      throwError(
        "CAMPAIGN",
        400,
        "CAMPAIGN_NOT_FOUND",
        "Campaign not found while Validating sheet boundaries"
      );
    }
  }
  return CampaignDetailsNew?.boundaries;
}

async function getFiltersFromCampaignSearchResponse(
  request: any,
  responseFromCampaignSearch: any
) {
  const boundaries = await getBoundariesFromCampaignSearchResponse(
    request,
    responseFromCampaignSearch?.CampaignDetails?.[0]
  );
  const boundariesModified = boundaries?.map((ele: any) => ({
    ...ele,
    boundaryType: ele?.type,
  }));
  if (!boundariesModified) {
    logger.info(`no boundaries found so considering the complete hierarchy`);
    return { Filters: null };
  }
  logger.info(`boundaries found for filtering`);
  return { Filters: { boundaries: boundariesModified } };
}

const getConfigurableColumnHeadersBasedOnCampaignType = async (
  request: any,
  localizationMap?: any
) => {
  try {
    const responseFromCampaignSearch = await getCampaignSearchResponse(request);
    const campaignObject = responseFromCampaignSearch?.CampaignDetails?.[0];
    let campaignType = campaignObject?.projectType;
    const isSourceMicroplan = checkIfSourceIsMicroplan(campaignObject);
    campaignType = isSourceMicroplan
      ? `${config?.prefixForMicroplanCampaigns}-${campaignType}`
      : campaignType;
    const isUpdate = request?.body?.parentCampaignObject ? true : false;
    const mdmsResponse = await callMdmsTypeSchema(
      request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId,
      isUpdate,
      request?.query?.type || request?.body?.ResourceDetails?.type,
      campaignType
    );
    if (!mdmsResponse || mdmsResponse?.columns.length === 0) {
      logger.error(
        `Campaign Type ${campaignType} has not any columns configured in schema`
      );
      throwError(
        "COMMON",
        400,
        "SCHEMA_ERROR",
        `Campaign Type ${campaignType} has not any columns configured in schema`
      );
    }
    const columnsForGivenCampaignId = mdmsResponse?.columns;

    const headerColumnsAfterHierarchy = getLocalizedHeaders(
      columnsForGivenCampaignId,
      localizationMap
    );
    if (
      !headerColumnsAfterHierarchy.includes(
        getLocalizedName(config.boundary.boundaryCode, localizationMap)
      )
    ) {
      logger.error(
        `Column Headers of generated Boundary Template does not have ${getLocalizedName(
          config.boundary.boundaryCode,
          localizationMap
        )} column`
      );
      throwError(
        "COMMON",
        400,
        "VALIDATION_ERROR",
        `Column Headers of generated Boundary Template does not have ${getLocalizedName(
          config.boundary.boundaryCode,
          localizationMap
        )} column`
      );
    }
    return headerColumnsAfterHierarchy;
  } catch (error: any) {
    console.log(error);
    throwError(
      "FILE",
      400,
      "FETCHING_COLUMN_ERROR",
      "Error fetching column Headers From Schema (either boundary code column not found or given  Campaign Type not found in schema) Check logs"
    );
  }
};

async function getFinalValidHeadersForTargetSheetAsPerCampaignType(
  request: any,
  hierarchy: any[],
  differentTabsBasedOnLevel: any,
  localizationMap?: any
) {
  const modifiedHierarchy = hierarchy.map((ele) =>
    `${request?.body?.ResourceDetails?.hierarchyType}_${ele}`.toUpperCase()
  );
  const localizedHierarchy = getLocalizedHeaders(
    modifiedHierarchy,
    localizationMap
  );
  const index = localizedHierarchy.indexOf(
    getLocalizedName(differentTabsBasedOnLevel, localizationMap)
  );
  const responseFromCampaignSearch = await getCampaignSearchResponse(request);
  const campaignObject = responseFromCampaignSearch?.CampaignDetails?.[0];
  const isSourceMicroplan = checkIfSourceIsMicroplan(campaignObject);
  var expectedHeadersForTargetSheetUptoHierarchy: any;
  if (isSourceMicroplan) {
    expectedHeadersForTargetSheetUptoHierarchy = localizedHierarchy;
  } else {
    expectedHeadersForTargetSheetUptoHierarchy =
      index !== -1
        ? localizedHierarchy.slice(index)
        : throwError(
          "COMMON",
          400,
          "VALIDATION_ERROR",
          `${differentTabsBasedOnLevel} level not present in the hierarchy`
        );
  }
  const columnFromSchemaOfTargetTemplate = await generateDynamicTargetHeaders(
    request,
    campaignObject,
    localizationMap
  );
  const localizedcolumnFromSchemaOfTargetTemplate = getLocalizedHeaders(
    columnFromSchemaOfTargetTemplate,
    localizationMap
  );
  let updatedLocalizedcolumnFromSchemaOfTargetTemplate =
    localizedcolumnFromSchemaOfTargetTemplate;
  if (request?.body?.parentCampaignObject) {
    updatedLocalizedcolumnFromSchemaOfTargetTemplate =
      localizedcolumnFromSchemaOfTargetTemplate
        .map((item: any) => `${item}(OLD)`)
        .concat(localizedcolumnFromSchemaOfTargetTemplate);
  }
  const expectedHeadersForTargetSheet = [
    ...expectedHeadersForTargetSheetUptoHierarchy,
    getLocalizedName(config?.boundary?.boundaryCode, localizationMap),
    ...updatedLocalizedcolumnFromSchemaOfTargetTemplate,
  ];
  return expectedHeadersForTargetSheet;
}

async function getDifferentTabGeneratedBasedOnConfig(
  request: any,
  boundaryDataGeneratedBeforeDifferentTabSeparation: any,
  localizationMap?: any,
  fileUrl?: any
) {
  var boundaryDataGeneratedAfterDifferentTabSeparation: any =
    boundaryDataGeneratedBeforeDifferentTabSeparation;
  const boundaryData = await getBoundaryDataAfterGeneration(
    boundaryDataGeneratedBeforeDifferentTabSeparation,
    request,
    localizationMap
  );
  let differentTabsBasedOnLevel = await getBoundaryOnWhichWeSplit(
    request?.query?.campaignId,
    request?.query?.tenantId
  );
  differentTabsBasedOnLevel = getLocalizedName(
    `${request?.query?.hierarchyType}_${differentTabsBasedOnLevel}`.toUpperCase(),
    localizationMap
  );
  logger.info(
    `Boundaries are seperated based on hierarchy type ${differentTabsBasedOnLevel}`
  );
  const isKeyOfThatTypePresent = boundaryData.some((data: any) =>
    data.hasOwnProperty(differentTabsBasedOnLevel)
  );
  const boundaryTypeOnWhichWeSplit = boundaryData.filter(
    (data: any) => data[differentTabsBasedOnLevel]
  );
  if (
    isKeyOfThatTypePresent &&
    boundaryTypeOnWhichWeSplit.length >=
    parseInt(config?.boundary?.numberOfBoundaryDataOnWhichWeSplit)
  ) {
    logger.info(
      `sinces the conditions are matched boundaries are getting splitted into different tabs`
    );
    boundaryDataGeneratedAfterDifferentTabSeparation =
      await convertSheetToDifferentTabs(
        request,
        boundaryData,
        differentTabsBasedOnLevel,
        localizationMap,
        fileUrl
      );
  }
  return boundaryDataGeneratedAfterDifferentTabSeparation;
}

async function getBoundaryOnWhichWeSplit(campaignId: string, tenantId: string) {
  const campaignDetailsResponse: any = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
  const campaignDetails: any = campaignDetailsResponse?.CampaignDetails?.[0];
  const MdmsCriteria: any = {
    tenantId: tenantId,
    schemaCode: `${config.values.moduleName}.${config.masterNameForSplitBoundariesOn}`,
    filters: {
      hierarchy: campaignDetails?.hierarchyType,
    },
  };
  const mdmsResponse: any = await searchMDMSDataViaV2Api(MdmsCriteria);
  if (!Array.isArray(mdmsResponse?.mdms) || mdmsResponse.mdms.length === 0) {
    throwError("MDMS", 500, "MDMS_DATA_NOT_FOUND_ERROR", `${campaignDetails?.hierarchyType} hierarchy not configured in mdms data 
                ${config.values.moduleName}.${config.masterNameForSplitBoundariesOn}`)
  }
  return mdmsResponse?.mdms?.[0]?.data?.splitBoundariesOn;
}

function checkIfSourceIsMicroplan(objectWithAdditionalDetails: any): boolean {
  return objectWithAdditionalDetails?.additionalDetails?.source === "microplan";
}

function createIdRequests(employees: any[]): any[] {
  if (employees && Array.isArray(employees) && employees.length > 0) {
    const { tenantId } = employees[0]; // Assuming all employees have the same tenantId
    return Array.from({ length: employees.length }, () => ({
      tenantId: tenantId,
      idName: config?.values?.idgen?.idNameForUserNameGeneration,
      format: config?.values?.idgen?.formatForUserName,
    }));
  } else {
    return [];
  }
}

async function createUniqueUserNameViaIdGen(idRequests: any, requestInfo?: RequestInfo) {
  const idgenurl = config?.host?.idGenHost + config?.paths?.idGen;
  try {
    const result = await httpRequest(
      idgenurl,
      { RequestInfo: requestInfo, idRequests },
      undefined,
      undefined,
      undefined,
      undefined
    );

    return result;
  } catch (error: any) {
    logger.error(`Error during ID generation: ${error.message}`);

    throwError(
      "ID_GENERATION",
      500,
      "ID_GENERATION_FAILED",
      `Error occurred while generating ID: ${error.message}`
    );
  }
}

async function processFetchMicroPlan(request: any) {
  try {
    logger.info("Waiting for 1 second for templates to get generated...");
    await new Promise((resolve) => setTimeout(resolve, 1000));

    logger.info("Started processing fetch microplan");

    const { tenantId } = request.body.MicroplanDetails;
    const localizationMap = await getLocalizedMessagesHandler(request, tenantId);
    const resources: CampaignResource[] = request?.body?.CampaignDetails?.resources || [];
    const filteredResources = resources.filter(
      (obj: CampaignResource) => obj?.filestoreId && obj?.resourceId
    );

    logger.info(`Filtered resources with valid IDs: ${filteredResources?.length}`);
    logger.debug(`Filtered resources: ${getFormattedStringForDebug(filteredResources)}`);

    const fetchOperations: Promise<void>[] = [];

    if (filteredResources.length === 0 || filteredResources.every((obj: any) => obj?.type !== "facility")) {
      fetchOperations.push(fetchFacilityData(request, localizationMap));
    }
    if (filteredResources.length === 0 || filteredResources.every((obj: any) => obj?.type !== "boundaryWithTarget")) {
      fetchOperations.push(fetchTargetData(request, localizationMap));
    }
    if (filteredResources.length === 0 || filteredResources.every((obj: any) => obj?.type !== "user")) {
      fetchOperations.push(fetchUserData(request, localizationMap));
    }

    await Promise.all(fetchOperations);

    logger.info("Updating campaign object after successful fetch microplan...");
    await updateCampaignAfterSearch(request, "MICROPLAN_COMPLETED");

    logger.info("Completed processing fetch microplan");
  } catch (error: any) {
    logger.error(`Error during microplan fetch: ${error.message}`);
    await updateCampaignAfterSearch(request, "MICROPLAN_FETCH_FAILED");
  }
}


async function updateCampaignAfterSearch(request: any, source = "MICROPLAN_FETCHING") {
  logger.info("search campaign with id ")
  const { tenantId, campaignId } = request.body.MicroplanDetails;
  const campaignDetails = {
    tenantId: tenantId,
    ids: [campaignId]
  }
  const searchedCampaignResponse = await searchProjectTypeCampaignService(campaignDetails)
  const searchedCamapignObject = searchedCampaignResponse?.CampaignDetails;
  if (Array.isArray(searchedCamapignObject) && searchedCamapignObject.length > 0) {
    const newRequestBody = {
      RequestInfo: request.body.RequestInfo,
      CampaignDetails: searchedCamapignObject?.[0]
    };
    const req: any = replicateRequest(request, newRequestBody)
    logger.info("Updating the received campaign object, source & its key");
    if (!req.body.CampaignDetails.additionalDetails) {
      req.body.CampaignDetails.additionalDetails = {};
    }
    req.body.CampaignDetails.additionalDetails.activity = source;
    (!req.body?.CampaignDetails?.additionalDetails?.["disease"]) && (req.body.CampaignDetails.additionalDetails["disease"] = "MALARIA"),
      (!req.body?.CampaignDetails?.additionalDetails?.["beneficiaryType"]) && (req.body.CampaignDetails.additionalDetails["beneficiaryType"] =
        "INDIVIDUAL");
    req.body.CampaignDetails.additionalDetails["key"] = 1;
    logger.debug(
      `updated object with new source , disease & beneficiarytype ${getFormattedStringForDebug(req.body.CampaignDetails)}`
    );
    await updateProjectTypeCampaignService(req);
    logger.info("Updated the received campaign object");
  } else {
    throwError("CAMPAIGN", 500, "CAMPAIGN_SEARCH_ERROR", "Error in campaign search");
  }
}

/**
 * Recursively build a flat map of boundary code to boundary type across the boundary tree.
 */
export function getBoundaryCodeAndBoundaryTypeMapping(boundaries: any, currentMapping: any = {}) {
  for (const boundary of boundaries) {
    currentMapping[boundary.code] = boundary.boundaryType;
    if (boundary.children?.length > 0) {
      getBoundaryCodeAndBoundaryTypeMapping(boundary.children, currentMapping);
    }
  }
  return currentMapping;
}

/**
 * Validate uploaded usernames for allowed-character format and flag duplicate usernames as INVALID rows.
 */
export function validateUsernamesFormat(data: any[], localizationMap: any) {
  if (!data?.length) return [];

  const userSheet = getLocalizedName(createAndSearch?.user?.parseArrayConfig?.sheetName, localizationMap);
  const errors: any[] = [];
  const userNameColumn = getLocalizedName("UserName", localizationMap);

  const usernameMap = new Map<string, number[]>(); // username -> rowNumbers[]

  data.forEach((item: any) => {
    const rowNumber = item?.["!row#number!"];
    const username = item[userNameColumn];

    const isValid = /^[A-Za-z][A-Za-z0-9-]*$/.test(username);
    if (username && !isValid) {
      errors.push({
        status: "INVALID",
        rowNumber,
        sheetName: userSheet,
        errorDetails: "Invalid username format User name can only be alphanumeric",
      });
    }

    if (username) {
      if (!usernameMap.has(username)) {
        usernameMap.set(username, []);
      }
      usernameMap.get(username)!.push(rowNumber);
    }
  });

  usernameMap.forEach((rows, username) => {
    if (rows.length > 1) {
      rows.forEach((rowNumber) => {
        errors.push({
          status: "INVALID",
          rowNumber,
          sheetName: userSheet,
          errorDetails: `Duplicate username '${username}'`,
        });
      });
    }
  });

  return errors;
}

/**
 * Return whether the campaign was created from a microplan source (checked directly in DB).
 */
export async function isCampaignIdOfMicroplan(tenantId: string, campaignId: string) {
  try {
    const tableName = getTableName(config.DB_CONFIG.DB_CAMPAIGN_DETAILS_TABLE_NAME, tenantId);
    const query = `SELECT id FROM ${tableName} WHERE id = $1 and tenantId = $2 and additionalDetails->>'source' = 'microplan'`;
    const result = await executeQuery(query, [campaignId, tenantId]);
    return Array.isArray(result?.rows) && result?.rows?.length > 0;
  } catch (e) {
    console.log(e);
    logger.error(`Error checking if campaign id ${campaignId} is of microplan: ${e}`);
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error checking if campaign id is of microplan");
    return false;
  }
}

/**
 * Fetch a campaign by tenantId + campaignId, throwing typed errors when inputs are missing or none is found.
 */
export async function validateAndFetchCampaign(request: any) {
  const { tenantId, campaignId } = request.body.CampaignDetails;
  if (!tenantId || !campaignId) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "tenantId and campaignId are required");
  }

  const searchCriteria = {
    tenantId,
    ids: [campaignId]
  };

  const campaignResponse = await searchProjectTypeCampaignService(searchCriteria, request);

  if (campaignResponse?.CampaignDetails?.length === 0) {
    throwError("COMMON", 404, "CAMPAIGN_NOT_FOUND", "Campaign not found");
  }

  return campaignResponse.CampaignDetails[0];
}

/**
 * Mark a campaign cancelled/inactive and produce the update message to persist the cancellation.
 */
export async function prepareAndProduceCancelMessage(campaignToUpdate: any, requestInfo: RequestInfo, request: any) {
  const tenantId = request.body.CampaignDetails.tenantId;
  campaignToUpdate.isActive = false;
  campaignToUpdate.status = campaignStatuses.cancelled;
  campaignToUpdate.campaignDetails = campaignToUpdate.campaignDetails || {};
  campaignToUpdate.parentId = campaignToUpdate.parentId || null;
  campaignToUpdate.auditDetails.lastModifiedTime = Date.now();
  campaignToUpdate.auditDetails.lastModifiedBy = request?.body?.RequestInfo?.userInfo?.uuid;

  const topic = config.kafka.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC;
  const produceMessage = {
    RequestInfo: requestInfo,
    CampaignDetails: campaignToUpdate
  };

  await produceModifiedMessages(produceMessage, topic, tenantId);

  return campaignToUpdate;
}

export {
  generateProcessedFileAndPersist,
  convertToTypeData,
  searchProjectCampaignResourcData,
  processDataSearchRequest,
  processBasedOnAction,
  appendSheetsToWorkbook,
  generateFilteredBoundaryData,
  generateHierarchy,
  createBoundaryMap,
  convertSheetToDifferentTabs,
  getBoundaryDataAfterGeneration,
  enrichAndPersistCampaignWithError,
  getLocalizedName,
  reorderBoundaries,
  reorderBoundariesOfDataAndValidate,
  getTargetBoundariesRelatedToCampaignId,
  getFiltersFromCampaignSearchResponse,
  getConfigurableColumnHeadersBasedOnCampaignType,
  getFinalValidHeadersForTargetSheetAsPerCampaignType,
  getDifferentTabGeneratedBasedOnConfig,
  checkIfSourceIsMicroplan,
  getBoundaryOnWhichWeSplit,
  createIdRequests,
  createUniqueUserNameViaIdGen,
  getRootBoundaryCode,
  getResourceDetails,
  enrichInnerCampaignDetails,
  processFetchMicroPlan,
  updateCampaignAfterSearch,
  processBoundary,
  userCredGeneration,
  getResourceStatusMap,
  updateResourceStatus,
  updateResourceDetails,
  persistCampaignUpdate,
  hasResourceOfType,
};

/**
 * Get the resource statuses map from campaign additionalDetails.
 */
function getResourceStatusMap(campaignDetails: any): Record<string, string> {
  return campaignDetails?.additionalDetails?.resourceStatuses || {};
}

/**
 * Update the status of a specific resource type in campaign additionalDetails.
 * Persists the updated campaign to the database via Kafka.
 */
async function updateResourceStatus(
  campaignDetails: any,
  resourceType: string,
  status: string,
  tenantId: string,
  useruuid: string
): Promise<void> {
  if (!campaignDetails.additionalDetails) {
    campaignDetails.additionalDetails = {};
  }
  if (!campaignDetails.additionalDetails.resourceStatuses) {
    campaignDetails.additionalDetails.resourceStatuses = {};
  }
  campaignDetails.additionalDetails.resourceStatuses[resourceType] = status;
  logger.info(`Updated resource status: ${resourceType} -> ${status}`);
}

/**
 * Bulk-update all active toCreate resource detail rows for a campaign to completed.
 * Used after unified template child campaign processing finishes to close out
 * resource rows that were copied from the parent and never go through the task system.
 */
export async function markAllToCreateResourcesAsCompleted(
  campaignId: string,
  tenantId: string,
  userUuid: string
): Promise<void> {
  const tableName = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  const now = Date.now();
  await executeQuery(
    `UPDATE ${tableName} SET status = $1, lastmodifiedby = $2, lastmodifiedtime = $3
     WHERE campaignid = $4 AND tenantid = $5 AND status = $6 AND isactive = true`,
    [resourceStatuses.completed, userUuid, now, campaignId, tenantId, resourceStatuses.toCreate]
  );
  logger.info(`Marked all toCreate resource details as completed for campaign ${campaignId}`);
}

/**
 * Update per-resource status fields on a specific resource entry.
 * Also keeps additionalDetails.resourceStatuses in sync for backward compatibility.
 */
function updateResourceDetails(
  campaignDetails: any,
  resourceEntry: any,
  updates: { status?: string; processedFileStoreId?: string; error?: string; errorMessage?: string }
): void {
  Object.assign(resourceEntry, updates);
  if (updates.status) {
    if (!campaignDetails.additionalDetails) {
      campaignDetails.additionalDetails = {};
    }
    if (!campaignDetails.additionalDetails.resourceStatuses) {
      campaignDetails.additionalDetails.resourceStatuses = {};
    }
    campaignDetails.additionalDetails.resourceStatuses[resourceEntry.type] = updates.status;
    logger.info(`Updated resource details for type ${resourceEntry.type}: status=${updates.status}`);
  }
}

/**
 * Persist campaign update to DB via Kafka without modifying campaign status.
 */
async function persistCampaignUpdate(campaignDetails: any, requestInfo: RequestInfo): Promise<void> {
  campaignDetails.campaignDetails = {
    deliveryRules: campaignDetails?.deliveryRules || [],
    boundaries: campaignDetails?.boundaries || [],
  };
  campaignDetails.auditDetails = {
    ...campaignDetails.auditDetails,
    lastModifiedTime: Date.now(),
    lastModifiedBy: requestInfo?.userInfo?.uuid,
  };
  const produceMessage: any = {
    RequestInfo: requestInfo,
    CampaignDetails: campaignDetails,
  };
  await produceModifiedMessages(
    produceMessage,
    config?.kafka?.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
    campaignDetails?.tenantId
  );
}

/**
 * Check if campaign has a resource of the given type.
 */
function hasResourceOfType(campaignDetails: any, resourceType: string): boolean {
  return (campaignDetails?.resources || []).some((r: any) => r?.type === resourceType);
}
