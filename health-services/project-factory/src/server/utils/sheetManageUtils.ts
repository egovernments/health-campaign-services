import { v4 as uuidV4 } from "uuid";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import * as ExcelJS from "exceljs";
import { callMdmsSchema, createAndUploadFileWithOutRequest, getJsonData, getSheetDataFromWorksheet } from "../api/genericApis";
import { getLocalizedHeaders, getLocalizedMessagesHandlerViaLocale, handledropdownthings, searchAllGeneratedResources } from "./genericUtils";
import { getLocalisationModuleName } from "./localisationUtils";
import { getLocalizedName } from "./campaignUtils";
import { adjustRowHeight, enrichTemplateMetaData, freezeUnfreezeColumns, getExcelWorkbookFromFileURL, getLocaleFromWorkbook, manageMultiSelect, updateFontNameToRoboto } from "./excelUtils";
import * as path from 'path';
import { ColumnProperties, SheetMap } from "../models/SheetMap";
import { logger } from "./logger";
import { generatedResourceStatuses, resourceDetailsStatuses } from "../config/constants";
import fs from 'fs';
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { fetchFileFromFilestore } from "../api/coreApis";

export async function initializeGenerateAndGetResponse(
    tenantId: string,
    type: string,
    hierarchyType: string,
    campaignId: string,
    userUuid: string,
    locale: string = config.localisation.defaultLocale
) {
    const currentTime = Date.now();

    const getResourcesByStatus = (status: string) =>
        searchAllGeneratedResources({ tenantId, type, hierarchyType, status, campaignId }, locale);

    const [completed, inProgress]: any = await Promise.all([
        getResourcesByStatus(generatedResourceStatuses.completed),
        getResourcesByStatus(generatedResourceStatuses.inprogress),
    ]);

    const expiredResources = [...markAsExpired(completed, currentTime, userUuid), ...markAsExpired(inProgress, currentTime, userUuid)];

    if (expiredResources.length > 0) {
        await produceModifiedMessages(
            { generatedResource: expiredResources },
            config.kafka.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC
        );
    }

    const newResource = {
        id: uuidV4(),
        tenantId,
        type,
        hierarchyType,
        campaignId,
        locale,
        status: generatedResourceStatuses.inprogress,
        additionalDetails: {},
        auditDetails: {
            createdTime: currentTime,
            lastModifiedTime: currentTime,
            createdBy: userUuid,
            lastModifiedBy: userUuid,
        },
    };

    await produceModifiedMessages(
        { generatedResource: [newResource] },
        config.kafka.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC
    );

    return newResource;
}

export async function initializeProcessAndGetResponse(
    ResourceDetails: ResourceDetails,
    userUuid: string,
    templateConfig: any,
    locale: string = config.localisation.defaultLocale
) {
    const currentTime = Date.now();
    const newResourceDetails = {
        id: uuidV4(),
        ...ResourceDetails,
        locale,
        status: generatedResourceStatuses.inprogress,
        additionalDetails: {},
        auditDetails: {
            createdTime: currentTime,
            lastModifiedTime: currentTime,
            createdBy: userUuid,
            lastModifiedBy: userUuid,
        },
    };

    const persistMessage: any = { ResourceDetails: newResourceDetails };
    await produceModifiedMessages(persistMessage, config?.kafka?.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC); 
    
    processResource(newResourceDetails, templateConfig, locale);
    return newResourceDetails;
}


const markAsExpired = (resources: any[], currentTime: number, userUuid: string) =>
    resources.map((resource) => {
        const audit = resource.auditDetails || {};
        return {
            ...resource,
            status: generatedResourceStatuses.expired,
            count: null,
            auditDetails: {
                createdTime: parseInt(audit.createdTime ?? `${currentTime}`),
                lastModifiedTime: currentTime,
                createdBy: audit.createdBy ?? userUuid,
                lastModifiedBy: userUuid,
            },
        };
    });



