/**
 * excelIngestionUtils.test.ts
 *
 * Unit tests for the sheet-data access helpers:
 *   - searchSheetData       (REGRESSION: behavior must stay identical)
 *   - getSheetDataCount     (uses the service's true TotalCount, payload-independent)
 *   - forEachSheetDataPage  (bounded-memory pagination over LIMIT/OFFSET)
 *
 * The excel-ingestion service responds with:
 *   { SheetDataDetails: { Data: [...rows], TotalCount: number, SheetWiseCounts: [...] } }
 * httpRequest is mocked; the request body (2nd arg) carries SheetDataSearchCriteria.
 */

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: { excelIngestionHost: 'http://ei/' },
        paths: { excelIngestionSheetSearch: 'excel-ingestion/v1/data/sheet/_search' },
        excelIngestion: { sheetFetchPageSize: 2000, persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
    },
}));

import { searchSheetData, getSheetDataCount, forEachSheetDataPage } from '../utils/excelIngestionUtils';
import { httpRequest } from '../utils/request';

const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;

// Convenience: read the SheetDataSearchCriteria from the Nth httpRequest call
const criteriaOf = (callIndex: number) => (httpMock.mock.calls[callIndex][1] as any).SheetDataSearchCriteria;

const makeRow = (n: number) => ({
    referenceId: 'ref', tenantId: 'tn', fileStoreId: 'fs',
    sheetName: 'Sheet', rowNumber: n, rowjson: { id: n }, createdBy: 'u', createdTime: 1,
});

describe('searchSheetData — behavior preserved (regression)', () => {
    beforeEach(() => jest.clearAllMocks());

    it('returns the Data array on a well-formed response', async () => {
        httpMock.mockResolvedValue({
            SheetDataDetails: { Data: [makeRow(3), makeRow(4)], TotalCount: 2, SheetWiseCounts: [] },
        } as any);

        const result = await searchSheetData('tn', 'ref', 'fs', null, 'Sheet');

        expect(result).toHaveLength(2);
        expect(result?.[0].rowNumber).toBe(3);
    });

    it('returns an empty array when Data is empty (truthy empty-array path)', async () => {
        httpMock.mockResolvedValue({
            SheetDataDetails: { Data: [], TotalCount: 0, SheetWiseCounts: [] },
        } as any);

        const result = await searchSheetData('tn', 'ref', 'fs');

        expect(result).toEqual([]);
    });

    it('returns null when SheetDataDetails is missing', async () => {
        httpMock.mockResolvedValue({} as any);
        expect(await searchSheetData('tn', 'ref', 'fs')).toBeNull();
    });

    it('returns null when httpRequest throws', async () => {
        httpMock.mockRejectedValue(new Error('network down'));
        expect(await searchSheetData('tn', 'ref', 'fs')).toBeNull();
    });

    it('sends offset:0 and the passed limit/sheetName in the request', async () => {
        httpMock.mockResolvedValue({
            SheetDataDetails: { Data: [], TotalCount: 0, SheetWiseCounts: [] },
        } as any);

        await searchSheetData('tn', 'ref', 'fs', 5000, 'MySheet');

        const c = criteriaOf(0);
        expect(c.offset).toBe(0);
        expect(c.limit).toBe(5000);
        expect(c.sheetName).toBe('MySheet');
        expect(c.tenantId).toBe('tn');
        expect(c.referenceId).toBe('ref');
        expect(c.fileStoreId).toBe('fs');
    });
});

describe('getSheetDataCount — uses true TotalCount', () => {
    beforeEach(() => jest.clearAllMocks());

    it('returns TotalCount even when Data.length differs (count is payload-independent)', async () => {
        httpMock.mockResolvedValue({
            SheetDataDetails: { Data: [makeRow(3)], TotalCount: 35000, SheetWiseCounts: [] },
        } as any);

        const count = await getSheetDataCount('tn', 'ref', 'fs', 'Sheet');

        expect(count).toBe(35000);
    });

    it('requests limit:1, offset:0 and forwards the sheetName', async () => {
        httpMock.mockResolvedValue({
            SheetDataDetails: { Data: [makeRow(3)], TotalCount: 1, SheetWiseCounts: [] },
        } as any);

        await getSheetDataCount('tn', 'ref', 'fs', 'Boundary');

        const c = criteriaOf(0);
        expect(c.limit).toBe(1);
        expect(c.offset).toBe(0);
        expect(c.sheetName).toBe('Boundary');
    });

    it('treats an undefined TotalCount as 0', async () => {
        httpMock.mockResolvedValue({
            SheetDataDetails: { Data: [], SheetWiseCounts: [] },
        } as any);

        expect(await getSheetDataCount('tn', 'ref', 'fs')).toBe(0);
    });

    it('returns null when SheetDataDetails is missing', async () => {
        httpMock.mockResolvedValue({} as any);
        expect(await getSheetDataCount('tn', 'ref', 'fs')).toBeNull();
    });

    it('returns null when httpRequest throws', async () => {
        httpMock.mockRejectedValue(new Error('service down'));
        expect(await getSheetDataCount('tn', 'ref', 'fs')).toBeNull();
    });
});

