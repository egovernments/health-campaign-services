import * as XLSX from 'xlsx';
import config from "../config";
import FormData from 'form-data';
import { httpRequest } from "../utils/request";
import { logger } from "../utils/logger";
import { correctParentValues, getBoundaryRelationshipData, getDataSheetReady, sortCampaignDetails } from "../utils/genericUtils";
import { validateProjectFacilityResponse, validateProjectResourceResponse, validateStaffResponse, validatedProjectResponseAndUpdateId } from "../utils/validators/genericValidator";
import { extractCodesFromBoundaryRelationshipResponse, generateFilteredBoundaryData } from '../utils/campaignUtils';
const _ = require('lodash');

const getWorkbook = async (fileUrl: string, sheetName: string) => {
    try {
        const headers = {
            'Content-Type': 'application/json',
            Accept: 'application/pdf',
        };
        const responseFile = await httpRequest(fileUrl, null, {}, 'get', 'arraybuffer', headers);
        const workbook = XLSX.read(responseFile, { type: 'buffer' });
        if (!workbook.Sheets.hasOwnProperty(sheetName)) {
            throw new Error(`Sheet with name "${sheetName}" is not present in the file.`);
        }
        return workbook;
    } catch (error) {
        throw Error("Error while fetching sheet data: " + error)
    }

}
const getSheetData = async (fileUrl: string, sheetName: string, getRow = false) => {
    const workbook = await getWorkbook(fileUrl, sheetName)
    const sheetData = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName]);
    const jsonData = sheetData.map((row: any, index: number) => {
        const rowData: any = {};
        Object.keys(row).forEach(key => {
            rowData[key] = row[key] === undefined || row[key] === '' ? null : row[key];
        });
        if (getRow) rowData['!row#number!'] = index + 1; // Adding row number
        return rowData;
    });
    logger.info("Sheet Data : " + JSON.stringify(jsonData))
    return jsonData;
};





const searchMDMS: any = async (uniqueIdentifiers: any[], schemaCode: string, requestinfo: any, response: any) => {
    if (!uniqueIdentifiers) {
        return;
    }
    const apiUrl = config.host.mdms + config.paths.mdms_search;
    logger.info("Mdms url : " + apiUrl)
    const data = {
        "MdmsCriteria": {
            "tenantId": requestinfo?.userInfo?.tenantId,
            "uniqueIdentifiers": uniqueIdentifiers,
            "schemaCode": schemaCode
        },
        "RequestInfo": requestinfo
    }
    try {
        const result = await httpRequest(apiUrl, data, undefined, undefined, undefined, undefined);
        logger.info("Template search Result : " + JSON.stringify(result))
        return result;
    } catch (error: any) {
        logger.error("Error: " + error)
        return error?.response?.data?.Errors[0].message;
    }

}


const getCampaignNumber: any = async (requestBody: any, idFormat: String, idName: string, tenantId: string) => {
    const data = {
        RequestInfo: requestBody?.RequestInfo,
        "idRequests": [
            {
                "idName": idName,
                "tenantId": tenantId,
                "format": idFormat
            }
        ]
    }
    const idGenUrl = config.host.idGenHost + config.paths.idGen;
    logger.info("IdGen url : " + idGenUrl)
    logger.info("Idgen Request : " + JSON.stringify(data))
    const result = await httpRequest(idGenUrl, data, undefined, undefined, undefined, undefined);
    if (result?.idResponses?.[0]?.id) {
        return result?.idResponses?.[0]?.id;
    }
    throw new Error("Error during generating campaign number");
}

const getResouceNumber: any = async (RequestInfo: any, idFormat: String, idName: string) => {
    const data = {
        RequestInfo,
        "idRequests": [
            {
                "idName": idName,
                "tenantId": RequestInfo?.userInfo?.tenantId,
                "format": idFormat
            }
        ]
    }
    const idGenUrl = config.host.idGenHost + config.paths.idGen;
    logger.info("IdGen url : " + idGenUrl)
    logger.info("Idgen Request : " + JSON.stringify(data))
    try {
        const result = await httpRequest(idGenUrl, data, undefined, undefined, undefined, undefined);
        if (result?.idResponses?.[0]?.id) {
            return result?.idResponses?.[0]?.id;
        }
        return result;
    } catch (error: any) {
        logger.error("Error: " + error)
        return error;
    }

}