export async function generateResource(responseToSend: any, templateConfig: any) {
    try {
        const localizationMapHierarchy = responseToSend?.hierarchyType && await getLocalizedMessagesHandlerViaLocale(responseToSend?.locale, responseToSend?.tenantId, getLocalisationModuleName(responseToSend?.hierarchyType), true);
        const localizationMapModule = await getLocalizedMessagesHandlerViaLocale(responseToSend?.locale, responseToSend?.tenantId);
        const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
        const workBook = await createBasicTemplateViaConfig(responseToSend, templateConfig, localizationMap);
        enrichTemplateMetaData(workBook, responseToSend?.locale, responseToSend?.campaignId);
        const fileResponse = await createAndUploadFileWithOutRequest(workBook, responseToSend?.tenantId);
        responseToSend.fileStoreid = fileResponse?.[0]?.fileStoreId;
        if (!responseToSend.fileStoreid) throw new Error("FileStoreId not created.");
        responseToSend.status = generatedResourceStatuses.completed;
        await produceModifiedMessages({ generatedResource: [responseToSend] }, config?.kafka?.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC);
    } catch (error) {
        console.log(error)
        await handleErrorDuringGenerate(responseToSend, error);
    }
}

async function processResource(ResoureDetails: any, templateConfig: any, locale: string) {
    try {
        const fileUrl = await fetchFileFromFilestore(ResoureDetails?.fileStoreId, ResoureDetails?.tenantId);
        const workBook = await getExcelWorkbookFromFileURL(fileUrl);
        locale = getLocaleFromWorkbook(workBook) || "";
        if(!locale){
            throw new Error("Locale not found in the file metadata.");
        }
        const localizationMapHierarchy = ResoureDetails?.hierarchyType && await getLocalizedMessagesHandlerViaLocale(locale, ResoureDetails?.tenantId, getLocalisationModuleName(ResoureDetails?.hierarchyType), true);
        const localizationMapModule = await getLocalizedMessagesHandlerViaLocale(locale, ResoureDetails?.tenantId);
        const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
        await processRequest(ResoureDetails, workBook, templateConfig, localizationMap);
        await produceModifiedMessages({ ResoureDetails}, config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC);
    } catch (error) {
        console.log(error)
        await handleErrorDuringProcess(ResoureDetails, error);
    }
}

async function processRequest(ResoureDetails: any, workBook: any, templateConfig: any, localizationMap: any) {
    const wholeSheetData: any = {};
    for (const sheet of templateConfig?.sheets || []) {
        const sheetName = getLocalizedName(sheet?.sheetName, localizationMap);
        const worksheet = workBook.getWorksheet(sheetName);
        const sheetData = getSheetDataFromWorksheet(worksheet);
        const jsonData = getJsonData(sheetData, true);
        wholeSheetData[sheetName] = jsonData;
    }
    const className = `${ResoureDetails?.type}-processClass`;
    let classFilePath = path.join(__dirname, '..', 'processFlowClasses', `${className}.js`);
    if (!fs.existsSync(classFilePath)) {
        // fallback for local dev with ts-node
        classFilePath = path.join(__dirname, '..', 'processFlowClasses', `${className}.ts`);
    }
    try {
        const { TemplateClass } = await import(classFilePath);
        const sheetMap: SheetMap = await TemplateClass.process(ResoureDetails, wholeSheetData, localizationMap);
        mergeSheetMapAndSchema(sheetMap, templateConfig, localizationMap);
        for (const sheet of templateConfig?.sheets) {
            const sheetName = getLocalizedName(sheet?.sheetName, localizationMap);
            const sheetData: any = sheetMap?.[sheetName];
            const worksheet = getOrCreateWorksheet(workBook, sheetName);
            await fillSheetMapInWorkbook(worksheet, sheetData);
            const schema = sheet?.schema;
            const columnsToFreeze = Object.keys(sheetData?.dynamicColumns || {}).filter(
                (columnName) => sheetData.dynamicColumns[columnName]?.freezeColumn
            );
            const columnsToUnFreezeTillData = Object.keys(sheetData?.dynamicColumns || {}).filter(
                (columnName) => sheetData.dynamicColumns[columnName]?.unFreezeColumnTillData
            )
            freezeUnfreezeColumns(worksheet, getLocalizedHeaders(columnsToFreeze, localizationMap), getLocalizedHeaders(columnsToUnFreezeTillData, localizationMap));
            manageMultiSelect(worksheet, schema, localizationMap);
            await handledropdownthings(worksheet, schema, localizationMap);
            updateFontNameToRoboto(worksheet);
        }
    } catch (error) {
        logger.error(`Error importing or calling process function from ${classFilePath}`);
        console.error(error);
        throw error;
    }
}

async function handleErrorDuringGenerate(responseToSend: any, error: any) {
    responseToSend.status = generatedResourceStatuses.failed, responseToSend.additionalDetails = {
        ...responseToSend.additionalDetails,
        error: {
            status: error.status,
            code: error.code,
            description: error.description,
            message: error.message
        }
    }
    await produceModifiedMessages({ generatedResource: [responseToSend] }, config?.kafka?.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC);
}

