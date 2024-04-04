
import { httpRequest } from "./request";
import config from "../config/index";
import { v4 as uuidv4 } from 'uuid';
import { produceModifiedMessages } from '../Kafka/Listener'
import { createProjectCampaignResourcData, projectCreate } from "../api/campaignApis";
import { getCampaignNumber, createAndUploadFile } from "../api/genericApis";
import { logger } from "./logger";
import createAndSearch from "../config/createAndSearch";
import pool from "../config/dbPoolConfig";
import * as XLSX from 'xlsx';

// import * as xlsx from 'xlsx-populate';
const _ = require('lodash');


function updateRange(range: any, desiredSheet: any) {
    let maxColumnIndex = 0;

    // Iterate through each row to find the last column with data
    for (let row = range.s.r; row <= range.e.r; row++) {
        for (let col = range.s.c; col <= range.e.c; col++) {
            const cellAddress = XLSX.utils.encode_cell({ r: row, c: col });
            if (desiredSheet[cellAddress]) {
                maxColumnIndex = Math.max(maxColumnIndex, col);
            }
        }
    }

    // Update the end column of the range with the maximum column index found
    range.e.c = maxColumnIndex
}

function findColumns(desiredSheet: any): { statusColumn: string, errorDetailsColumn: string } {
    var range = XLSX.utils.decode_range(desiredSheet['!ref']);

    // Check if the status column already exists in the first row
    var statusColumn: any;
    for (let col = range.s.c; col <= range.e.c; col++) {
        const cellAddress = XLSX.utils.encode_cell({ r: range.s.r, c: col });
        if (desiredSheet[cellAddress] && desiredSheet[cellAddress].v === '#status#') {
            statusColumn = String.fromCharCode(65 + col);
            for (let row = range.s.r; row <= range.e.r; row++) {
                const cellAddress = XLSX.utils.encode_cell({ r: row, c: statusColumn.charCodeAt(0) - 65 });
                delete desiredSheet[cellAddress];
            }
            break;
        }
    }
    // Check if the errorDetails column already exists in the first row
    var errorDetailsColumn: any;
    for (let col = range.s.c; col <= range.e.c; col++) {
        const cellAddress = XLSX.utils.encode_cell({ r: range.s.r, c: col });
        if (desiredSheet[cellAddress] && desiredSheet[cellAddress].v === '#errorDetails#') {
            errorDetailsColumn = String.fromCharCode(65 + col);
            for (let row = range.s.r; row <= range.e.r; row++) {
                const cellAddress = XLSX.utils.encode_cell({ r: row, c: errorDetailsColumn.charCodeAt(0) - 65 });
                delete desiredSheet[cellAddress];
            }
            break;
        }
    }
    updateRange(range, desiredSheet);
    logger.info("Updated Range : " + JSON.stringify(range))
    // If the status column doesn't exist, calculate the next available column
    const emptyColumnIndex = range.e.c + 1;
    statusColumn = String.fromCharCode(65 + emptyColumnIndex);
    desiredSheet[statusColumn + '1'] = { v: '#status#', t: 's', r: '<t xml:space="preserve">#status#</t>', h: '#status#', w: '#status#' };

    // Calculate errorDetails column one column to the right of status column
    errorDetailsColumn = String.fromCharCode(statusColumn.charCodeAt(0) + 1);
    desiredSheet[errorDetailsColumn + '1'] = { v: '#errorDetails#', t: 's', r: '<t xml:space="preserve">#errorDetails#</t>', h: '#errorDetails#', w: '#errorDetails#' };
    return { statusColumn, errorDetailsColumn };
}

