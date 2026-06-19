import * as express from "express";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import { bulkDecrypt } from "../../utils/cryptUtils";

/**
 * Controller for crypto operations (encryption/decryption)
 */
class CryptoController {
    public path = "/v1/crypto";
    public router = express.Router();

    constructor() {
        this.initializeRoutes();
    }

    public initializeRoutes() {
        this.router.post(`${this.path}/_bulkDecrypt`, this.bulkDecryptData);
    }

    /**
     * Bulk decrypt API - decrypts list of encrypted strings (max 500)
     * @param request Express request object
     * @param response Express response object
     */
    bulkDecryptData = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`RECEIVED BULK DECRYPT REQUEST`);
            
            const { encryptedStrings } = request.body;
            
            // Validation
            if (!encryptedStrings || !Array.isArray(encryptedStrings)) {
                return errorResponder(
                    { 
                        message: "encryptedStrings array is required", 
                        code: "INVALID_REQUEST",
                        description: "Request body must contain 'encryptedStrings' as an array"
                    }, 
                    request, 
                    response, 
                    400
                );
            }

            if (encryptedStrings.length === 0) {
                return errorResponder(
                    { 
                        message: "encryptedStrings array cannot be empty", 
                        code: "INVALID_REQUEST",
                        description: "At least one encrypted string must be provided"
                    }, 
                    request, 
                    response, 
                    400
                );
            }

            if (encryptedStrings.length > 500) {
                return errorResponder(
                    { 
                        message: "Too many strings to decrypt", 
                        code: "LIMIT_EXCEEDED",
                        description: "Maximum 500 encrypted strings allowed per request"
                    }, 
                    request, 
                    response, 
                    400
                );
            }

            // Perform bulk decryption
            const decryptedStrings = bulkDecrypt(encryptedStrings);
            
            logger.info(`Successfully decrypted ${decryptedStrings.length} strings`);
            
            return sendResponse(response, { decryptedStrings }, request);

        } catch (error: any) {
            logger.error(`Bulk decrypt error: ${error.message}`);
            return errorResponder(
                { 
                    message: error.message || "Failed to decrypt strings", 
                    code: "DECRYPTION_ERROR",
                    description: "An error occurred during bulk decryption"
                }, 
                request, 
                response, 
                500
            );
        }
    };
}

export default CryptoController;