import { v4 as uuidV4 } from "uuid";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import * as ExcelJS from "exceljs";
import { callMdmsSchema, createAndUploadFileWithOutRequest, getJsonDataWithUnlocalisedKey, getSheetDataFromWorksheet } from "../api/genericApis";
import { getLocalizedMessagesHandlerViaLocale, handledropdownthingsUnLocalised, searchAllGeneratedResources, throwError } from "./genericUtils";
import { getLocalisationModuleName } from "./localisationUtils";
import { getLocalizedName } from "./campaignUtils";
import { adjustRowHeight, enrichTemplateMetaData, freezeUnfreezeColumns, getExcelWorkbookFromFileURL, getLocaleFromWorkbook, manageMultiSelectUnlocalised, updateFontNameToRoboto, validateFileCmapaignIdInMetaData } from "./excelUtils";
import * as path from 'path';
import { ColumnProperties, SheetMap } from "../models/SheetMap";
import { logger } from "./logger";
import { generatedResourceStatuses, resourceDetailsStatuses } from "../config/constants";
import fs from 'fs';
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { fetchFileFromFilestore } from "../api/coreApis";
import { EnrichProcessConfigUtil } from "./EnrichProcessConfigUtil";
import { processTemplateConfigs } from "../config/processTemplateConfigs";

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
            config.kafka.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC,
            tenantId
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
        config.kafka.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC,
        tenantId
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
        processedFileStoreId: null,
        action : "process"
    };

    const persistMessage: any = { ResourceDetails: newResourceDetails };
    await produceModifiedMessages(persistMessage, config?.kafka?.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC, ResourceDetails?.tenantId); 
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
        await produceModifiedMessages({ generatedResource: [responseToSend] }, config?.kafka?.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC, responseToSend?.tenantId);
    } catch (error) {
        console.log(error)
        await handleErrorDuringGenerate(responseToSend, error);
    }
}

export async function processResource(ResourceDetails: any, templateConfig: any) {
    try {
        const fileUrl = await fetchFileFromFilestore(ResourceDetails?.fileStoreId, ResourceDetails?.tenantId);
        const workBook = await getExcelWorkbookFromFileURL(fileUrl);
        let locale = getLocaleFromWorkbook(workBook) || "";
        if(!locale){
            throw new Error("Locale not found in the file metadata.");
        }
        const localizationMapHierarchy = ResourceDetails?.hierarchyType && await getLocalizedMessagesHandlerViaLocale(locale, ResourceDetails?.tenantId, getLocalisationModuleName(ResourceDetails?.hierarchyType), true);
        const localizationMapModule = await getLocalizedMessagesHandlerViaLocale(locale, ResourceDetails?.tenantId);
        const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
        await processRequest(ResourceDetails, workBook, templateConfig, localizationMap);
        enrichTemplateMetaData(workBook, locale, ResourceDetails?.campaignId);
        const fileResponse = await createAndUploadFileWithOutRequest(workBook, ResourceDetails?.tenantId);
        ResourceDetails.processedFileStoreId = fileResponse?.[0]?.fileStoreId;
        if (!ResourceDetails.processedFileStoreId) throw new Error("FileStoreId not created.");
        ResourceDetails.status = generatedResourceStatuses.completed;
        await produceModifiedMessages({ ResourceDetails : ResourceDetails }, config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC, ResourceDetails?.tenantId);
    } catch (error) {
        console.log(error)
        await handleErrorDuringProcess(ResourceDetails, error);
    }
}

export function checkAllRowsConsistency(jsonData: any) {
    if (!Array.isArray(jsonData)) return;
    if (!jsonData.length) return;

    const firstRowNumber = jsonData[0]?.["!row#number!"];

    if (firstRowNumber !== 3) {
        throwError("VALIDATION_ERROR", 400, "INVALID_FILE_WITH_GAP", "There should not be any empty gap in sheet rows.");
    }

    let prevRow = firstRowNumber;

    for (let i = 1; i < jsonData.length; i++) {
        const currentRowNumber = jsonData[i]?.["!row#number!"];
        if (currentRowNumber !== prevRow + 1) {
            throwError("VALIDATION_ERROR", 400, "INVALID_FILE_WITH_GAP", "There should not be any empty gap in sheet rows.");
        }
        prevRow = currentRowNumber;
    }
}


