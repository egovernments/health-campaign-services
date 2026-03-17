import * as express from "express";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import {
  createResourceDetail,
  updateResourceDetail,
  searchResourceDetails
} from "../../service/resourceDetailsService";
import { resourceDetailsCreateSchema } from "../../config/models/resourceDetailsCreateSchema";
import { resourceDetailsUpdateSchema } from "../../config/models/resourceDetailsUpdateSchema";
import { resourceDetailsCriteriaSchema, paginationSchema } from "../../config/models/resourceDetailsCriteria";

class ResourceDetailsController {
  public path = "/v1/resource-details";
  public router = express.Router();

  constructor() {
    this.intializeRoutes();
  }

  public intializeRoutes() {
    this.router.post(`${this.path}/_create`, this.createResource);
    this.router.post(`${this.path}/_update`, this.updateResource);
    this.router.post(`${this.path}/_search`, this.searchResources);
  }

  createResource = async (request: express.Request, response: express.Response) => {
    try {
      logger.info(`RECEIVED RESOURCE DETAILS CREATE REQUEST`);
      const parseResult = resourceDetailsCreateSchema.safeParse(request?.body?.ResourceDetails);
      if (!parseResult.success) {
        return errorResponder(
          { message: parseResult.error.message, code: "VALIDATION_ERROR", description: parseResult.error.issues },
          request, response, 400
        );
      }
      const userUuid = request?.body?.RequestInfo?.userInfo?.uuid || "system";
      const ResourceDetails = await createResourceDetail(parseResult.data, userUuid);
      return sendResponse(response, { ResourceDetails }, request);
    } catch (e: any) {
      logger.error(String(e));
      return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
    }
  };

  updateResource = async (request: express.Request, response: express.Response) => {
    try {
      logger.info(`RECEIVED RESOURCE DETAILS UPDATE REQUEST`);
      const parseResult = resourceDetailsUpdateSchema.safeParse(request?.body?.ResourceDetails);
      if (!parseResult.success) {
        return errorResponder(
          { message: parseResult.error.message, code: "VALIDATION_ERROR", description: parseResult.error.issues },
          request, response, 400
        );
      }
      const userUuid = request?.body?.RequestInfo?.userInfo?.uuid || "system";
      const ResourceDetails = await updateResourceDetail(parseResult.data, userUuid);
      return sendResponse(response, { ResourceDetails }, request);
    } catch (e: any) {
      logger.error(String(e));
      return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
    }
  };

  searchResources = async (request: express.Request, response: express.Response) => {
    try {
      logger.info(`RECEIVED RESOURCE DETAILS SEARCH REQUEST`);
      const criteriaParseResult = resourceDetailsCriteriaSchema.safeParse(request?.body?.ResourceDetailsCriteria);
      if (!criteriaParseResult.success) {
        return errorResponder(
          { message: criteriaParseResult.error.message, code: "VALIDATION_ERROR", description: criteriaParseResult.error.issues },
          request, response, 400
        );
      }
      const paginationParseResult = paginationSchema.safeParse(request?.body?.Pagination);
      const pagination = paginationParseResult.success ? paginationParseResult.data : undefined;

      const result = await searchResourceDetails(criteriaParseResult.data, pagination);
      return sendResponse(response, {
        ...result,
        Pagination: pagination || { limit: 50, offset: 0 }
      }, request);
    } catch (e: any) {
      logger.error(String(e));
      return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
    }
  };
}

export default ResourceDetailsController;