function processErrorData(request: any, createAndSearchConfig: any, workbook: any, sheetName: any) {
    const desiredSheet: any = workbook.Sheets[sheetName];
    const columns = findColumns(desiredSheet);
    const statusColumn = columns.statusColumn;
    const errorDetailsColumn = columns.errorDetailsColumn;

    const errorData = request.body.sheetErrorDetails;
    errorData.forEach((error: any) => {
        const rowIndex = error.rowNumber;
        if (error.isUniqueIdentifier) {
            const uniqueIdentifierCell = createAndSearchConfig.uniqueIdentifierColumn + (rowIndex + 1);
            desiredSheet[uniqueIdentifierCell] = { v: error.uniqueIdentifier, t: 's', r: '<t xml:space="preserve">#uniqueIdentifier#</t>', h: error.uniqueIdentifier, w: error.uniqueIdentifier };
        }

        const statusCell = statusColumn + (rowIndex + 1);
        const errorDetailsCell = errorDetailsColumn + (rowIndex + 1);
        desiredSheet[statusCell] = { v: error.status, t: 's', r: '<t xml:space="preserve">#status#</t>', h: error.status, w: error.status };
        desiredSheet[errorDetailsCell] = { v: error.errorDetails, t: 's', r: '<t xml:space="preserve">#errorDetails#</t>', h: error.errorDetails, w: error.errorDetails };

    });

    desiredSheet['!ref'] = desiredSheet['!ref'].replace(/:[A-Z]+/, ':' + errorDetailsColumn);
    workbook.Sheets[sheetName] = desiredSheet;
}

async function updateStatusFile(request: any) {
    const fileStoreId = request?.body?.ResourceDetails?.fileStoreId;
    const tenantId = request?.body?.ResourceDetails?.tenantId;
    const createAndSearchConfig = createAndSearch[request?.body?.ResourceDetails?.type];
    const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: tenantId, fileStoreIds: fileStoreId }, "get");

    if (!fileResponse?.fileStoreIds?.[0]?.url) {
        throw new Error("No download URL returned for the given fileStoreId");
    }

    const headers = {
        'Content-Type': 'application/json',
        Accept: 'application/pdf',
    };

    const fileUrl = fileResponse?.fileStoreIds?.[0]?.url;
    const sheetName = createAndSearchConfig?.parseArrayConfig?.sheetName;
    const responseFile = await httpRequest(fileUrl, null, {}, 'get', 'arraybuffer', headers);
    const workbook = XLSX.read(responseFile, { type: 'buffer' });

    // Check if the specified sheet exists in the workbook
    if (!workbook.Sheets.hasOwnProperty(sheetName)) {
        throw new Error(`Sheet with name "${sheetName}" is not present in the file.`);
    }
    processErrorData(request, createAndSearchConfig, workbook, sheetName);

    const responseData = await createAndUploadFile(workbook, request);
    logger.info('File updated successfully:' + JSON.stringify(responseData));
    if (responseData?.[0]?.fileStoreId) {
        request.body.ResourceDetails.processedFileStoreId = responseData?.[0]?.fileStoreId;
    }
    else {
        throw new Error("Error in Creatring Status File");
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
        const tenantId = _.get(requestBody, createAndSearchConfig?.parseArrayConfig?.tenantId?.getValueViaPath);
        _.set(resultantElement, createAndSearchConfig?.parseArrayConfig?.tenantId?.resultantPath, tenantId);
    }

}


function processData(dataFromSheet: any[], createAndSearchConfig: any) {
    const parseLogic = createAndSearchConfig?.parseArrayConfig?.parseLogic;
    const requiresToSearchFromSheet = createAndSearchConfig?.requiresToSearchFromSheet;
    var createData = [], searchData = [];
    for (const data of dataFromSheet) {
        const resultantElement: any = {};
        for (const element of parseLogic) {
            let dataToSet = _.get(data, element.sheetColumnName);
            if (element.conversionCondition) {
                dataToSet = element.conversionCondition[dataToSet];
            }
            if (element.type) {
                dataToSet = convertToType(dataToSet, element.type);
            }
            _.set(resultantElement, element.resultantPath, dataToSet);
        }
        resultantElement["!row#number!"] = data["!row#number!"];
        var addToCreate = true;
        for (const key of requiresToSearchFromSheet) {
            if (data[key.sheetColumnName]) {
                searchData.push(resultantElement)
                addToCreate = false;
                break;
            }
        }
        if (addToCreate) {
            createData.push(resultantElement)
        }
    }
    return { searchData, createData };
}

function setTenantIdAndSegregate(processedData: any, createAndSearchConfig: any, requestBody: any) {
    for (const resultantElement of processedData.createData) {
        setTenantId(resultantElement, requestBody, createAndSearchConfig);
    }
    for (const resultantElement of processedData.searchData) {
        setTenantId(resultantElement, requestBody, createAndSearchConfig);
    }
    return processedData;
}

// Original function divided into two parts
function convertToTypeData(dataFromSheet: any[], createAndSearchConfig: any, requestBody: any) {
    const processedData = processData(dataFromSheet, createAndSearchConfig);
    return setTenantIdAndSegregate(processedData, createAndSearchConfig, requestBody);
}