async function handleErrorDuringProcess(ResoureDetails: any, error: any) {
    ResoureDetails.status = resourceDetailsStatuses.failed, ResoureDetails.additionalDetails = {
        ...ResoureDetails.additionalDetails,
        error: {
            status: error.status,
            code: error.code,
            description: error.description,
            message: error.message
        }
    }
    await produceModifiedMessages({ ResoureDetails}, config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC);
}

async function createBasicTemplateViaConfig(responseToSend: any, templateConfig: any, localizationMap: any) {
    const newWorkbook = new ExcelJS.Workbook();
    const tenantId = responseToSend?.tenantId;
    for (const sheet of templateConfig?.sheets) {
        const schemaName = sheet?.schemaName;
        const schema = await callMdmsSchema(tenantId, schemaName);
        sheet.schema = schema;
    }
    if (templateConfig?.generation) {
        const className = `${responseToSend?.type}-generateClass`;
        let classFilePath = path.join(__dirname, '..', 'generateFlowClasses', `${className}.js`);
        if (!fs.existsSync(classFilePath)) {
            // fallback for local dev with ts-node
            classFilePath = path.join(__dirname, '..', 'generateFlowClasses', `${className}.ts`);
        }
        try {
            const { TemplateClass } = await import(classFilePath);
            const sheetMap: SheetMap = await TemplateClass.generate(templateConfig, responseToSend, localizationMap);
            mergeSheetMapAndSchema(sheetMap, templateConfig, localizationMap);
            for (const sheet of templateConfig?.sheets) {
                const sheetName = getLocalizedName(sheet?.sheetName, localizationMap);
                logger.info(`Generating sheet ${sheetName}`);
                const sheetData: any = sheetMap?.[sheetName];
                const worksheet = getOrCreateWorksheet(newWorkbook, sheetName);
                await fillSheetMapInWorkbook(worksheet, sheetData);
                const schema = sheet?.schema;
                const columnsToFreeze = Object.keys(sheetData?.dynamicColumns || {}).filter(
                    (columnName) => sheetData.dynamicColumns[columnName]?.freezeColumn
                );
                const columnsToUnFreezeTillData = Object.keys(sheetData?.dynamicColumns || {}).filter(
                    (columnName) => sheetData.dynamicColumns[columnName]?.unFreezeColumnTillData
                )
                const columnsToFreezeColumnIfFilled = Object.keys(sheetData?.dynamicColumns || {}).filter(
                    (columnName) => sheetData.dynamicColumns[columnName]?.freezeColumnIfFilled
                )
                freezeUnfreezeColumns(worksheet, getLocalizedHeaders(columnsToFreeze, localizationMap), getLocalizedHeaders(columnsToUnFreezeTillData, localizationMap), getLocalizedHeaders(columnsToFreezeColumnIfFilled, localizationMap));
                manageMultiSelect(worksheet, schema, localizationMap);
                await handledropdownthings(worksheet, schema, localizationMap);
                updateFontNameToRoboto(worksheet);
                logger.info(`Sheet ${sheetName} generated successfully`);
            }
        } catch (error) {
            logger.error(`Error importing or calling generate function from ${classFilePath}`);
            console.error(error);
            throw error;
        }
    }
    else {
        logger.info(`Template generation skipped for ${responseToSend?.type} according to the config.`);
    }
    return newWorkbook;
}