export async function processRequest(ResourceDetails: any, workBook: any, templateConfig: any, localizationMap: any) {
    validateFileCmapaignIdInMetaData(workBook, ResourceDetails?.campaignId);
    const wholeSheetData: any = {};
    for (const sheet of templateConfig?.sheets || []) {
        const sheetName = getLocalizedName(sheet?.sheetName, localizationMap);
        const worksheet = workBook.getWorksheet(sheetName);
        const sheetData = getSheetDataFromWorksheet(worksheet);
        const jsonData = getJsonDataWithUnlocalisedKey(sheetData, true);
        if (sheet?.validateRowsGap) checkAllRowsConsistency(jsonData);
        wholeSheetData[sheetName] = jsonData;
        if(!sheet?.schemaName) continue;
        const schema = await callMdmsSchema(ResourceDetails?.tenantId, sheet?.schemaName);
        sheet.schema = schema;
    }
    const className = `${ResourceDetails?.type}-processClass`;
    let classFilePath = path.join(__dirname, '..', 'processFlowClasses', `${className}.js`);
    if (!fs.existsSync(classFilePath)) {
        // fallback for local dev with ts-node
        classFilePath = path.join(__dirname, '..', 'processFlowClasses', `${className}.ts`);
    }
    try {
        const { TemplateClass } = await import(classFilePath);
        const sheetMap: SheetMap = await TemplateClass.process(ResourceDetails, wholeSheetData, localizationMap, templateConfig);
        mergeSheetMapAndSchema(sheetMap, templateConfig, localizationMap);
        for (const sheet of templateConfig?.sheets) {
            const sheetName = sheet?.sheetName;
            if (!sheetMap?.[sheetName]?.data?.length) continue;
            const sheetData: any = sheetMap?.[sheetName];
            const worksheet = getOrCreateWorksheet(workBook, getLocalizedName(sheetName, localizationMap));
            await fillSheetMapInWorkbook(worksheet, sheetData, true, localizationMap);
            const schema = sheet?.schema;
            const columnsToFreeze = Object.keys(sheetData?.dynamicColumns || {})
                .filter((i) => sheetData.dynamicColumns[i]?.[1]?.freezeColumn === true)
                .map((i) => sheetData.dynamicColumns[i]?.[0]);

            const columnsToUnFreezeTillData = Object.keys(sheetData?.dynamicColumns || {})
                .filter((columnName) => sheetData.dynamicColumns[columnName]?.[1]?.unFreezeColumnTillData)
                .map((columnName) => sheetData.dynamicColumns[columnName]?.[0]);

            const columnsToFreezeColumnIfFilled = Object.keys(sheetData?.dynamicColumns || {})
                .filter((columnName) => sheetData.dynamicColumns[columnName]?.[1]?.freezeColumnIfFilled)
                .map((columnName) => sheetData.dynamicColumns[columnName]?.[0]);

            const columnsToFreezeTillData = Object.keys(sheetData?.dynamicColumns || {})
                .filter((columnName) => sheetData.dynamicColumns[columnName]?.[1]?.freezeTillData)
                .map((columnName) => sheetData.dynamicColumns[columnName]?.[0]);

            await freezeUnfreezeColumns(worksheet, columnsToFreeze, columnsToUnFreezeTillData, columnsToFreezeTillData, columnsToFreezeColumnIfFilled);
            manageMultiSelectUnlocalised(worksheet, schema);
            await handledropdownthingsUnLocalised(worksheet, schema);
            updateFontNameToRoboto(worksheet);
        }
        
        await lockSheetAccordingToConfig(workBook, templateConfig, localizationMap);
    } catch (error) {
        logger.error(`Error importing or calling process function from ${classFilePath}`);
        console.error(error);
        throw error;
    }
}

