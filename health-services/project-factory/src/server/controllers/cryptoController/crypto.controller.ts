import * as express from "express";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import { bulkDecrypt } from "../../utils/cryptUtils";
import config from "../../config";

const INTERNAL_KEY_HEADER = "x-internal-key";

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

            // Server-to-server authorization. This is a decryption oracle (any submitted ciphertext is
            // returned as plaintext with a single global key); body userInfo/roles cannot be trusted
            // because the service does not verify tokens. Require an out-of-band shared secret instead.
            // Fail closed: if no secret is configured the endpoint is unusable until ops set it.
            const providedKey = request.headers[INTERNAL_KEY_HEADER];
            if (!config.cryptoInternalKey || providedKey !== config.cryptoInternalKey) {
                logger.error(`Rejected bulk decrypt request: missing or invalid ${INTERNAL_KEY_HEADER}`);
                return errorResponder(
                    {
                        message: "Unauthorized",
                        code: "UNAUTHORIZED",
                        description: "This endpoint requires a valid internal service key"
                    },
                    request,
                    response,
                    401
                );
            }

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