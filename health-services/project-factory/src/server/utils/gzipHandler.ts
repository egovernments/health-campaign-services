import { Request } from "express";
import * as zlib from "zlib";

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
    try {
        const decompressedData = await decompressGzip(gzipBuffer);
        req.body = decompressedData; // Assign the parsed data to req.body
    } catch (err: any) {
        throw new Error(`Failed to process Gzip data: ${err.message}`);
    }
};

// Helper function to decompress Gzip data
const decompressGzip = (gzipBuffer: Buffer): Promise<any> => {
    return new Promise((resolve, reject) => {
        zlib.gunzip(gzipBuffer as Uint8Array, (err, result) => {
            if (err) return reject(err);
            try {
                resolve(JSON.parse(result.toString()));
            } catch (parseErr) {
                reject(new Error("Invalid JSON format in decompressed data"));
            }
        });
    });
};