function updateActivityResourceId(request: any) {
    if (request?.body?.Activities && Array.isArray(request?.body?.Activities)) {
        for (const activity of request?.body?.Activities) {
            activity.resourceDetailsId = request?.body?.ResourceDetails?.id
        }
    }
}

async function generateProcessedFileAndPersist(request: any) {
    await updateStatusFile(request);
    updateActivityResourceId(request);
    logger.info("ResourceDetails to persist : " + JSON.stringify(request?.body?.ResourceDetails));
    logger.info("Activities to persist : " + JSON.stringify(request?.body?.Activities));
    produceModifiedMessages(request?.body, config.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC);
    await new Promise(resolve => setTimeout(resolve, 2000));
    produceModifiedMessages(request?.body, config.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC);
}

function getRootBoundaryCode(boundaries: any[]) {
    for (const boundary of boundaries) {
        if (boundary.isRoot) {
            return boundary.code;
        }
    }
    return "";
}

function enrichRootProjectId(requestBody: any) {
    var rootBoundary;
    for (const boundary of requestBody?.CampaignDetails?.boundaries) {
        if (boundary?.isRoot) {
            rootBoundary = boundary?.code
            break;
        }
    }
    if (rootBoundary) {
        requestBody.CampaignDetails.projectId = requestBody?.boundaryProjectMapping?.[rootBoundary]?.projectId
    }
}

async function enrichAndPersistProjectCampaignRequest(request: any) {
    const action = request?.body?.CampaignDetails?.action;
    request.body.CampaignDetails.campaignNumber = await getCampaignNumber(request.body, "CMP-[cy:yyyy-MM-dd]-[SEQ_EG_CMP_ID]", "campaign.number", request?.body?.CampaignDetails?.tenantId);
    request.body.CampaignDetails.campaignDetails = { deliveryRules: request?.body?.CampaignDetails?.deliveryRules, startDate: request?.body?.CampaignDetails?.startDate, endDate: request?.body?.CampaignDetails?.endDate };
    request.body.CampaignDetails.status = action == "create" ? "started" : "drafted";
    request.body.CampaignDetails.boundaryCode = getRootBoundaryCode(request.body.CampaignDetails.boundaries)
    request.body.CampaignDetails.auditDetails = {
        createdBy: request?.body?.RequestInfo?.userInfo?.uuid,
        createdTime: Date.now(),
        lastModifiedBy: request?.body?.RequestInfo?.userInfo?.uuid,
        lastModifiedTime: Date.now(),
    }
    if (action == "create") {
        enrichRootProjectId(request.body);
    }
    else {
        request.body.CampaignDetails.projectId = null
    }
    produceModifiedMessages(request?.body, config.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC);
    delete request.body.CampaignDetails.campaignDetails
}


function getChildParentMap(modifiedBoundaryData: any) {
    const childParentMap: Map<string, string | null> = new Map();

    for (let i = 0; i < modifiedBoundaryData.length; i++) {
        const row = modifiedBoundaryData[i];
        for (let j = row.length - 1; j > 0; j--) {
            const child = row[j];
            const parent = row[j - 1]; // Parent is the element to the immediate left
            childParentMap.set(child, parent);
        }
    }

    return childParentMap;
}


function getCodeMappingsOfExistingBoundaryCodes(withBoundaryCode: any[]) {
    console.log(withBoundaryCode, "withhhhhhhhhhhhhhhhhh")
    const countMap = new Map<string, number>();
    const mappingMap = new Map<string, string>();
    withBoundaryCode.forEach((row: any[]) => {
        const len = row.length;
        if (len >= 3) {
            const grandParent = row[len - 3];
            if (mappingMap.has(grandParent)) {
                countMap.set(grandParent, (countMap.get(grandParent) || 0) + 1);
            } else {
                throw new Error("Insert boundary hierarchy level wise");
            }
        }
        mappingMap.set(row[len - 2], row[len - 1]);
        console.log(mappingMap, "mapppppp");
    });
    return { mappingMap, countMap };
}




