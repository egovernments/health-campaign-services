import * as express from "express";
import { logger } from "../../utils/logger";
import { validateGenerateRequest } from "../../utils/validators/genericValidator";
import { enrichResourceDetails, errorResponder, processGenerate, sendResponse, modifyBoundaryData, getResponseFromDb, generateAuditDetails } from "../../utils/genericUtils";
import { processGenericRequest } from "../../api/campaignApis";
import { createAndUploadFile, createBoundaryEntities, createBoundaryRelationship, createExcelSheet, getBoundaryCodesHandler, getBoundarySheetData, getHierarchy, getSheetData } from "../../api/genericApis";
import config from "../../config";
import { httpRequest } from "../../utils/request";
import { validateCreateRequest, validateSearchRequest } from "../../utils/validators/campaignValidators";
import { addBoundaryCodeToData, appendSheetsToWorkbook, generateProcessedFileAndPersist, getBoundaryTypeMap, getChildParentMap, getCodeMappingsOfExistingBoundaryCodes, prepareDataForExcel, processDataSearchRequest } from "../../utils/campaignUtils";








// Define the MeasurementController class
class dataManageController {
    // Define class properties
    public path = "/v1/data";
    public router = express.Router();
    public dayInMilliSecond = 86400000;

    // Constructor to initialize routes
    constructor() {
        this.intializeRoutes();
    }

    // Initialize routes for MeasurementController
    public intializeRoutes() {
        this.router.post(`${this.path}/_generate`, this.generateData);
        this.router.post(`${this.path}/_download`, this.downloadData)
        this.router.post(`${this.path}/_getboundarysheet`, this.getBoundaryData);
        this.router.post(`${this.path}/_autoGenerateBoundaryCode`, this.autoGenerateBoundaryCodes);
        this.router.post(`${this.path}/_create`, this.createData);
        this.router.post(`${this.path}/_search`, this.searchData);
    }


    generateData = async (request: express.Request, response: express.Response) => {
        try {
            validateGenerateRequest(request);
            await processGenerate(request, response);
            return sendResponse(response, { GeneratedResource: request?.body?.generatedResource }, request);

        } catch (e: any) {
            logger.error(String(e))
            return errorResponder({ message: String(e) }, request, response);
        }
    };


    downloadData = async (request: express.Request, response: express.Response) => {
        try {
            const type = request.query.type;
            const responseData = await getResponseFromDb(request, response);
            if (!responseData || responseData.length === 0) {
                logger.error("No data of type  " + type + " with status Completed present in db")
                throw new Error('First Generate then Download');
            }
            const auditDetails = generateAuditDetails(request);
            const transformedResponse = responseData.map((item: any) => {
                return {
                    fileStoreId: item.fileStoreid,
                    additionalDetails: {},
                    type: type,
                    auditDetails: auditDetails
                };
            });
            if (type == "boundaryWithTarget") {
                const fileStoreId = transformedResponse?.[0].fileStoreId;
                const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: request?.query?.tenantId, fileStoreIds: fileStoreId }, "get");
                if (!fileResponse?.fileStoreIds?.[0]?.url) {
                    throw new Error("Invalid file");
                }
                console.log(fileResponse?.fileStoreIds?.[0]?.url, "ggggggggggg")
                const boundaryData = await getSheetData(fileResponse?.fileStoreIds?.[0]?.url, "Sheet1");
                const updatedWorkbook = await appendSheetsToWorkbook(boundaryData);
                const boundaryDetails = await createAndUploadFile(updatedWorkbook, request);
                transformedResponse[0].fileStoreId = boundaryDetails[0].fileStoreId;
                return sendResponse(response, { fileStoreIds: transformedResponse }, request);
            } else {
                return sendResponse(response, { fileStoreIds: transformedResponse }, request);
            }
        } catch (e: any) {
            logger.error(String(e));
            return errorResponder({ message: String(e) + "    Check Logs" }, request, response);
        }
    }


    getBoundaryData = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            const boundarySheetData: any = await getBoundarySheetData(request);
            const BoundaryFileDetails: any = await createAndUploadFile(boundarySheetData?.wb, request);
            return BoundaryFileDetails;
        }
        catch (error: any) {
            logger.error(String(error));
            return errorResponder({ message: String(error) + "    Check Logs" }, request, response);
        }
    };

    autoGenerateBoundaryCodes = async (request: any, response: any) => {
        try {
            const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: request?.body?.ResourceDetails?.tenantId, fileStoreIds: request?.body?.ResourceDetails?.fileStoreId }, "get");
            if (!fileResponse?.fileStoreIds?.[0]?.url) {
                throw new Error("Invalid file");
            }
            const boundaryData = await getSheetData(fileResponse?.fileStoreIds?.[0]?.url, "Sheet1", false);
            const [withBoundaryCode, withoutBoundaryCode] = modifyBoundaryData(boundaryData);
            const { mappingMap, countMap } = getCodeMappingsOfExistingBoundaryCodes(withBoundaryCode);
            const childParentMap = getChildParentMap(withoutBoundaryCode);
            const boundaryMap = await getBoundaryCodesHandler(withoutBoundaryCode, childParentMap, mappingMap, countMap);
            const boundaryTypeMap = getBoundaryTypeMap(boundaryData, boundaryMap);
            await createBoundaryEntities(request, boundaryMap);
            const modifiedMap: Map<string, string | null> = new Map();
            childParentMap.forEach((value, key) => {
                const modifiedKey = boundaryMap.get(key);
                const modifiedValue = boundaryMap.get(value);
                modifiedMap.set(modifiedKey, modifiedValue);
            });
            await createBoundaryRelationship(request, boundaryTypeMap, modifiedMap);
            const boundaryDataForSheet = addBoundaryCodeToData(withBoundaryCode, withoutBoundaryCode, boundaryMap);
            const hierarchy = await getHierarchy(request, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType);
            const headers = [...hierarchy, "Boundary Code", "Target at the Selected Boundary level", "Start Date of Campaign (Optional Field)", "End Date of Campaign (Optional Field)"];
            const data = prepareDataForExcel(boundaryDataForSheet, hierarchy, boundaryMap);
            const boundarySheetData = await createExcelSheet(data, headers);
            const BoundaryFileDetails: any = await createAndUploadFile(boundarySheetData?.wb, request);
            return sendResponse(response, { BoundaryFileDetails: BoundaryFileDetails }, request);
        }
        catch (error) {
            return errorResponder({ message: String(error) + "    Check Logs" }, request, response);
        }
    }

    createData = async (request: any, response: any) => {
        try {
            await validateCreateRequest(request);
            await processGenericRequest(request);
            await enrichResourceDetails(request);
            await generateProcessedFileAndPersist(request);
            return sendResponse(response, { ResourceDetails: request?.body?.ResourceDetails }, request);
        } catch (e: any) {
            logger.error(String(e))
            return errorResponder({ message: String(e) }, request, response);
        }
    }

    searchData = async (request: any, response: any) => {
        try {
            await validateSearchRequest(request);
            await processDataSearchRequest(request);
            return sendResponse(response, { ResourceDetails: request?.body?.ResourceDetails }, request);
        } catch (e: any) {
            logger.error(String(e))
            return errorResponder({ message: String(e) }, request, response);
        }
    }

};
export default dataManageController;