describe('forEachSheetDataPage — bounded pagination', () => {
    beforeEach(() => jest.clearAllMocks());

    // Mock that paginates a synthetic dataset based on the request's offset/limit
    const paginate = (totalRows: number) => {
        const all = Array.from({ length: totalRows }, (_, i) => makeRow(i + 3));
        httpMock.mockImplementation(async (_url, body: any) => {
            const { offset, limit } = body.SheetDataSearchCriteria;
            return {
                SheetDataDetails: {
                    Data: all.slice(offset, offset + limit),
                    TotalCount: totalRows,
                    SheetWiseCounts: [],
                },
            } as any;
        });
        return all;
    };

    it('single page: one onPage call, returns row count, no second fetch', async () => {
        paginate(3);
        const onPage = jest.fn();

        const processed = await forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 2000, onPage);

        expect(processed).toBe(3);
        expect(onPage).toHaveBeenCalledTimes(1);
        expect(httpMock).toHaveBeenCalledTimes(1);
        expect(onPage.mock.calls[0][0]).toHaveLength(3);
    });

    it('multi-page: walks offsets 0,2,4 for TotalCount=5, returns total', async () => {
        paginate(5);
        const seen: number[] = [];
        const onPage = jest.fn(async (rows: any[]) => { seen.push(...rows.map((r: any) => r.rowNumber)); });

        const processed = await forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 2, onPage);

        expect(processed).toBe(5);
        expect(onPage).toHaveBeenCalledTimes(3);
        expect(httpMock).toHaveBeenCalledTimes(3);
        expect(criteriaOf(0).offset).toBe(0);
        expect(criteriaOf(1).offset).toBe(2);
        expect(criteriaOf(2).offset).toBe(4);
        expect(seen).toEqual([3, 4, 5, 6, 7]);
    });

    it('terminates via offset>=totalCount on an exact multiple (no empty trailing fetch)', async () => {
        paginate(4);
        const onPage = jest.fn();

        const processed = await forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 2, onPage);

        expect(processed).toBe(4);
        expect(onPage).toHaveBeenCalledTimes(2);
        expect(httpMock).toHaveBeenCalledTimes(2);
    });

    it('empty sheet: onPage never called, returns 0', async () => {
        paginate(0);
        const onPage = jest.fn();

        const processed = await forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 2, onPage);

        expect(processed).toBe(0);
        expect(onPage).not.toHaveBeenCalled();
        expect(httpMock).toHaveBeenCalledTimes(1);
    });

    it('awaits an async onPage before fetching the next page', async () => {
        paginate(4);
        const order: string[] = [];
        const onPage = jest.fn(async (rows: any[]) => {
            order.push(`onPage-start-${rows[0].rowNumber}`);
            await new Promise(r => setTimeout(r, 1));
            order.push(`onPage-end-${rows[0].rowNumber}`);
        });

        await forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 2, onPage);

        // page 1 must fully resolve before page 2 starts
        expect(order).toEqual([
            'onPage-start-3', 'onPage-end-3',
            'onPage-start-5', 'onPage-end-5',
        ]);
    });

    it('throws (fail loud) on an invalid response mid-iteration', async () => {
        httpMock.mockResolvedValue({} as any); // no SheetDataDetails
        const onPage = jest.fn();

        await expect(
            forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 2, onPage)
        ).rejects.toThrow();
        expect(onPage).not.toHaveBeenCalled();
    });

    it('throws when httpRequest throws mid-iteration', async () => {
        httpMock.mockRejectedValue(new Error('boom'));
        await expect(
            forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 2, jest.fn())
        ).rejects.toThrow();
    });

    it('falls back to the configured default page size when pageSize is 0/undefined', async () => {
        paginate(3);
        await forEachSheetDataPage('tn', 'ref', 'fs', 'Sheet', 0, jest.fn());
        // configured default page size (2000) is large (>3), so a single fetch covers everything
        expect(httpMock).toHaveBeenCalledTimes(1);
        expect(criteriaOf(0).limit).toBe(2000);
    });
});