function getBoundaryTypeMap(boundaryData: any[], boundaryMap: Map<string, string>) {
    const boundaryTypeMap: { [key: string]: string } = {};

    boundaryData.forEach((boundary) => {
        Object.entries(boundary).forEach(([key, value]) => {
            if (typeof value === 'string' && key !== 'Boundary Code') {
                const boundaryCode = boundaryMap.get(value);
                if (boundaryCode !== undefined) {
                    boundaryTypeMap[boundaryCode] = key;
                }
            }
        });
    });

    return boundaryTypeMap;
}

function addBoundaryCodeToData(withBoundaryCode: any[], withoutBoundaryCode: any[], boundaryMap: Map<string, string>) {
    const boundaryDataWithBoundaryCode = withBoundaryCode;
    const boundaryDataForWithoutBoundaryCode = withoutBoundaryCode.map((row: any[]) => {
        const boundaryName = row[row.length - 1]; // Get the last element of the row
        const boundaryCode = boundaryMap.get(boundaryName); // Fetch corresponding boundary code from map
        return [...row, boundaryCode]; // Append boundary code to the row and return updated row
    });
    const boundaryDataForSheet = [...boundaryDataWithBoundaryCode, ...boundaryDataForWithoutBoundaryCode];
    return boundaryDataForSheet;
}

function prepareDataForExcel(boundaryDataForSheet: any, hierarchy: any[], boundaryMap: any) {
    const data = boundaryDataForSheet.map((boundary: any[]) => {
        const boundaryCode = boundary.pop();
        const rowData = boundary.concat(Array(Math.max(0, hierarchy.length - boundary.length)).fill(''));
        const boundaryCodeIndex = hierarchy.length;
        rowData[boundaryCodeIndex] = boundaryCode;
        return rowData;
    });
    return data;
}
function extractCodesFromBoundaryRelationshipResponse(boundaries: any[]): any {
    const codes = new Set();
    for (const boundary of boundaries) {
        codes.add(boundary.code); // Add code to the Set
        if (boundary.children && boundary.children.length > 0) {
            const childCodes = extractCodesFromBoundaryRelationshipResponse(boundary.children); // Recursively get child codes
            childCodes.forEach((code: any) => codes.add(code)); // Add child codes to the Set
        }
    }
    return codes;
}

async function searchProjectCampaignResourcData(request: any) {
    const CampaignDetails = request.body.CampaignDetails;
    const { tenantId, pagination, ids, ...searchFields } = CampaignDetails;
    const queryData = buildSearchQuery(tenantId, pagination, ids, searchFields);
    const responseData = await executeSearchQuery(queryData.query, queryData.values);
    request.body.CampaignDetails = responseData;
}

function buildSearchQuery(tenantId: string, pagination: any, ids: string[], searchFields: any): { query: string, values: any[] } {
    let conditions = [];
    let values = [tenantId];
    let index = 2;

    for (const field in searchFields) {
        if (searchFields[field] !== undefined) {
            conditions.push(`${field} = $${index}`);
            values.push(searchFields[field]);
            index++;
        }
    }

    let query = `
        SELECT *
        FROM health.eg_cm_campaign_details
        WHERE tenantId = $1
    `;

    if (ids && ids.length > 0) {
        const idParams = ids.map((id, i) => `$${index + i}`);
        query += ` AND id IN (${idParams.join(', ')})`;
        values.push(...ids);
    }

    if (conditions.length > 0) {
        query += ` AND ${conditions.join(' AND ')}`;
    }

    if (pagination) {
        query += '\n';

        if (pagination.sortBy) {
            query += `ORDER BY ${pagination.sortBy}`;
            if (pagination.sortOrder) {
                query += ` ${pagination.sortOrder.toUpperCase()}`;
            }
            query += '\n';
        }

        if (pagination.limit !== undefined) {
            query += `LIMIT ${pagination.limit}`;
            if (pagination.offset !== undefined) {
                query += ` OFFSET ${pagination.offset}`;
            }
            query += '\n';
        }
    }

    return { query, values };
}

async function executeSearchQuery(query: string, values: any[]) {
    const queryResult = await pool.query(query, values);
    return queryResult.rows.map((row: any) => ({
        id: row.id,
        tenantId: row.tenantid,
        status: row.status,
        action: row.action,
        campaignNumber: row.campaignnumber,
        campaignName: row.campaignname,
        projectType: row.projecttype,
        hierarchyType: row.hierarchytype,
        boundaryCode: row.boundarycode,
        projectId: row.projectid,
        createdBy: row.createdby,
        lastModifiedBy: row.lastmodifiedby,
        createdTime: Number(row?.createdtime),
        lastModifiedTime: row.lastmodifiedtime ? Number(row.lastmodifiedtime) : null,
        additionalDetails: row.additionaldetails,
        campaignDetails: row.campaigndetails
    }));
}

