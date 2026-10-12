import { Request } from "express";
import * as zlib from "zlib";
import { requestValidation } from "./requestValidation";

export const handleGzipRequest = async (req: Request): Promise<void> => {
    const buffers: Buffer[] = [];

    // Collect data chunks from the request
    await new Promise<void>((resolve, reject) => {
        req.on("data", (chunk: any) => buffers.push(chunk));
        req.on("end", resolve);
        req.on("error", reject);
    });

    // Concatenate and decompress the data
    const gzipBuffer = Buffer.concat(buffers as Uint8Array[]);
    let raw: Buffer;
    try {
        raw = await new Promise<Buffer>((resolve, reject) =>
            zlib.gunzip(gzipBuffer as Uint8Array, (err, result) => (err ? reject(err) : resolve(result))));
    } catch (err: any) {
        throw new Error(`Failed to process Gzip data: ${err.message}`);
    }
    requestValidation.inspectJsonBuffer(req, raw); // ENFORCE: throws a 400 RequestValidationError
    try {
        req.body = JSON.parse(raw.toString());
    } catch (parseErr) {
        throw new Error("Failed to process Gzip data: Invalid JSON format in decompressed data");
    }
};