const getSchema: any = async (code: string, RequestInfo: any) => {
    const data = {
        RequestInfo,
        SchemaDefCriteria: {
            "tenantId": RequestInfo?.userInfo?.tenantId,
            "limit": 200,
            "codes": [
                code
            ]
        }
    }
    const mdmsSearchUrl = config.host.mdms + config.paths.mdmsSchema;
    logger.info("Schema search url : " + mdmsSearchUrl)
    logger.info("Schema search Request : " + JSON.stringify(data))
    try {
        const result = await httpRequest(mdmsSearchUrl, data, undefined, undefined, undefined, undefined);
        return result?.SchemaDefinitions?.[0]?.definition;
    } catch (error: any) {
        logger.error("Error: " + error)
        return error;
    }

}



const getCount: any = async (responseData: any, request: any, response: any) => {
    try {
        const host = responseData?.host;
        const url = responseData?.searchConfig?.countUrl;
        const requestInfo = { "RequestInfo": request?.body?.RequestInfo }
        const result = await httpRequest(host + url, requestInfo, undefined, undefined, undefined, undefined);
        const count = _.get(result, responseData?.searchConfig?.countPath);
        return count;
    } catch (error: any) {
        logger.error("Error: " + error)
        throw error;
    }

}

async function createAndUploadFile(updatedWorkbook: XLSX.WorkBook, request: any, tenantId?: any) {
    const buffer = XLSX.write(updatedWorkbook, { bookType: 'xlsx', type: 'buffer' });
    const formData = new FormData();
    formData.append('file', buffer, 'filename.xlsx');
    formData.append('tenantId', tenantId ? tenantId : request?.body?.RequestInfo?.userInfo?.tenantId);
    formData.append('module', 'pgr');

    logger.info("File uploading url : " + config.host.filestore + config.paths.filestore);
    var fileCreationResult = await httpRequest(config.host.filestore + config.paths.filestore, formData, undefined, undefined, undefined,
        {
            'Content-Type': 'multipart/form-data',
            'auth-token': request?.body?.RequestInfo?.authToken
        }
    );
    const responseData = fileCreationResult?.files;
    return responseData;
}

function generateHierarchyList(data: any[], parentChain: any = []) {
    let result: any[] = [];

    // Iterate over each boundary in the current level
    for (let boundary of data) {
        let currentChain = [...parentChain, boundary.code];

        // Add the current chain to the result
        result.push(currentChain.join(','));

        // If there are children, recursively call the function
        if (boundary.children.length > 0) {
            let childResults = generateHierarchyList(boundary.children, currentChain);
            result = result.concat(childResults);
        }
    }
    return result;

}

function generateHierarchy(boundaries: any[]) {
    // Create an object to store boundary types and their parents
    const parentMap: any = {};

    // Populate the object with boundary types and their parents
    for (const boundary of boundaries) {
        parentMap[boundary.boundaryType] = boundary.parentBoundaryType;
    }

    // Traverse the hierarchy to generate the hierarchy list
    const hierarchyList = [];
    for (const boundaryType in parentMap) {
        if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
            const parentBoundaryType = parentMap[boundaryType];
            if (parentBoundaryType === null) {
                // This boundary type has no parent, add it to the hierarchy list
                hierarchyList.push(boundaryType);
                // Traverse its children recursively
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
                // This boundary type has the current parent, add it to the hierarchy list
                hierarchyList.push(boundaryType);
                // Traverse its children recursively
                traverseChildren(boundaryType, parentMap, hierarchyList);
            }
        }
    }
}

const getHierarchy = async (request: any, tenantId: string, hierarchyType: string) => {
    const url = `${config.host.boundaryHost}${config.paths.boundaryHierarchy}`;

    // Create request body
    const requestBody = {
        "RequestInfo": request?.body?.RequestInfo,
        "BoundaryTypeHierarchySearchCriteria": {
            "tenantId": tenantId,
            "limit": 5,
            "offset": 0,
            "hierarchyType": hierarchyType
        }
    };

    try {
        const response = await httpRequest(url, requestBody);
        const boundaryList = response?.BoundaryHierarchy?.[0].boundaryHierarchy;
        return generateHierarchy(boundaryList);
    } catch (error) {
        console.error('Error:', error);
        throw error;
    }
};