async function processDataSearchRequest(request: any) {
    const { SearchCriteria } = request.body;
    const query = buildWhereClauseForDataSearch(SearchCriteria);
    const queryResult = await pool.query(query.query, query.values);
    const results = queryResult.rows.map((row: any) => ({
        id: row.id,
        tenantId: row.tenantid,
        status: row.status,
        action: row.action,
        fileStoreId: row.filestoreid,
        processedFilestoreId: row.processedfilestoreid,
        type: row.type,
        createdBy: row.createdby,
        lastModifiedBy: row.lastmodifiedby,
        createdTime: Number(row?.createdtime),
        lastModifiedTime: row.lastmodifiedtime ? Number(row.lastmodifiedtime) : null,
        additionalDetails: row.additionaldetails
    }));
    request.body.ResourceDetails = results;
}

function buildWhereClauseForDataSearch(SearchCriteria: any): { query: string; values: any[] } {
    const { id, tenantId, type, status } = SearchCriteria;
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

    const whereClause = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

    return {
        query: `
    SELECT *
    FROM health.eg_cm_resource_details
    ${whereClause};`, values
    };
}

async function processBoundary(boundary: any, boundaryCodes: any, boundaries: any[], request: any, parent?: any) {
    if (!boundaryCodes.has(boundary.code)) {
        boundaries.push({ code: boundary?.code, type: boundary?.boundaryType });
        boundaryCodes.add(boundary?.code);
    }
    if (!request?.body?.boundaryProjectMapping?.[boundary?.code]) {
        request.body.boundaryProjectMapping[boundary?.code] = {
            parent: parent ? parent : null,
            projectId: null
        }
    }
    else {
        request.body.boundaryProjectMapping[boundary?.code].parent = parent
    }
    if (boundary?.includeAllChildren) {
        const params = {
            tenantId: request?.body?.CampaignDetails?.tenantId,
            codes: boundary?.code,
            hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
            includeChildren: true
        }
        logger.info("Boundary relationship search url : " + config.host.boundaryHost + config.paths.boundaryRelationship);
        logger.info("Boundary relationship search params : " + JSON.stringify(params));
        const boundaryResponse = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, request.body, params);
        if (boundaryResponse?.TenantBoundary?.[0]) {
            logger.info("Boundary found " + JSON.stringify(boundaryResponse?.TenantBoundary?.[0]?.boundary));
            for (const childBoundary of boundaryResponse.TenantBoundary[0]?.boundary?.[0].children) {
                await processBoundary(childBoundary, boundaryCodes, boundaries, request, boundary?.code);
            }
        }
    }
}

async function addBoundaries(request: any) {
    const { boundaries } = request?.body?.CampaignDetails;
    var boundaryCodes = new Set(boundaries.map((boundary: any) => boundary.code));
    for (const boundary of boundaries) {
        await processBoundary(boundary, boundaryCodes, boundaries, request);
    }
}

function reorderBoundariesWithParentFirst(reorderedBoundaries: any[], boundaryProjectMapping: any) {
    // Function to get the index of a boundary in the reordered boundaries array
    function getIndex(code: any) {
        return reorderedBoundaries.findIndex((boundary: any) => boundary.code === code);
    }

    // Reorder boundaries so that parents come first
    for (const boundary of reorderedBoundaries) {
        const parentCode = boundaryProjectMapping[boundary.code]?.parent;
        if (parentCode) {
            const parentIndex = getIndex(parentCode);
            const boundaryIndex = getIndex(boundary.code);
            if (parentIndex !== -1 && boundaryIndex !== -1 && parentIndex > boundaryIndex) {
                // Move the boundary to be right after its parent
                reorderedBoundaries.splice(parentIndex + 1, 0, reorderedBoundaries.splice(boundaryIndex, 1)[0]);
            }
        }
    }

    return reorderedBoundaries;
}


