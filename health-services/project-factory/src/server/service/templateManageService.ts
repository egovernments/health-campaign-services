import { formatFirstRow, getNewExcelWorkbook } from "../utils/excelUtils";
import { createReadMeSheet, getLocalizedHeaders, getLocalizedMessagesHandler, handledropdownthings, hideUniqueIdentifierColumn, setDropdownFromSchema } from "../utils/genericUtils";
import { enrichCreateTemplateRequest, searchTemplateUtil } from "../utils/templateUtils";
import { headingMapping } from "../config/constants";
import { getLocalizedName } from "../utils/campaignUtils";
import config from "../config";
import { logger } from "../utils/logger";
import createAndSearch from "../config/createAndSearch";
import { callMdmsTypeSchema, createAndUploadFile } from "../api/genericApis";
import { getBoundaryTabName } from "../utils/boundaryUtils";
// import { getHierarchy } from "server/api/campaignApis";
import { produceModifiedMessages } from "../kafka/Producer";
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
    const { template } = requestBody;
    const { type, tenantId, locale } = template;

    const localizationMap = await getLocalizedMessagesHandler(requestBody, tenantId, undefined, undefined, locale);
    const workbook = getNewExcelWorkbook();

    const localizedHeading = getLocalizedName(headingMapping?.[type], localizationMap);
    await createReadMeSheet(requestBody, workbook, localizedHeading, localizationMap);

    if (type !== config?.boundary?.boundaryType) {
        const schema = await callMdmsTypeSchema(requestBody, tenantId, false, type);
        const localizedMainSheetTab = type === config?.facility?.facilityType
            ? getLocalizedName(config?.facility?.facilityTab, localizationMap)
            : getLocalizedName(config?.user?.userTab, localizationMap);

        const mainSheet = workbook.addWorksheet(localizedMainSheetTab);
        const localizedHeaders = getLocalizedHeaders(schema?.columns, localizationMap);
        mainSheet.addRow(localizedHeaders);

        const firstRow = mainSheet.getRow(1);
        formatFirstRow(firstRow, mainSheet, '93C47D', 40, true);
        hideUniqueIdentifierColumn(mainSheet, createAndSearch?.[type]?.uniqueIdentifierColumn);
        setDropdownFromSchema(requestBody, schema, localizationMap);

        logger.info("Adding dropdowns", JSON.stringify(requestBody?.dropdowns));
        await handledropdownthings(mainSheet, requestBody?.dropdowns);
    }

    const boundarySheet = workbook.addWorksheet(getLocalizedName(getBoundaryTabName(), localizationMap));
    formatFirstRow(boundarySheet.getRow(1), boundarySheet, '93C47D', 40, true);

    const fileDetails = await createAndUploadFile(workbook, requestBody, tenantId);
    template.fileStoreId = fileDetails?.[0]?.fileStoreId;
    const messagePayload: any = {
        templateDetails: template,
    };

    await produceModifiedMessages(
        messagePayload,
        config?.kafka?.KAFKA_CREATE_TEMPLATE_DETAILS_TOPIC
    );
}