async function lockSheetAccordingToConfig(workBook: any, templateConfig: any, localizationMap: any) {
    for (const sheet of templateConfig?.sheets) {
        if (sheet?.lockWholeSheet) {
            const sheetName = getLocalizedName(sheet?.sheetName, localizationMap);
            const worksheet = getOrCreateWorksheet(workBook, sheetName);
            if (!worksheet) continue;

            logger.info(`Locking sheet ${sheetName}`);

            // Mark all cells as locked
            worksheet.eachRow((row) => {
                row.eachCell({ includeEmpty: true }, (cell) => {
                    cell.protection = { locked: true };
                });
            });

            // Protect sheet with full options
            await worksheet.protect('passwordhere', {
                selectLockedCells: true,
                selectUnlockedCells: false,
                formatCells: false,
                formatColumns: false,
                formatRows: false,
                insertColumns: false,
                insertRows: false,
                insertHyperlinks: false,
                deleteColumns: false,
                deleteRows: false,
                sort: false,
                autoFilter: false,
                pivotTables: false,
            });

            logger.info(`Sheet ${sheetName} locked successfully`);
        }
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
    await produceModifiedMessages({ generatedResource: [responseToSend] }, config?.kafka?.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC, responseToSend?.tenantId);
}

export async function handleErrorDuringProcess(ResourceDetails: any, error: any) {
    ResourceDetails.status = resourceDetailsStatuses.failed, ResourceDetails.additionalDetails = {
        ...ResourceDetails.additionalDetails,
        error: {
            status: error.status,
            code: error.code,
            description: error.description,
            message: error.message
        }
    }
    await produceModifiedMessages({ ResourceDetails: ResourceDetails }, config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC, ResourceDetails?.tenantId);
}

async function createBasicTemplateViaConfig(responseToSend: any, templateConfig: any, localizationMap: any) {
    const newWorkbook = new ExcelJS.Workbook();
    const tenantId = responseToSend?.tenantId;
    for (const sheet of templateConfig?.sheets) {
        const schemaName = sheet?.schemaName;
        const schema = await callMdmsSchema(tenantId, schemaName);
        sheet.schema = schema;
    }
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
            const sheetName = sheet?.sheetName;
            logger.info(`Generating sheet ${sheetName}`);
            const sheetData: any = sheetMap?.[sheetName];
            const worksheet = getOrCreateWorksheet(newWorkbook, getLocalizedName(sheetName, localizationMap));
            await fillSheetMapInWorkbook(worksheet, sheetData, false, localizationMap);
            const schema = sheet?.schema;
            const columnsToFreeze = Object.keys(sheetData?.dynamicColumns || {})
                .filter((i) => sheetData.dynamicColumns[i]?.[1]?.freezeColumn === true)
                .map((i) => sheetData.dynamicColumns[i]?.[0]);

            const columnsToUnFreezeTillData = Object.keys(sheetData?.dynamicColumns || {})
                .filter((columnName) => sheetData.dynamicColumns[columnName]?.[1]?.unFreezeColumnTillData)
                .map((columnName) => sheetData.dynamicColumns[columnName]?.[0]);

            const columnsToFreezeColumnIfFilled = Object.keys(sheetData?.dynamicColumns || {})
                .filter((columnName) => sheetData.dynamicColumns[columnName]?.[1]?.freezeColumnIfFilled)
                .map((columnName) => sheetData.dynamicColumns[columnName]?.[0]);

            const columnsToFreezeTillData = Object.keys(sheetData?.dynamicColumns || {})
                .filter((columnName) => sheetData.dynamicColumns[columnName]?.[1]?.freezeTillData)
                .map((columnName) => sheetData.dynamicColumns[columnName]?.[0]);

            await freezeUnfreezeColumns(worksheet, columnsToFreeze, columnsToUnFreezeTillData, columnsToFreezeTillData, columnsToFreezeColumnIfFilled);
            manageMultiSelectUnlocalised(worksheet, schema);
            await handledropdownthingsUnLocalised(worksheet, schema);
            updateFontNameToRoboto(worksheet);
            await lockSheetAccordingToConfig(newWorkbook, templateConfig, localizationMap);
            logger.info(`Sheet ${sheetName} generated successfully`);
        }
    } catch (error) {
        logger.error(`Error importing or calling generate function from ${classFilePath}`);
        console.error(error);
        throw error;
    }
    return newWorkbook;
}

function mergeSheetMapAndSchema(sheetMap: SheetMap, templateConfig: any, localizationMap: any) {

    const sheetsProcessed = new Set<string>();

    // Preprocess sheets in templateConfig
    for (const sheet of templateConfig?.sheets || []) {
        const rawSheetName = sheet?.sheetName;

        const dynamicCols = mergeAndGetDynamicColumns(
            sheetMap?.[rawSheetName]?.dynamicColumns ?? null,
            sheet?.schema
        );

        sheetMap[rawSheetName] = sheetMap[rawSheetName] || { data: [] };
        sheetMap[rawSheetName].dynamicColumns = dynamicCols;

        sheetsProcessed.add(rawSheetName);
    }

    // Post-process unmatched sheetMap entries
    for (const [sheetName, sheetData] of Object.entries(sheetMap)) {
        if (sheetsProcessed.has(sheetName)) continue;

        const dynamicCols = mergeAndGetDynamicColumns(sheetData?.dynamicColumns, {});
        sheetMap[sheetName].dynamicColumns = dynamicCols;

        const currentSchema: any = { properties: {} };
        for (const column of Object.keys(dynamicCols)) {
            currentSchema.properties[column] = dynamicCols[column];
        }

        templateConfig.sheets.push({
            sheetName: sheetName,
            schema: currentSchema
        });

        sheetsProcessed.add(sheetName);
    }
}