function mergeSheetMapAndSchema(sheetMap: SheetMap, templateConfig: any, localizationMap: any) {
    const reverseLocalizationMap = new Map<string, string>();
    const localizationCache = new Map<string, string>();

    for (const [key, value] of Object.entries(localizationMap)) {
        reverseLocalizationMap.set(String(value), key);
    }

    const sheetsProcessed = new Set<string>();

    // Preprocess sheets in templateConfig
    for (const sheet of templateConfig?.sheets || []) {
        const rawSheetName = sheet?.sheetName;
        const localizedSheetName = localizationCache.get(rawSheetName)
            || getLocalizedName(rawSheetName, localizationMap);
        localizationCache.set(rawSheetName, localizedSheetName);

        const dynamicCols = mergeAndGetDynamicColumns(
            sheetMap?.[localizedSheetName]?.dynamicColumns ?? null,
            sheet?.schema,
            localizationMap
        );

        sheetMap[localizedSheetName] = sheetMap[localizedSheetName] || { data: [] };
        sheetMap[localizedSheetName].dynamicColumns = dynamicCols;

        sheetsProcessed.add(localizedSheetName);
    }

    // Post-process unmatched sheetMap entries
    for (const [sheetName, sheetData] of Object.entries(sheetMap)) {
        if (sheetsProcessed.has(sheetName)) continue;

        const dynamicCols = mergeAndGetDynamicColumns(sheetData?.dynamicColumns, {}, localizationMap);
        sheetMap[sheetName].dynamicColumns = dynamicCols;

        const currentSchema: any = { properties: {} };
        for (const column of Object.keys(dynamicCols)) {
            const originalKey = reverseLocalizationMap.get(column) || column;
            currentSchema.properties[originalKey] = dynamicCols[column];
        }

        const rawSheetName = reverseLocalizationMap.get(sheetName) || sheetName;

        templateConfig.sheets.push({
            sheetName: rawSheetName,
            schema: currentSchema
        });

        sheetsProcessed.add(sheetName);
    }
}



function mergeAndGetDynamicColumns(dynamicColumns: any, schema: any, localizationMap: any): any {
    if (!dynamicColumns) dynamicColumns = {};

    let maxOrderNumber = Number.MAX_SAFE_INTEGER;

    const assignProps = (target: any, source: any, isMulti = false, index = 0, parentOrder = 0) => {
        target.width ??= source.width ?? 40;
        target.color ??= source.color;
        target.adjustHeight ??= source.adjustHeight;
        target.freezeColumnIfFilled ??= source.freezeColumnIfFilled;
        target.showInProcessed ??= source.showInProcessed;
        target.unFreezeColumnTillData ??= source.unFreezeColumnTillData;

        if (!isMulti) {
            target.freezeColumn ??= source.freezeColumn;
            target.hideColumn ??= source.hideColumn;
            target.orderNumber ??= source.orderNumber ?? maxOrderNumber;
        } else {
            const order = parentOrder - 1 + (0.01 * index);
            target.orderNumber ??= order;
        }

        maxOrderNumber = Math.min(maxOrderNumber, target.orderNumber);
    };

    for (const propertyKey in schema?.properties) {
        const property = schema.properties[propertyKey];
        const localizedKey = getLocalizedName(propertyKey, localizationMap);

        // Process multiSelectDetails if any
        if (property?.multiSelectDetails?.maxSelections) {
            const max = property.multiSelectDetails.maxSelections;
            const parentOrder = dynamicColumns[localizedKey]?.orderNumber ?? property.orderNumber ?? maxOrderNumber;

            for (let i = 1; i <= max; i++) {
                const multiKey = `${propertyKey}_MULTISELECT_${i}`;
                const localizedMultiKey = getLocalizedName(multiKey, localizationMap);
                dynamicColumns[localizedMultiKey] ??= {};
                assignProps(dynamicColumns[localizedMultiKey], property, true, i, parentOrder);
            }
        }

        // Process base column
        dynamicColumns[localizedKey] ??= {};
        assignProps(dynamicColumns[localizedKey], property);
    }

    // Return sorted by orderNumber
    return Object.fromEntries(
        Object.entries(dynamicColumns).sort(([, a]: any, [, b]: any) => a.orderNumber - b.orderNumber)
    );
}




async function fillSheetMapInWorkbook(worksheet: ExcelJS.Worksheet, sheetData: any) {
    const columnNameToIndexMap = processDynamicColumns(worksheet, sheetData);
    addDataToWorksheet(worksheet, sheetData, columnNameToIndexMap);
    logger.info(`Added data to sheet ${worksheet.name}`);
}

// Helper functions
function getOrCreateWorksheet(workbook: ExcelJS.Workbook, sheetName: string) {
    let worksheet = workbook.getWorksheet(sheetName);
    if (!worksheet) {
        worksheet = workbook.addWorksheet(sheetName);
    }
    return worksheet;
}