// TODO: FIX THIS FUNCTION...NOT REORDERING CORRECTLY
async function reorderBoundaries(request: any) {
    request.body.boundaryProjectMapping = {}
    await addBoundaries(request)
    logger.info("Boundaries after addition " + JSON.stringify(request?.body?.CampaignDetails?.boundaries));
    console.log("Boundary Project Mapping " + JSON.stringify(request?.body?.boundaryProjectMapping));
    reorderBoundariesWithParentFirst(request?.body?.CampaignDetails?.boundaries, request?.body?.boundaryProjectMapping)
    logger.info("Reordered Boundaries " + JSON.stringify(request?.body?.CampaignDetails?.boundaries));
}

async function createProject(request: any) {
    const { tenantId, boundaries, projectType, startDate, endDate } = request?.body?.CampaignDetails;
    var Projects: any = [{
        tenantId,
        projectType,
        startDate,
        endDate,
        "projectSubType": "Campaign",
        "department": "Campaign",
        "description": "Campaign ",
    }]
    const projectCreateBody = {
        RequestInfo: request?.body?.RequestInfo,
        Projects
    }
    await reorderBoundaries(request)
    for (const boundary of boundaries) {
        Projects[0].address = { tenantId: tenantId, boundary: boundary?.code, boundaryType: boundary?.type }
        if (request?.body?.boundaryProjectMapping?.[boundary?.code]?.parent) {
            const parent = request?.body?.boundaryProjectMapping?.[boundary?.code]?.parent
            Projects[0].parent = request?.body?.boundaryProjectMapping?.[parent]?.projectId
        }
        else {
            Projects[0].parent = null
        }
        Projects[0].referenceID = request?.body?.CampaignDetails?.id
        await projectCreate(projectCreateBody, request)
        await new Promise(resolve => setTimeout(resolve, 3000));
    }
}

async function processBasedOnAction(request: any) {
    request.body.CampaignDetails.id = uuidv4()
    if (request?.body?.CampaignDetails?.action == "create") {
        await createProjectCampaignResourcData(request);
        await createProject(request)
        await enrichAndPersistProjectCampaignRequest(request)
    }
    else {
        await enrichAndPersistProjectCampaignRequest(request)
    }
}
async function appendSheetsToWorkbook(boundaryData: any[]) {
    try {
        const uniqueDistricts: string[] = [];
        const uniqueDistrictsForMainSheet: string[] = [];
        const workbook = XLSX.utils.book_new();
        const mainSheetData: any[] = [];
        const headersForMainSheet = Object.keys(boundaryData[0]);
        mainSheetData.push(headersForMainSheet);

        for (const data of boundaryData) {
            const rowData = Object.values(data);
            const districtIndex = rowData.indexOf(data.District);
            const districtLevelRow = rowData.slice(0, districtIndex + 1);
            if (!uniqueDistrictsForMainSheet.includes(districtLevelRow.join('_'))) {
                uniqueDistrictsForMainSheet.push(districtLevelRow.join('_'));
                mainSheetData.push(rowData);
            }
        }
        const mainSheet = XLSX.utils.aoa_to_sheet(mainSheetData);
        XLSX.utils.book_append_sheet(workbook, mainSheet, 'Sheet1');

        for (const item of boundaryData) {
            if (item.District && !uniqueDistricts.includes(item.District)) {
                uniqueDistricts.push(item.District);
            }
        }
        for (const district of uniqueDistricts) {
            const districtDataFiltered = boundaryData.filter(item => item.District === district);
            const districtIndex = Object.keys(districtDataFiltered[0]).indexOf('District');
            const headers = Object.keys(districtDataFiltered[0]).slice(districtIndex);
            const newSheetData = [headers];
            for (const data of districtDataFiltered) {
                const rowData = Object.values(data).slice(districtIndex).map(value => value === null ? '' : String(value)); // Replace null with empty string
                newSheetData.push(rowData);
            }
            const ws = XLSX.utils.aoa_to_sheet(newSheetData);
            XLSX.utils.book_append_sheet(workbook, ws, district);
        }
        return workbook;
    } catch (error) {
        throw Error("An error occurred while appending sheets:");
    }
}

export {
    generateProcessedFileAndPersist,
    convertToTypeData,
    getChildParentMap,
    getBoundaryTypeMap,
    addBoundaryCodeToData,
    prepareDataForExcel,
    extractCodesFromBoundaryRelationshipResponse,
    searchProjectCampaignResourcData,
    processDataSearchRequest,
    getCodeMappingsOfExistingBoundaryCodes,
    processBasedOnAction,
    appendSheetsToWorkbook
}