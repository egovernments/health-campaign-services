import * as express from "express";
import { generateDataService } from "../../service/sheetManageService";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import { validateGenerateRequest } from "../../validators/genericValidator";
import { getLocaleFromRequest } from "../../utils/localisationUtils";
import GenerateTemplateQuery from "../../models/GenerateTemplateQuery";

class SheetManageController {
    public path = "/v2/data";
    public router = express.Router();

    constructor() {
        this.router.post(`${this.path}/_generate`, this.generateData);
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
            const { type, tenantId, hierarchyType, campaignId } = req.query as Record<string, string>;
            logger.info(`DATA GENERATE REQUEST RECEIVED :: TYPE = ${type}`);
            await validateGenerateRequest(req);

            const userUuid = req.body?.RequestInfo?.userInfo?.uuid;
            const locale = getLocaleFromRequest(req);

            const data: GenerateTemplateQuery = { type, tenantId, hierarchyType, campaignId };
            const GeneratedResource = await generateDataService(data, userUuid, locale);

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
}

export default SheetManageController;
