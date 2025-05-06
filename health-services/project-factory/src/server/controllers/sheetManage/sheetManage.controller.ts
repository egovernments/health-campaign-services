import * as express from "express";
import { generateDataService } from "../../service/sheetManageService";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import { validateGenerateRequest } from "../../validators/genericValidator";
import { getLocaleFromRequest } from "../../utils/localisationUtils";
import GenerateTemplateQuery from "../../models/GenerateTemplateQuery";

class SheetManageController {
    public path = "/v1/sheet";
    public router = express.Router();

    constructor() {
        this.router.post(`${this.path}/_generate`, this.generateData);
    }

    generateData = async (req: express.Request, res: express.Response) => {
        try {
            const { type, tenantId, hierarchyType, campaignId } = req.query as Record<string, string>;
            logger.info(`DATA GENERATE REQUEST RECEIVED :: TYPE = ${type}`);
            await validateGenerateRequest(req);

            const userUuid = req.body?.RequestInfo?.userInfo?.uuid;
            const locale = getLocaleFromRequest(req);

            const data : GenerateTemplateQuery = { type, tenantId, hierarchyType, campaignId };
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