async function createExcelSheet(data: any, headers: any, sheetName: string = 'Sheet1') {
    const workbook = XLSX.utils.book_new();
    const sheetData = [headers, ...data];
    const ws = XLSX.utils.aoa_to_sheet(sheetData);

    // Define column widths (in pixels)
    const columnWidths = headers.map(() => ({ width: 30 }));

    // Apply column widths to the sheet
    ws['!cols'] = columnWidths;

    XLSX.utils.book_append_sheet(workbook, ws, sheetName);
    return { wb: workbook, ws: ws, sheetName: sheetName };
}

async function getBoundaryCodesHandler(boundaryList: any, childParentMap: any, elementCodesMap: any, countMap: any) {
    try {
        const updatedelementCodesMap = await getAutoGeneratedBoundaryCodes(boundaryList, childParentMap, elementCodesMap, countMap);
        return updatedelementCodesMap;
    } catch (error) {
        console.error("Error in getBoundaryCodesHandler:", error);
        throw error; // Propagate the error
    }
}

async function getAutoGeneratedBoundaryCodes(boundaryList: any, childParentMap: any, elementCodesMap: any, countMap: any) {
    const columnsData: string[][] = [];
    for (const row of boundaryList) {
        row.forEach((element: any, index: any) => {
            if (!columnsData[index]) {
                columnsData[index] = [];
            }
            if (!columnsData[index].includes(element)) {
                columnsData[index].push(element);
            }
        });
    }

    const elementSet = new Set<string>();
    columnsData.forEach(column => {
        column.forEach(element => {
            elementSet.add(element);
        });
    });

    for (let i = 0; i < columnsData.length; i++) {
        const column = columnsData[i];
        for (const element of column) {
            if (!elementCodesMap.has(element)) {
                const parentCode = childParentMap.get(element)!;
                if (parentCode !== undefined && parentCode !== null && elementSet.has(parentCode)) {
                    countMap.set(parentCode, (countMap.get(parentCode) || 0) + 1);

                    let code;
                    const grandParentCode = childParentMap.get(parentCode);
                    if (grandParentCode != null && grandParentCode != undefined) {
                        const parentBoundaryCode = elementCodesMap.get(parentCode);
                        const lastUnderscoreIndex = parentBoundaryCode.lastIndexOf('_');
                        const parentBoundaryCodeTrimmed = lastUnderscoreIndex !== -1 ? parentBoundaryCode.substring(0, lastUnderscoreIndex) : parentBoundaryCode;
                        code = generateElementCode(countMap.get(parentCode), parentBoundaryCodeTrimmed, element);
                    } else {
                        code = generateElementCode(countMap.get(parentCode), elementCodesMap.get(parentCode), element);
                    }

                    elementCodesMap.set(element, code); // Store the code of the element in the map
                }
                else {
                    elementCodesMap.set(element, "ADMIN_" + element.toString().substring(0, 2));
                }
            }
            else { continue; }
        }
    }
    return elementCodesMap;
}

function generateElementCode(sequence: any, parentCode: any, element: any) {
    let paddedSequence = sequence.toString().padStart(2, '0'); // Pad single-digit numbers with leading zero
    return parentCode + '_' + paddedSequence + '_' + element;
}

async function getBoundarySheetData(request: any) {
    const params = {
        ...request?.query,
        includeChildren: true
    };
    const boundaryData = await getBoundaryRelationshipData(request, params);
    logger.info("boundaryData for sheet " + JSON.stringify(boundaryData))
    if (request?.body?.Filters) {
        const filteredBoundaryData = await generateFilteredBoundaryData(request);
        return await getDataSheetReady(filteredBoundaryData, request);
    }
    else {
        return await getDataSheetReady(boundaryData, request);
    }
}



async function createProjectAndUpdateId(projectBody: any, boundaryProjectIdMapping: any, boundaryCode: any, campaignDetails: any) {
    const projectCreateUrl = `${config.host.projectHost}` + `${config.paths.projectCreate}`
    logger.info("Project Creation url " + projectCreateUrl)
    logger.info("Project Creation body " + JSON.stringify(projectBody))
    const projectResponse = await httpRequest(projectCreateUrl, projectBody, undefined, "post", undefined, undefined);
    logger.info("Project Creation response" + JSON.stringify(projectResponse))
    validatedProjectResponseAndUpdateId(projectResponse, projectBody, campaignDetails);
    boundaryProjectIdMapping[boundaryCode] = projectResponse?.Project[0]?.id
    await new Promise(resolve => setTimeout(resolve, 3000));
}

