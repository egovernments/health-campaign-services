import { v4 as uuidV4 } from "uuid";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import * as ExcelJS from "exceljs";
import { callMdmsSchema, createAndUploadFileWithOutRequest } from "../api/genericApis";
import { getLocalizedHeaders, getLocalizedMessagesHandlerViaLocale, handledropdownthings } from "./genericUtils";
import { getLocalisationModuleName } from "./localisationUtils";
import { getLocalizedName } from "./campaignUtils";
import {  adjustRowHeight, freezeUnfreezeColumns, manageMultiSelect } from "./excelUtils";
import * as path from 'path';
import { ColumnProperties, SheetMap } from "../models/SheetMap";

export async function initializeGenerateAndGetResponse( tenantId: string, type: string, hierarchyType: string,campaignId: string, templateConfig: any, locale: string = config.localisation.defaultLocale) {
    const responseToSend = {
        id: uuidV4(),
        tenantId: tenantId,
        type: type,
        hierarchyType: hierarchyType,
        campaignId: campaignId,
        locale: locale
    };
    const produceMessage : any = {
        generateData: responseToSend
    }
    await produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC);
    generateResource(responseToSend, templateConfig);
    return responseToSend;
}

async function generateResource(responseToSend: any, templateConfig: any) {
    try {
        const localizationMapHierarchy = responseToSend?.hierarchyType && await getLocalizedMessagesHandlerViaLocale(responseToSend?.locale, responseToSend?.tenantId, getLocalisationModuleName(responseToSend?.hierarchyType), true);
        const localizationMapModule = await getLocalizedMessagesHandlerViaLocale(responseToSend?.locale, responseToSend?.tenantId);
        const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };
        const workBook = await createBasicTemplateViaConfig(responseToSend, templateConfig, localizationMap);
        await createAndUploadFileWithOutRequest(workBook, responseToSend?.tenantId);
    } catch (error) {
        console.log(error)
    }
}

async function createBasicTemplateViaConfig(responseToSend:any,templateConfig: any, localizationMap: any) {
    const newWorkbook = new ExcelJS.Workbook();
    const tenantId = responseToSend?.tenantId;
    for(const sheet of templateConfig?.sheets) {
        const schemaName = sheet?.schemaName;
        const schema = await callMdmsSchema(tenantId, getLocalizedName(schemaName, localizationMap));
        sheet.schema = schema;
    }
    if (templateConfig?.generation) {
        const className = `${responseToSend?.type}-templateClass`;
        const classFilePath = path.join(__dirname, '..', 'templateClasses', `${className}.ts`); 

        // Dynamically import the template class and call the generate function
        try {
            const { TemplateClass } = await import(classFilePath);
            // Now call the generate function from the class
            const sheetMap : SheetMap = await TemplateClass.generate(templateConfig, responseToSend, localizationMap);
            mergeSheetMapAndSchema(sheetMap, templateConfig, localizationMap);
            for(const sheet of templateConfig?.sheets) {
                const sheetName = getLocalizedName(sheet?.sheetName, localizationMap);
                const sheetData : any = sheetMap?.[sheetName];
                const worksheet = getOrCreateWorksheet(newWorkbook, sheetName);
                await fillSheetMapInWorkbook(worksheet, sheetData);
                const schema = sheet?.schema;
                const columnsToFreeze = Object.keys(sheetData?.dynamicColumns || {}).filter(
                    (columnName) => sheetData.dynamicColumns[columnName]?.freezeColumn
                );
                freezeUnfreezeColumns(worksheet, getLocalizedHeaders(columnsToFreeze, localizationMap));
                manageMultiSelect(worksheet, schema, localizationMap);
                await handledropdownthings(worksheet, schema, localizationMap);
            }
        } catch (error) {
            console.error(`Error importing or calling generate function from ${classFilePath}:`, error);
        }
    }
    return newWorkbook;
}

function mergeSheetMapAndSchema(sheetMap: SheetMap, templateConfig: any, localizationMap: any) {
    // todo : need to work on this
    console.log(sheetMap, templateConfig, " sheetMap and templateConfig");
    for(const sheet of templateConfig?.sheets) {
        const sheetName = getLocalizedName(sheet?.sheetName, localizationMap);
        if (sheetMap?.[sheetName]) {
            sheetMap[sheetName].dynamicColumns = mergeAndGetDynamicColumns(sheetMap?.[sheetName].dynamicColumns, sheet?.schema, localizationMap);
        }
        else{
            sheetMap[sheetName] = {
                dynamicColumns: mergeAndGetDynamicColumns(null, sheet?.schema, localizationMap),
                data: []
            }
        }
    }
}


