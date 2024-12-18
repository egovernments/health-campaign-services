import zlib from "zlib";
import { Request, Response, NextFunction } from "express";
import { logger } from "./logger";

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

      // Decompress the Gzip data
      zlib.gunzip(gzipBuffer, (err, decompressedBuffer) => {
        if (err) {
          logger.error("Error decompressing Gzip file:", err);
          res.status(500).send("Invalid Gzip file");
          return;
        }

        try {
          // Convert the decompressed buffer to string and parse as JSON
          const jsonData = decompressedBuffer.toString();
          const parsedData = JSON.parse(jsonData);
          req.body = parsedData; // Attach parsed data to the request body
          next(); // Proceed to next middleware
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