function mergeAndGetDynamicColumns(dynamicColumns: any, schema: any): any {
    if (!dynamicColumns) dynamicColumns = {};

    let maxOrderNumber = Number.MAX_SAFE_INTEGER;

    const assignProps = (target: any, source: any, isMulti = false, index = 0, parentOrder = 0) => {
        target.width ??= source.width ?? 40;
        target.color ??= source.color;
        target.adjustHeight ??= source.adjustHeight;
        target.freezeColumnIfFilled ??= source.freezeColumnIfFilled;
        target.showInProcessed ??= source.showInProcessed;
        target.unFreezeColumnTillData ??= source.unFreezeColumnTillData;
        target.freezeTillData ??= source.freezeTillData;
        target.adjustHeight ??= source.adjustHeight;
        target.wrapText ??= source.wrapText;


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

        // Process multiSelectDetails if any
        if (property?.multiSelectDetails?.maxSelections) {
            const max = property.multiSelectDetails.maxSelections;
            const parentOrder = dynamicColumns[propertyKey]?.orderNumber ?? property.orderNumber ?? maxOrderNumber;

            for (let i = 1; i <= max; i++) {
                const multiKey = `${propertyKey}_MULTISELECT_${i}`;
                dynamicColumns[multiKey] ??= {};
                assignProps(dynamicColumns[multiKey], property, true, i, parentOrder);
            }
        }

        // Process base column
        dynamicColumns[propertyKey] ??= {};
        assignProps(dynamicColumns[propertyKey], property);
    }

    const sortedArray: [string, any][] = Object.entries(dynamicColumns)
        .sort(([, a]: any, [, b]: any) => a.orderNumber - b.orderNumber);
      
    
    return sortedArray;
}




