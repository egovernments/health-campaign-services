import * as express from "express";
import { generateDataService, processDataService } from "../../service/sheetManageService";
import { errorResponder, sendResponse, throwError } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import { validateGenerateRequest } from "../../validators/genericValidator";
import { getLocaleFromRequest } from "../../utils/localisationUtils";
import { GenerateTemplateQuery, generateTemplateQuerySchema } from "../../models/GenerateTemplateQuery";
import { ResourceDetails, resourceDetailsSchema } from "../../config/models/resourceDetailsSchema";

class SheetManageController {
    public path = "/v2/data";
    public router = express.Router();

    constructor() {
        this.router.post(`${this.path}/_generate`, this.generateData);
        this.router.post(`${this.path}/_process`, this.processData);
    }
    
    /**
     * Generates Excel template data based on request parameters.
     *
     * @param req - Express request object containing:
     *   - Query parameters:
     *     - type: string (template config type e.g., 'user', 'boundary')
     *     - tenantId: string (tenant identifier)
     *     - hierarchyType: string (boundary hierarchy type)
     *     - campaignId: string (optional campaign ID)
     *   - Body:
     *     - RequestInfo.userInfo.uuid: string (UUID of the user requesting generation)
     *
     * @param res - Express response object used to send the generated resource or error response.
     *
     * @returns JSON response containing the generated template resource.
     */
    generateData = async (req: express.Request, res: express.Response) => {
        try {
            logger.info(`DATA GENERATE REQUEST RECEIVED`);
            const parsed: any = generateTemplateQuerySchema.safeParse(req.query);
            if (!parsed.success) {
                const errors = parsed.error.errors.map((err: any) => `${err.message}`);
                throwError("COMMON", 400, "VALIDATION_ERROR", errors.join("; "));
            }
            const generateTemplateQuery: GenerateTemplateQuery = parsed.data;
            const userUuid = req.body?.RequestInfo?.userInfo?.uuid;
            const locale = getLocaleFromRequest(req);

            const GeneratedResource = await generateDataService(generateTemplateQuery, userUuid, locale);

            return sendResponse(res, { GeneratedResource }, req);
        } catch (e: any) {
            logger.error(String(e));
            return errorResponder(
                { message: String(e), code: e?.code, description: e?.description },
                req,
                res,
                e?.status || 500
            );
        }
    };

    processData = async (req: express.Request, res: express.Response) => {
        try {
            logger.info(`DATA PROCESS REQUEST RECEIVED`);
            const parsed : any = resourceDetailsSchema.safeParse(req.body.ResourceDetails);

            if (!parsed.success) {
                const errors = parsed.error.errors.map((err : any) => `${err.message}`);
                throwError("COMMON", 400, "VALIDATION_ERROR", errors.join("; "));
            }

            const ResourceDetails: ResourceDetails = parsed.data;
            const userUuid = req.body?.RequestInfo?.userInfo?.uuid;
            const locale = getLocaleFromRequest(req);
            const processedData = await processDataService(ResourceDetails, userUuid, locale);
            // Continue processing with validated `validData`
            return sendResponse(res, { ResourceDetails : processedData }, req);
        } catch (e: any) {
            logger.error(String(e));
            return errorResponder(
                { message: String(e), code: e?.code, description: e?.description },
                req,
                res,
                e?.status || 500
            );
        }
    };
}

export default SheetManageController;
