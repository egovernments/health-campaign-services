import zlib from "zlib";
import { Request, Response, NextFunction } from "express";
const { object, string } = require("yup"); // Importing object and string from yup for schema validation
import { logger } from "./logger";
import { errorResponder } from "./genericUtils";


const requestSchema = object({
    apiId: string().nullable(), // Nullable string field for API ID
    action: string().nullable(), // Nullable string field for action
    msgId: string().required(), // Required string field for message ID
    authToken: string().nullable(), // Nullable string field for authentication token
    userInfo: object().nonNullable() // Non-nullable object field for user information
});

export const handleGzipRequest = (
    req: Request,
    res: Response,
    next: NextFunction
) => {
    const buffers: Buffer[] = [];

    req.on("data", (chunk) => buffers.push(chunk));
    req.on("end", () => {
        try {
            const gzipBuffer = Buffer.concat(buffers);
            logger.info("Gzip buffer size:", gzipBuffer.length);

            // Decompress the Gzip data
            zlib.gunzip(gzipBuffer, (err, decompressedBuffer) => {
                if (err) {
                    logger.error("Error decompressing Gzip file:", err);
                    res.status(500).send("Invalid Gzip file");
                    return;
                }

                try {
                    // Convert the decompressed buffer to string and parse as JSON
                    const jsonData = decompressedBuffer.toString().trim();
                    logger.info("Decompressed JSON data:", jsonData);
                    const parsedData = JSON.parse(jsonData);
                    req.body = parsedData;
                    // Validation 1: Check if tenantId is present
                    if (!req?.body?.RequestInfo?.userInfo?.tenantId) {
                        let e: any = new Error("RequestInfo.userInfo.tenantId is missing");
                        e = Object.assign(e, { status: 400, code: "VALIDATION_ERROR" });
                        return errorResponder(e, req, res, 400); // Return error response if tenantId is missing
                    }

                    // Validation 2: Validate the request payload against the defined schema
                    requestSchema.validateSync(req.body.RequestInfo); // Assuming validateSync is synchronous
                    next(); // Proceed to next middleware or controller if valid
                } catch (parseError) {
                    logger.error("Error parsing JSON data:", parseError);
                    res.status(500).send("Invalid JSON in Gzip content");
                }
            });
        } catch (err) {
            logger.error("Error processing Gzip content:", err);
            res.status(500).send("Invalid Gzip content");
        }
    });
};