async function createProjectIfNotExists(requestBody: any) {
    const { projectType, tenantId } = requestBody?.Campaign
    sortCampaignDetails(requestBody?.Campaign?.CampaignDetails)
    correctParentValues(requestBody?.Campaign?.CampaignDetails)
    var boundaryProjectIdMapping: any = {};
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        const projectBody: any = {
            RequestInfo: requestBody.RequestInfo,
            Projects: []
        }
        var { projectId, startDate, endDate, boundaryCode, boundaryType, parentBoundaryCode, description, department, referenceID, projectSubType, isTaskEnabled = true, documents = [], rowVersion = 0 } = campaignDetails;
        const address = {
            tenantId,
            boundary: boundaryCode,
            boundaryType
        }
        startDate = parseInt(startDate);
        endDate = parseInt(endDate);
        if (!projectId) {
            projectBody.Projects.push({
                tenantId, parent: boundaryProjectIdMapping[parentBoundaryCode] || null, address, description, department, referenceID, projectSubType, projectType, startDate, endDate, isTaskEnabled, documents, rowVersion
            })
            await createProjectAndUpdateId(projectBody, boundaryProjectIdMapping, boundaryCode, campaignDetails)
        }
    }
}

async function createStaff(resouceBody: any) {
    const staffCreateUrl = `${config.host.projectHost}` + `${config.paths.staffCreate}`
    logger.info("Staff Creation url " + staffCreateUrl)
    logger.info("Staff Creation body " + JSON.stringify(resouceBody))
    const staffResponse = await httpRequest(staffCreateUrl, resouceBody, undefined, "post", undefined, undefined);
    logger.info("Staff Creation response" + JSON.stringify(staffResponse))
    validateStaffResponse(staffResponse);
}

async function createProjectResource(resouceBody: any) {
    const projectResourceCreateUrl = `${config.host.projectHost}` + `${config.paths.projectResourceCreate}`
    logger.info("Project Resource Creation url " + projectResourceCreateUrl)
    logger.info("Project Resource Creation body " + JSON.stringify(resouceBody))
    const projectResourceResponse = await httpRequest(projectResourceCreateUrl, resouceBody, undefined, "post", undefined, undefined);
    logger.info("Project Resource Creation response" + JSON.stringify(projectResourceResponse))
    validateProjectResourceResponse(projectResourceResponse);
}

async function createProjectFacility(resouceBody: any) {
    const projectFacilityCreateUrl = `${config.host.projectHost}` + `${config.paths.projectFacilityCreate}`
    logger.info("Project Facility Creation url " + projectFacilityCreateUrl)
    logger.info("Project Facility Creation body " + JSON.stringify(resouceBody))
    const projectFacilityResponse = await httpRequest(projectFacilityCreateUrl, resouceBody, undefined, "post", undefined, undefined);
    logger.info("Project Facility Creation response" + JSON.stringify(projectFacilityResponse))
    validateProjectFacilityResponse(projectFacilityResponse);
}
async function createRelatedEntity(resources: any, tenantId: any, projectId: any, startDate: any, endDate: any, resouceBody: any) {
    for (const resource of resources) {
        const type = resource?.type
        for (const resourceId of resource?.resourceIds) {
            if (type == "staff") {
                const ProjectStaff = {
                    tenantId: tenantId.split('.')?.[0],
                    projectId,
                    userId: resourceId,
                    startDate,
                    endDate
                }
                resouceBody.ProjectStaff = ProjectStaff
                await createStaff(resouceBody)
            }
            else if (type == "resource") {
                const ProjectResource = {
                    // FIXME : Tenant Id should not be splitted
                    tenantId: tenantId.split('.')?.[0],
                    projectId,
                    resource: {
                        productVariantId: resourceId,
                        type: "DRUG",
                        "isBaseUnitVariant": false
                    },
                    startDate,
                    endDate
                }
                resouceBody.ProjectResource = ProjectResource
                await createProjectResource(resouceBody)
            }
            else if (type == "facility") {
                const ProjectFacility = {
                    // FIXME : Tenant Id should not be splitted
                    tenantId: tenantId.split('.')?.[0],
                    projectId,
                    facilityId: resourceId
                }
                resouceBody.ProjectFacility = ProjectFacility
                await createProjectFacility(resouceBody)
            }
        }
    }
}

async function createRelatedResouce(requestBody: any) {
    const { tenantId } = requestBody?.Campaign

    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
        const resouceBody: any = {
            RequestInfo: requestBody.RequestInfo,
        }
        var { projectId, startDate, endDate, resources } = campaignDetails;
        startDate = parseInt(startDate);
        endDate = parseInt(endDate);
        await createRelatedEntity(resources, tenantId, projectId, startDate, endDate, resouceBody);
    }
}

