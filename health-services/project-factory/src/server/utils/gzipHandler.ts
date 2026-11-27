import { Request } from "express";
import * as zlib from "zlib";
import config from "../config";

const unitBytes: Record<string, number> = { b: 1, kb: 1024, mb: 1024 * 1024, gb: 1024 * 1024 * 1024 };
const defaultLimitBytes = 2 * 1024 * 1024;

const toBytes = (size: string): number => {
    const match = /^\s*(\d+(?:\.\d+)?)\s*(b|kb|mb|gb)?\s*$/i.exec(size);
    return match ? Math.floor(parseFloat(match[1]) * unitBytes[(match[2] || "b").toLowerCase()]) : defaultLimitBytes;
};

const payloadTooLarge = (maxBytes: number): Error =>
    Object.assign(new Error(`Request body is larger than the allowed ${maxBytes} bytes`), { status: 413, code: "PAYLOAD_TOO_LARGE" });

/** Reads a gzip-compressed request stream and replaces req.body with the decompressed JSON; the compressed and unzipped sizes are both capped at INCOMING_REQUEST_PAYLOAD_LIMIT so gzip cannot get around the JSON body limit. */
export const handleGzipRequest = async (req: Request): Promise<void> => {
    const maxBodyBytes = toBytes(String(config.app.incomingRequestPayloadLimit));
    const buffers: Buffer[] = [];
    let received = 0;

    await new Promise<void>((resolve, reject) => {
        req.on("data", (chunk: Buffer) => {
            received += chunk.length;
            if (received > maxBodyBytes) buffers.length = 0;
            else buffers.push(chunk);
        });
        req.on("end", resolve);
        req.on("error", reject);
    });

    if (received > maxBodyBytes) throw payloadTooLarge(maxBodyBytes);
    const gzipBuffer = Buffer.concat(buffers as Uint8Array[]);
    try {
        const decompressedData = await decompressGzip(gzipBuffer, maxBodyBytes);
        req.body = decompressedData;
    } catch (err: any) {
        if (err?.status === 413) throw err;
        throw new Error(`Failed to process Gzip data: ${err.message}`);
    }
};

const decompressGzip = (gzipBuffer: Buffer, maxBytes: number): Promise<any> => {
    return new Promise((resolve, reject) => {
        zlib.gunzip(gzipBuffer as Uint8Array, { maxOutputLength: maxBytes }, (err, result) => {
            if (err) return reject((err as NodeJS.ErrnoException).code === "ERR_BUFFER_TOO_LARGE" ? payloadTooLarge(maxBytes) : err);
            try {
                resolve(JSON.parse(result.toString()));
            } catch (parseErr) {
                reject(new Error("Invalid JSON format in decompressed data"));
            }
        });
    });
};
