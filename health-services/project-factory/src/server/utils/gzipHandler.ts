import { Request } from "express";
import * as zlib from "zlib";

/** Reads a gzip-compressed request stream and replaces req.body with the decompressed JSON. */
export const handleGzipRequest = async (req: Request): Promise<void> => {
    const buffers: Buffer[] = [];

    await new Promise<void>((resolve, reject) => {
        req.on("data", (chunk: any) => buffers.push(chunk));
        req.on("end", resolve);
        req.on("error", reject);
    });

    const gzipBuffer = Buffer.concat(buffers as Uint8Array[]);
    try {
        const decompressedData = await decompressGzip(gzipBuffer);
        req.body = decompressedData;
    } catch (err: any) {
        throw new Error(`Failed to process Gzip data: ${err.message}`);
    }
};

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