function processDynamicColumns(worksheet: ExcelJS.Worksheet, sheetData: any) {
    const columnNameToIndexMap: Record<string, number> = {};

    if (!sheetData.dynamicColumns) return columnNameToIndexMap;

    const headerRow = worksheet.getRow(1);

    Object.keys(sheetData.dynamicColumns).forEach((columnName, index) => {
        const columnIndex = index + 1;
        columnNameToIndexMap[columnName] = columnIndex;

        // Set header cell value
        const headerCell = headerRow.getCell(columnIndex);
        headerCell.value = columnName;

        // Apply column-level props (like width/hidden) and cell-level styles to header
        applyColumnProperties(headerRow, headerCell, sheetData.dynamicColumns[columnName]);
    });

    headerRow.commit(); // Commit the row after updating cells
    worksheet.views = [{ state: 'frozen', ySplit: 1 }];

    return columnNameToIndexMap;
}

function applyColumnProperties(row: ExcelJS.Row, cell: ExcelJS.Cell, columnProps: ColumnProperties) {
    const column = cell.worksheet.getColumn(cell.col);

    // Apply column-level properties like width and hidden
    column.width = columnProps.width ?? 40; // Default width = 40
    if (columnProps.hideColumn !== undefined) column.hidden = columnProps.hideColumn;

    cell.font = { ...cell.font, bold: true };
    adjustRowHeight(row, cell, column.width);

    if (columnProps.color) {
        cell.fill = {
            type: 'pattern',
            pattern: 'solid',
            fgColor: { argb: columnProps.color.replace('#', '') },
        };
    }

    cell.alignment = { horizontal: 'center' };
}



function addDataToWorksheet(
    worksheet: ExcelJS.Worksheet,
    sheetData: any,
    columnNameToIndexMap: Record<string, number>
) {
    // Clear all rows starting from row 2 (preserve first row)
    for (let i = worksheet.rowCount; i > 1; i--) {
        worksheet.spliceRows(i, 1);
    }

    // Insert column headers in row 2
    const headers = Object.keys(columnNameToIndexMap);

    const newRows = sheetData.data.map((rowData: any) =>
        headers.map(columnName => rowData[columnName] ?? '')
    );

    worksheet.insertRows(2, newRows); // Start inserting data from row 2

    applyCellFormatting(worksheet, newRows.length, columnNameToIndexMap, sheetData);
}




function applyCellFormatting(worksheet: ExcelJS.Worksheet, rowCount: number, columnNameToIndexMap: Record<string, number>, sheetData: any) {
    const startRow = 0;

    for (let i = startRow; i <= rowCount + 1; i++) {
        const row = worksheet.getRow(i);
        row.eachCell((cell, colNumber) => {
            cell.alignment = {
                ...cell.alignment,
                wrapText: cell.value ? true : false
            };
            processBoldFormatting(cell);
            adjustRowHeightIfNeeded(row, cell, colNumber, columnNameToIndexMap, sheetData);
        });
        row.commit();
    }
}

function processBoldFormatting(cell: ExcelJS.Cell) {
    if (typeof cell.value !== 'string') return;

    const text = cell.value;
    const boldRanges: { start: number, end: number }[] = [];
    let pos = 0;

    // Find all bold ranges
    while (pos < text.length) {
        const startBold = text.indexOf('**', pos);
        if (startBold === -1) break;

        const endBold = text.indexOf('**', startBold + 2);
        if (endBold === -1) break;

        boldRanges.push({ start: startBold, end: endBold - 1 });
        pos = endBold + 2;
    }

    if (boldRanges.length === 0) return;

    // Process rich text formatting
    const cleanedText = text.replace(/\*\*/g, '');
    const richText: ExcelJS.RichText[] = [];
    boldRanges.sort((a, b) => a.start - b.start);

    for (let i = 0; i < cleanedText.length; i++) {
        const isBold = boldRanges.some(range => i >= range.start && i <= range.end);

        if (i === 0 || isBold !== (richText[richText.length - 1]?.font?.bold || false)) {
            richText.push({ text: cleanedText[i], font: { bold: isBold } });
        } else {
            richText[richText.length - 1].text += cleanedText[i];
        }
    }

    cell.value = { richText };
}

function adjustRowHeightIfNeeded(row: ExcelJS.Row, cell: ExcelJS.Cell, colNumber: number,
    columnNameToIndexMap: Record<string, number>, sheetData: any) {
    if (!sheetData.dynamicColumns) return;

    const columnName = Object.keys(columnNameToIndexMap).find(
        key => columnNameToIndexMap[key] === colNumber
    );

    if (columnName && sheetData.dynamicColumns[columnName]?.adjustHeight) {
        const columnWidth = sheetData.dynamicColumns[columnName]?.width ??
            cell.worksheet.getColumn(colNumber).width ?? 40;
        adjustRowHeight(row, cell, columnWidth);
    }
}







