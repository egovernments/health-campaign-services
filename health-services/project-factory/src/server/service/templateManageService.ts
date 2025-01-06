import { formatFirstRow, getNewExcelWorkbook } from "../utils/excelUtils";
import { createReadMeSheet, getLocalizedHeaders, getLocalizedMessagesHandler, handledropdownthings, hideUniqueIdentifierColumn, setDropdownFromSchema } from "../utils/genericUtils";
import { enrichCreateTemplateRequest, searchTemplateUtil } from "../utils/templateUtils";
import { headingMapping } from "../config/constants";
import { getLocalizedName } from "../utils/campaignUtils";
import config from "../config";
import { logger } from "../utils/logger";
import createAndSearch from "../config/createAndSearch";
import { callMdmsTypeSchema } from "../api/genericApis";
import { getBoundaryTabName } from "../utils/boundaryUtils";
// import { logger } from "../utils/logger";

export async function searchTemplateService(templateSearchCriteria: any
) {
    // Search for project campaign resource data
    const responseData: any = await searchTemplateUtil(templateSearchCriteria);
    const responseBody: any = { Templates: responseData }
    return responseBody;
};

export async function createTemplateService(requestBody: any) {
    enrichCreateTemplateRequest(requestBody);
    const localizationMap = await getLocalizedMessagesHandler(requestBody, requestBody?.template?.tenantId);
    const schema = await callMdmsTypeSchema(requestBody, requestBody?.template?.tenantId, false, requestBody?.template?.type, requestBody?.template?.campaignType);
    const workbook = getNewExcelWorkbook();
    const headingInSheet = headingMapping?.[requestBody?.template?.type];
    const localisedHeading = getLocalizedName(headingInSheet, localizationMap);
    await createReadMeSheet(requestBody, workbook, localisedHeading, localizationMap);
    const localizedUserTab = getLocalizedName(config?.user?.userTab, localizationMap);
    const userSheet = workbook.addWorksheet(localizedUserTab);
    const headers = schema?.columns;
    const localizedHeaders = getLocalizedHeaders(headers, localizationMap);
    console.log(localizedHeaders)
    const firstRow = userSheet.getRow(1);
    formatFirstRow(firstRow, userSheet, '93C47D', 40, true);
    hideUniqueIdentifierColumn(userSheet, createAndSearch?.[requestBody?.template?.type]?.uniqueIdentifierColumn);
    setDropdownFromSchema(requestBody, schema, localizationMap);
    let receivedDropdowns = requestBody?.dropdowns;
    logger.info("started adding dropdowns in user", JSON.stringify(receivedDropdowns))
    await handledropdownthings(userSheet, receivedDropdowns);
    const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
    const boundarySheet = workbook.addWorksheet(localizedBoundaryTab);
    const firstRowOfBoundarySheet = boundarySheet.getRow(1);
    console.log(firstRowOfBoundarySheet)


}