function mergeAndGetDynamicColumns(dynamicColumns: any, schema: any, localizationMap: any) {
    if (!dynamicColumns) dynamicColumns = {};

    // To keep track of the maximum orderNumber used
    let maxOrderNumber = Number.MAX_SAFE_INTEGER;

    for (const propertyKey in schema?.properties) {
        const property = schema.properties[propertyKey];
        const localizedKey = getLocalizedName(propertyKey, localizationMap);

        // Handle multiSelectDetails
        if (property?.multiSelectDetails?.maxSelections) {
            const max = property.multiSelectDetails.maxSelections;

            // Determine the parent column's orderNumber based on dynamicColumns, property, or maxOrderNumber
            const parentOrderNumber = dynamicColumns[localizedKey]?.orderNumber ?? property.orderNumber ?? maxOrderNumber;

            for (let i = 1; i <= max; i++) {
                const multiselectKey = `${propertyKey}_MULTISELECT_${i}`;
                const localizedMultiselectKey = getLocalizedName(multiselectKey, localizationMap);

                // Initialize the multi-select column if not already present
                if (!dynamicColumns[localizedMultiselectKey]) {
                    dynamicColumns[localizedMultiselectKey] = {};
                }

                // Assign properties with undefined checks
                dynamicColumns[localizedMultiselectKey].width = dynamicColumns[localizedMultiselectKey].width ?? property.width ?? 40;
                dynamicColumns[localizedMultiselectKey].color = dynamicColumns[localizedMultiselectKey].color ?? property.color;
                dynamicColumns[localizedMultiselectKey].adjustHeight = dynamicColumns[localizedMultiselectKey].adjustHeight ?? property.adjustHeight;
                dynamicColumns[localizedMultiselectKey].freezeColumnIfFilled = dynamicColumns[localizedMultiselectKey].freezeColumnIfFilled ?? property.freezeColumnIfFilled;
                dynamicColumns[localizedMultiselectKey].showInProcessed = dynamicColumns[localizedMultiselectKey].showInProcessed ?? property.showInProcessed;

                // Assign orderNumber: set as a decimal value based on parent (e.g., 5.01, 5.02, 5.03)
                const childOrderNumber = parentOrderNumber - (0.01 * i);
                dynamicColumns[localizedMultiselectKey].orderNumber = dynamicColumns[localizedMultiselectKey].orderNumber ?? childOrderNumber;

                // Track the highest orderNumber encountered
                maxOrderNumber = Math.min(maxOrderNumber, dynamicColumns[localizedMultiselectKey].orderNumber);
            }
        }

        // Apply to base localized key
        if (!dynamicColumns[localizedKey]) {
            dynamicColumns[localizedKey] = {};
        }

        // Assign properties with undefined checks
        dynamicColumns[localizedKey].width = dynamicColumns[localizedKey].width ?? property.width ?? 40;
        dynamicColumns[localizedKey].color = dynamicColumns[localizedKey].color ?? property.color;
        dynamicColumns[localizedKey].freezeColumn = dynamicColumns[localizedKey].freezeColumn ?? property.freezeColumn;
        dynamicColumns[localizedKey].hideColumn = dynamicColumns[localizedKey].hideColumn ?? property.hideColumn;
        dynamicColumns[localizedKey].adjustHeight = dynamicColumns[localizedKey].adjustHeight ?? property.adjustHeight;
        dynamicColumns[localizedKey].freezeColumnIfFilled = dynamicColumns[localizedKey].freezeColumnIfFilled ?? property.freezeColumnIfFilled;
        dynamicColumns[localizedKey].showInProcessed = dynamicColumns[localizedKey].showInProcessed ?? property.showInProcessed;

        // Determine the orderNumber based on dynamicColumns, property, or maxOrderNumber
        dynamicColumns[localizedKey].orderNumber = dynamicColumns[localizedKey].orderNumber ?? property.orderNumber ?? maxOrderNumber;

        // Track the highest orderNumber encountered
        maxOrderNumber = Math.min(maxOrderNumber, dynamicColumns[localizedKey].orderNumber);
    }

    // Sort dynamicColumns based on orderNumber
    const sortedDynamicColumns: Record<string, any> = {};
    Object.keys(dynamicColumns)
        .sort((a, b) => dynamicColumns[a].orderNumber - dynamicColumns[b].orderNumber)
        .forEach((key) => {
            sortedDynamicColumns[key] = dynamicColumns[key];
        });

    return sortedDynamicColumns;
}






async function fillSheetMapInWorkbook(worksheet: ExcelJS.Worksheet, sheetData: any) {
    const columnNameToIndexMap = processDynamicColumns(worksheet, sheetData);
    addDataToWorksheet(worksheet, sheetData, columnNameToIndexMap);
    console.log("Excel file filled successfully!");
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

function applyColumnProperties(row : ExcelJS.Row, cell: ExcelJS.Cell, columnProps: ColumnProperties) {
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

    for (let i = startRow; i <= rowCount+1; i++) {
        const row = worksheet.getRow(i);
        row.eachCell((cell, colNumber) => {
            applyBaseCellFormatting(cell);
            processBoldFormatting(cell);
            adjustRowHeightIfNeeded(row, cell, colNumber, columnNameToIndexMap, sheetData);
        });
        row.commit();
    }
}

function applyBaseCellFormatting(cell: ExcelJS.Cell) {
    cell.alignment = {
        ...cell.alignment,
        wrapText: true,
        vertical: 'middle'
    };
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