async function createBoundaryEntities(request: any, boundaryMap: Map<string, string>) {
    try {
        const requestBody = { "RequestInfo": request.body.RequestInfo } as { RequestInfo: any; Boundary?: any };
        const boundaries: any[] = [];
        const boundaryCodes: any[] = [];
        Array.from(boundaryMap.entries()).forEach(([, boundaryCode]) => {
            boundaryCodes.push(boundaryCode);
        });
        const boundaryEntityResponse = await httpRequest(config.host.boundaryHost + config.paths.boundaryServiceSearch, request.body, { tenantId: request?.body?.ResourceDetails?.tenantId, codes: boundaryCodes.join(', ') });
        const codesFromResponse = boundaryEntityResponse.Boundary.map((boundary: any) => boundary.code);
        const codeSet = new Set(codesFromResponse);  // Creating a set and filling it with the codes from the response
        Array.from(boundaryMap.entries()).forEach(async ([boundaryName, boundaryCode]) => {   // Convert the Map to an array of entries and iterate over it
            const boundary = {
                tenantId: request?.body?.ResourceDetails?.tenantId,
                code: boundaryCode,
                geometry: null,
                additionalDetails: {
                    name: boundaryName
                }
            };
            if (!codeSet.has(boundaryCode)) {
                boundaries.push(boundary);
            }
        });
        if (!(boundaries.length === 0)) {
            requestBody.Boundary = boundaries;
            const response = await httpRequest(`${config.host.boundaryHost}boundary-service/boundary/_create`, requestBody, {}, 'POST',);
            console.log('Boundary entities created:', response);
        }
        else {
            throw Error("Boundary present in the system")
        }
    } catch (error) {
        console.error('Error creating boundary entities:', error);
        throw Error('Error creating boundary entities: Boundary already present in the system'); // Throw the error to the calling function
    }
}

async function createBoundaryRelationship(request: any, boundaryTypeMap: { [key: string]: string } = {}, modifiedChildParentMap: any) {
    try {
        const requestBody = { "RequestInfo": request.body.RequestInfo } as { RequestInfo: any; BoundaryRelationship?: any };
        const url = `${config.host.boundaryHost}${config.paths.boundaryRelationship}`;
        const params = {
            "type": request?.body?.ResourceDetails?.type,
            "tenantId": request?.body?.ResourceDetails?.tenantId,
            "boundaryType": null,
            "codes": null,
            "includeChildren": true,
            "hierarchyType": request?.body?.ResourceDetails?.hierarchyType
        };
        const boundaryRelationshipResponse = await httpRequest(url, request.body, params);
        const boundaryData = boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary;
        const allCodes = extractCodesFromBoundaryRelationshipResponse(boundaryData);
        let flag = 1;
        for (const [boundaryCode, boundaryType] of Object.entries(boundaryTypeMap)) {
            const boundary = {
                tenantId: request?.body?.ResourceDetails?.tenantId,
                boundaryType: boundaryType,
                code: boundaryCode,
                hierarchyType: request?.body?.ResourceDetails?.hierarchyType,
                parent: modifiedChildParentMap.get(boundaryCode)
            }
            if (!allCodes.has(boundaryCode)) {
                flag = 0;
                requestBody.BoundaryRelationship = boundary;
                const response = await httpRequest(`${config.host.boundaryHost}boundary-service/boundary-relationships/_create`, requestBody, {}, 'POST');
                console.log('Boundary relationship created:', response);
            }
            else {
                continue
            }
        }
        if (flag) {
            throw Error("Boundary already exist in the system");
        }
    } catch (error) {
        console.error('Error creating boundary relationship:', error);
        throw new Error('Error creating boundary relationship: Boundary already exist in the system');
    }
}

export {
    getAutoGeneratedBoundaryCodes,
    getBoundaryCodesHandler,
    getHierarchy,
    createBoundaryEntities,
    createBoundaryRelationship,
    getWorkbook,
    getSheetData,
    searchMDMS,
    getCampaignNumber,
    getSchema,
    getResouceNumber,
    getCount,
    getBoundarySheetData,
    createAndUploadFile,
    createProjectIfNotExists,
    createRelatedResouce,
    createExcelSheet,
    generateHierarchyList
}