import { Readable } from 'stream';
import { gzipSync } from 'zlib';
import { randomBytes } from 'crypto';
import { Request } from 'express';
import config from '../config';
import { handleGzipRequest } from '../utils/gzipHandler';

jest.mock('../config', () => ({
    __esModule: true,
    default: { app: { incomingRequestPayloadLimit: '64kb' } },
}));

const LIMIT = 64 * 1024;

const streamOf = (...chunks: Buffer[]): Readable => Readable.from(chunks);
const asRequest = (stream: Readable): Request => stream as unknown as Request;
const jsonOfLength = (length: number): string => JSON.stringify({ pad: 'x'.repeat(length - 10) });

describe('handleGzipRequest', () => {
    afterEach(() => {
        config.app.incomingRequestPayloadLimit = '64kb';
    });

    describe('within the limit', () => {
        it('replaces req.body with the parsed JSON', async () => {
            const req = asRequest(streamOf(gzipSync(JSON.stringify({ RequestInfo: { msgId: 'm1' } }))));
            await handleGzipRequest(req);
            expect(req.body).toEqual({ RequestInfo: { msgId: 'm1' } });
        });

        it('accepts a body that unzips to exactly the limit', async () => {
            const req = asRequest(streamOf(gzipSync(jsonOfLength(LIMIT))));
            await handleGzipRequest(req);
            expect(req.body.pad).toHaveLength(LIMIT - 10);
        });

        it('joins a body that arrives in several chunks', async () => {
            const gz = gzipSync(JSON.stringify({ a: 'b'.repeat(1000) }));
            const req = asRequest(streamOf(gz.subarray(0, 10), gz.subarray(10, 20), gz.subarray(20)));
            await handleGzipRequest(req);
            expect(req.body.a).toHaveLength(1000);
        });
    });

    describe('over the limit', () => {
        it('rejects a body that unzips to one byte over the limit with 413', async () => {
            await expect(handleGzipRequest(asRequest(streamOf(gzipSync(jsonOfLength(LIMIT + 1))))))
                .rejects.toMatchObject({ status: 413, code: 'PAYLOAD_TOO_LARGE' });
        });

        it('rejects a small body that unzips far past the limit with 413', async () => {
            const bomb = gzipSync(Buffer.alloc(LIMIT * 64));
            expect(bomb.length).toBeLessThan(LIMIT);
            await expect(handleGzipRequest(asRequest(streamOf(bomb))))
                .rejects.toMatchObject({ status: 413, code: 'PAYLOAD_TOO_LARGE' });
        });

        it('rejects a compressed body larger than the limit with 413 after reading it to the end', async () => {
            const big = gzipSync(randomBytes(LIMIT * 2));
            const stream = streamOf(big.subarray(0, LIMIT), big.subarray(LIMIT));
            await expect(handleGzipRequest(asRequest(stream))).rejects.toMatchObject({ status: 413, code: 'PAYLOAD_TOO_LARGE' });
            expect(stream.readableEnded).toBe(true);
        });
    });

    describe('limit setting', () => {
        it('reads the production value "5mb" as 5 MiB', async () => {
            config.app.incomingRequestPayloadLimit = '5mb';
            await expect(handleGzipRequest(asRequest(streamOf(gzipSync(jsonOfLength(5 * 1024 * 1024)))))).resolves.toBeUndefined();
            await expect(handleGzipRequest(asRequest(streamOf(gzipSync(jsonOfLength(5 * 1024 * 1024 + 1))))))
                .rejects.toMatchObject({ status: 413 });
        });

        it('falls back to 2 MiB when the setting cannot be read', async () => {
            config.app.incomingRequestPayloadLimit = 'five megabytes';
            await expect(handleGzipRequest(asRequest(streamOf(gzipSync(jsonOfLength(2 * 1024 * 1024)))))).resolves.toBeUndefined();
            await expect(handleGzipRequest(asRequest(streamOf(gzipSync(jsonOfLength(2 * 1024 * 1024 + 1))))))
                .rejects.toMatchObject({ status: 413 });
        });
    });

    describe('bad input keeps its existing error', () => {
        it('rejects data that is not gzip without a 413 status', async () => {
            const err = await handleGzipRequest(asRequest(streamOf(Buffer.from('not gzip')))).catch((e: Error & { status?: number }) => e);
            expect(err).toBeInstanceOf(Error);
            expect((err as Error).message).toMatch(/^Failed to process Gzip data: /);
            expect((err as Error & { status?: number }).status).toBeUndefined();
        });

        it('rejects gzip data that is not JSON', async () => {
            await expect(handleGzipRequest(asRequest(streamOf(gzipSync('not json')))))
                .rejects.toThrow('Failed to process Gzip data: Invalid JSON format in decompressed data');
        });
    });
});