async function fillSheetMapInWorkbook(worksheet: ExcelJS.Worksheet, sheetData: any, isProcessedFile = false, localizationMap: any) {
    const columnNameToIndexMap = processDynamicColumns(worksheet, sheetData, isProcessedFile, localizationMap);
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

function processDynamicColumns(
    worksheet: ExcelJS.Worksheet,
    sheetData: any,
    isProcessedFile = false,
    localizationMap: any
) {
    const columnNameToIndexMap: Record<string, number> = {};

    if (!sheetData.dynamicColumns) return columnNameToIndexMap;

    const keyRow = worksheet.getRow(1);       // Row 1: Original keys
    const headerRow = worksheet.getRow(2);    // Row 2: Localized values

    let columnIndex = 1;

    for (const [columnName, columnConfig] of sheetData?.dynamicColumns) {
        const localisedColumnName = getLocalizedName(columnName, localizationMap);
        columnNameToIndexMap[columnName] = columnIndex;

        // Row 1 - Raw key (hidden)
        const keyCell = keyRow.getCell(columnIndex);
        keyCell.value = columnName;
        keyCell.protection = { locked: true };

        // Row 2 - Localized name
        const headerCell = headerRow.getCell(columnIndex);
        headerCell.value = localisedColumnName;
        headerCell.protection = { locked: true };

        applyColumnProperties(headerRow, headerCell, columnConfig, isProcessedFile);

        columnIndex++;
    }


    // Hide row with original keys
    keyRow.hidden = true;
    keyRow.commit();
    headerRow.commit();

    // Freeze pane below localized header
    worksheet.views = [{ state: 'frozen', ySplit: 2 }];

    return columnNameToIndexMap;
}


function applyColumnProperties(row: ExcelJS.Row, cell: ExcelJS.Cell, columnProps: ColumnProperties, isProcessedFile = false) {
    const column = cell.worksheet.getColumn(cell.col);

    // Apply column-level properties like width and hidden
    column.width = columnProps.width ?? 40; // Default width = 40
    if (columnProps.hideColumn !== undefined) column.hidden = columnProps.hideColumn;
    if( isProcessedFile && columnProps.showInProcessed) {
        column.hidden = false
    }

    cell.font = { ...cell.font, bold: true };
    adjustRowHeight(row, cell, column.width);

    if (columnProps.color) {
        cell.fill = {
            type: 'pattern',
            pattern: 'solid',
            fgColor: { argb: columnProps.color.replace('#', '') },
        };
    }

    cell.alignment = { horizontal: 'center', wrapText: true };
}



function addDataToWorksheet(
    worksheet: ExcelJS.Worksheet,
    sheetData: any,
    columnNameToIndexMap: Record<string, number>
) {
    // Clear all rows starting from row 3 (preserve first and second row)
    for (let i = worksheet.rowCount; i > 2; i--) {
        worksheet.spliceRows(i, 1);
    }

    const headers : any[] = Object.keys(columnNameToIndexMap);

    const newRows = sheetData.data.map((rowData: any) =>
        headers.map(columnName => rowData[columnName] ?? '')
    );

    worksheet.insertRows(3, newRows); // Start inserting data from row 2

    applyCellFormatting(worksheet, newRows.length, columnNameToIndexMap, sheetData);
}



function applyCellFormatting(
    worksheet: ExcelJS.Worksheet,
    rowCount: number,
    columnNameToIndexMap: Record<string, number>,
    sheetData: any
) {
    if (!sheetData?.dynamicColumns) return;

    // 1. Convert dynamicColumns array to map
    const dynamicColumnMap: Record<string, any> = Object.fromEntries(sheetData.dynamicColumns);

    // 2. Precompute colIndex → config map for fast O(1) access
    const columnIndexToConfigMap: Record<number, any> = {};
    for (const [columnName, index] of Object.entries(columnNameToIndexMap)) {
        if (dynamicColumnMap[columnName]) {
            columnIndexToConfigMap[index] = dynamicColumnMap[columnName];
        }
    }

    // 3. Loop through rows and cells
    for (let i = 1; i <= rowCount + 2; i++) {
        const row = worksheet.getRow(i);
        if (!row.hasValues) continue;

        for (let colNumber = 1; colNumber <= row.cellCount; colNumber++) {
            const cell = row.getCell(colNumber);
            if (!cell.value) continue;

            if (i <= 1) {
                cell.alignment = { ...(cell.alignment || {}), wrapText: true };
            }

            processBoldFormatting(cell);
            adjustRowHeightAndWrapIfNeeded(row, cell, colNumber, columnIndexToConfigMap);
        }

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

function adjustRowHeightAndWrapIfNeeded(
    row: ExcelJS.Row,
    cell: ExcelJS.Cell,
    colNumber: number,
    columnIndexToConfigMap: Record<number, any>
) {
    const properties = columnIndexToConfigMap[colNumber];
    if (!properties) return;

    if (properties.adjustHeight) {
        const columnWidth = properties.width ?? cell.worksheet.getColumn(colNumber).width ?? 40;
        adjustRowHeight(row, cell, columnWidth);
    }

    if (properties.wrapText) {
        cell.alignment = { ...(cell.alignment || {}), wrapText: true };
    }
  }
  

export async function enrichProcessTemplateConfig(ResourceDetails: any, processTemplateConfig: any){
    if (processTemplateConfig?.enrichmentFunction){
        const util = new EnrichProcessConfigUtil();
        await util.execute(processTemplateConfig.enrichmentFunction, ResourceDetails, processTemplateConfig);
    }
}

export async function validateResourceDetailsBeforeProcess(validationProcessType : string, resourceDetails: any, localizationMap : any) {
    logger.info("Validating resource details before process main function...");
    const processTemplateConfig = JSON.parse(JSON.stringify(processTemplateConfigs?.[String(validationProcessType)]));
    const validationResourceDetails = {
        type : validationProcessType,
        tenantId : resourceDetails?.tenantId,
        additionalDetails : resourceDetails?.additionalDetails,
        fileStoreId : resourceDetails?.fileStoreId,
        campaignId : resourceDetails?.campaignId,
        hierarchyType : resourceDetails?.hierarchyType
    }
    await enrichProcessTemplateConfig(validationResourceDetails, processTemplateConfig);
    const fileUrl = await fetchFileFromFilestore(validationResourceDetails?.fileStoreId, validationResourceDetails?.tenantId);
    const workBook = await getExcelWorkbookFromFileURL(fileUrl);
    let locale = getLocaleFromWorkbook(workBook) || "";
    if (!locale) {
        throw new Error("Locale not found in the file metadata.");
    }
    await processRequest(validationResourceDetails, workBook, processTemplateConfig, localizationMap);
    if (validationResourceDetails?.additionalDetails?.sheetErrors?.length) {
        throwError("COMMON", 400, "VALIDATION_ERROR", JSON.stringify(validationResourceDetails?.additionalDetails?.sheetErrors));
    }
    logger.info("Validated resource details before process main function...");
}

export function filterResourceDetailType(type : string){
    const templateConfig = JSON.parse(JSON.stringify(processTemplateConfigs?.[String(type)]));
    if(!templateConfig?.passFromController){
        throwError("COMMON", 400, "VALIDATION_ERROR", `Type ${type} not found or invalid`);
    }
}







