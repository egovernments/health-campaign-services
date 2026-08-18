/**
 * The current-register file is a snapshot written at upload time, so a register deleted afterwards
 * has to be taken out of that file — and the rows below it must not lose their dropdowns.
 */
jest.mock('../config', () => ({
    default: {
        host: {}, paths: {}, kafka: {}, DB_CONFIG: {},
        attendanceRegister: { sheetRefreshLeaseMs: 120000, sheetRefreshWaitMs: 60, sheetRefreshPollMs: 5 },
    },
    __esModule: true,
}));

jest.mock('../utils/db', () => ({
    executeQuery: jest.fn(),
    getTableName: jest.fn((table: string) => table),
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn(),
    getCampaignIdsByCampaignNumber: jest.fn(),
}));

jest.mock('../utils/resourceDetailsUtils', () => ({
    searchResourceDetailsFromDB: jest.fn(),
    getResourceDetailById: jest.fn(),
}));
jest.mock('../api/genericApis', () => ({ createAndUploadFileWithOutRequest: jest.fn() }));
jest.mock('../api/coreApis', () => ({ fetchFileFromFilestore: jest.fn() }));
jest.mock('../utils/excelUtils', () => ({ getExcelWorkbookFromFileURL: jest.fn() }));

import * as ExcelJS from 'exceljs';
import {
    removeDeletedRegisterRows,
    markRegisterSheetRefreshPending,
    completeOwedRegisterSheetRefresh,
} from '../utils/attendanceSheetUtils';
import { getCampaignIdsByCampaignNumber, getRelatedDataWithCampaign } from '../utils/genericUtils';
import { searchResourceDetailsFromDB, getResourceDetailById } from '../utils/resourceDetailsUtils';
import { createAndUploadFileWithOutRequest } from '../api/genericApis';
import { fetchFileFromFilestore } from '../api/coreApis';
import { getExcelWorkbookFromFileURL } from '../utils/excelUtils';
import { executeQuery } from '../utils/db';

const KEYS = [
    'HCM_ADMIN_CONSOLE_BOUNDARY_CODE',
    'HCM_ATTENDANCE_REGISTER_ID',
    'HCM_ATTENDANCE_REGISTER_EVENT_TYPE',
    '#status#',
];

function workbookWith(registers: { code: string; id: string; event: string; status: string }[]) {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet('HCM_ATTENDANCE_REGISTER_LIST');
    sheet.getRow(1).values = KEYS;                       // unlocalized keys
    sheet.getRow(2).values = ['Boundary Code', 'Register ID', 'Event Type', 'Status'];
    registers.forEach((r, i) => {
        sheet.getRow(3 + i).values = [r.code, r.id, r.event, r.status];
    });
    return { workbook, sheet };
}

const idsOf = (sheet: ExcelJS.Worksheet): string[] => {
    const ids: string[] = [];
    for (let row = 3; row <= sheet.rowCount; row++) {
        const value = sheet.getRow(row).getCell(2).value;
        if (value !== null && value !== undefined && String(value) !== '') ids.push(String(value));
    }
    return ids;
};

describe('removeDeletedRegisterRows', () => {
    const REGISTERS = [
        { code: 'MZ_01', id: 'REG-1', event: 'Registration', status: 'CREATED' },
        { code: 'MZ_02', id: 'REG-2', event: 'Distribution', status: 'CREATED' },
        { code: 'MZ_03', id: 'REG-3', event: 'Distribution', status: 'CREATED' },
    ];

    it('removes a deleted register and closes the gap it leaves', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);

        expect(removeDeletedRegisterRows(workbook, new Set(['REG-2']))).toBe(1);
        expect(idsOf(sheet)).toEqual(['REG-1', 'REG-3']);
    });

    it('keeps each surviving row whole, not just its id', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);

        removeDeletedRegisterRows(workbook, new Set(['REG-1']));

        expect(sheet.getRow(3).values).toEqual(
            expect.arrayContaining(['MZ_02', 'REG-2', 'Distribution', 'CREATED'])
        );
    });

    it('removes every deleted register in one pass', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);

        expect(removeDeletedRegisterRows(workbook, new Set(['REG-1', 'REG-3']))).toBe(2);
        expect(idsOf(sheet)).toEqual(['REG-2']);
    });

    it('blanks the trailing row so the removed register leaves nothing behind', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);

        removeDeletedRegisterRows(workbook, new Set(['REG-3']));

        expect(sheet.getRow(5).getCell(1).value).toBeNull();
        expect(sheet.getRow(5).getCell(2).value).toBeNull();
    });

    it('reports nothing removed when no register was deleted', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);

        expect(removeDeletedRegisterRows(workbook, new Set(['REG-9']))).toBe(0);
        expect(idsOf(sheet)).toEqual(['REG-1', 'REG-2', 'REG-3']);
    });

    it('reports nothing removed for an empty deleted set', () => {
        const { workbook } = workbookWith(REGISTERS);

        expect(removeDeletedRegisterRows(workbook, new Set())).toBe(0);
    });

    it('empties the block when every register was deleted', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);

        expect(removeDeletedRegisterRows(workbook, new Set(['REG-1', 'REG-2', 'REG-3']))).toBe(3);
        expect(idsOf(sheet)).toEqual([]);
    });

    it('ignores the pre-formatted padding rows below the data', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);
        sheet.getRow(50).getCell(3).value = '';           // template padding, no register id

        expect(removeDeletedRegisterRows(workbook, new Set(['REG-2']))).toBe(1);
        expect(idsOf(sheet)).toEqual(['REG-1', 'REG-3']);
    });

    it('carries a formula cell across as its computed value, not the formula', () => {
        const { workbook, sheet } = workbookWith(REGISTERS);
        sheet.getRow(4).getCell(1).value = { formula: 'A3', result: 'MZ_02' } as any;

        removeDeletedRegisterRows(workbook, new Set(['REG-1']));

        expect(sheet.getRow(3).getCell(1).value).toBe('MZ_02');
    });

    it('matches the register id column by key, not by position', () => {
        const workbook = new ExcelJS.Workbook();
        const sheet = workbook.addWorksheet('list');
        sheet.getRow(1).values = ['#status#', 'HCM_ATTENDANCE_REGISTER_ID'];
        sheet.getRow(3).values = ['CREATED', 'REG-1'];
        sheet.getRow(4).values = ['CREATED', 'REG-2'];

        expect(removeDeletedRegisterRows(workbook, new Set(['REG-1']))).toBe(1);
        expect(String(sheet.getRow(3).getCell(2).value)).toBe('REG-2');
    });

    it('leaves a workbook with no register sheet untouched', () => {
        const workbook = new ExcelJS.Workbook();
        const sheet = workbook.addWorksheet('Boundary Data');
        sheet.getRow(1).values = ['HCM_ADMIN_CONSOLE_BOUNDARY_CODE'];
        sheet.getRow(3).values = ['MZ_01'];

        expect(removeDeletedRegisterRows(workbook, new Set(['REG-1']))).toBe(0);
        expect(sheet.getRow(3).getCell(1).value).toBe('MZ_01');
    });
});

describe('refresh orchestration', () => {
    const TENANT = 'dev' as any;
    const CAMPAIGN_NUMBER = 'CMP-2026-08-17-007802';

    const row = (overrides: Record<string, unknown> = {}) => ({
        id: 'res-1',
        type: 'attendanceRegister',
        campaignid: 'campaign-1',
        parentresourceid: null,
        status: 'completed',
        processedfilestoreid: 'processed-1',
        lastmodifiedby: 'user-1',
        additionaldetails: {},
        ...overrides,
    });

    const pendingRow = (overrides: Record<string, unknown> = {}) =>
        row({ additionaldetails: { attendanceRefresh: { state: 'pending', at: 1 } }, ...overrides });

    beforeEach(() => {
        jest.clearAllMocks();
        jest.mocked(getCampaignIdsByCampaignNumber).mockResolvedValue(['campaign-1'] as any);
        jest.mocked(searchResourceDetailsFromDB).mockResolvedValue([row()] as any);
        jest.mocked(getRelatedDataWithCampaign).mockResolvedValue([
            { uniqueIdentifier: 'REG-2', isDeleted: true },
        ] as any);
        jest.mocked(fetchFileFromFilestore).mockResolvedValue('http://filestore/processed-1' as any);
        jest.mocked(getExcelWorkbookFromFileURL).mockImplementation(async () =>
            workbookWith([
                { code: 'MZ_01', id: 'REG-1', event: 'Registration', status: 'CREATED' },
                { code: 'MZ_02', id: 'REG-2', event: 'Distribution', status: 'CREATED' },
            ]).workbook as any
        );
        jest.mocked(createAndUploadFileWithOutRequest).mockResolvedValue([{ fileStoreId: 'processed-2' }] as any);
        // campaignNumber lookup, then the claim, then the finish
        jest.mocked(executeQuery).mockImplementation(async (query: string) => {
            if (query.includes('SELECT campaignNumber')) return { rows: [{ campaignnumber: CAMPAIGN_NUMBER }] } as any;
            if (query.includes('RETURNING id')) return { rowCount: 1, rows: [{ id: 'res-1' }] } as any;
            return { rowCount: 1, rows: [] } as any;
        });
    });

    const queriesMatching = (fragment: string) =>
        jest.mocked(executeQuery).mock.calls.filter(([query]) => String(query).includes(fragment));

    describe('markRegisterSheetRefreshPending', () => {
        it('records the debt before any rewrite is attempted', async () => {
            await markRegisterSheetRefreshPending(TENANT, CAMPAIGN_NUMBER);

            const stateWrites = queriesMatching('jsonb_set');
            expect(stateWrites).toHaveLength(1);
            expect(String(stateWrites[0][1]?.[1])).toContain('"pending"');
        });

        it('does nothing when the campaign resolves to no id', async () => {
            jest.mocked(getCampaignIdsByCampaignNumber).mockResolvedValue([] as any);

            await markRegisterSheetRefreshPending(TENANT, CAMPAIGN_NUMBER);

            expect(searchResourceDetailsFromDB).not.toHaveBeenCalled();
        });
    });

    describe('the download that owes a refresh', () => {
        const owed = () => row({ additionaldetails: { attendanceRefresh: { state: 'pending', at: 1 } } });

        it('claims the work, rewrites the file and repoints the resource', async () => {
            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [owed()] as any);

            expect(queriesMatching('RETURNING id')).toHaveLength(1);   // the claim
            expect(createAndUploadFileWithOutRequest).toHaveBeenCalled();
            const finish = queriesMatching('processedFileStoreId = $4');
            expect(finish).toHaveLength(1);
            expect(finish[0][1]).toContain('processed-2');
            expect(refreshed.get('res-1')).toBe('processed-2');
        });

        it('keeps serving the existing file when it needs no change', async () => {
            jest.mocked(getRelatedDataWithCampaign).mockResolvedValue([
                { uniqueIdentifier: 'REG-9', isDeleted: true },
            ] as any);

            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [owed()] as any);

            expect(createAndUploadFileWithOutRequest).not.toHaveBeenCalled();
            expect(queriesMatching('processedFileStoreId = $4')).toHaveLength(0);
            expect(queriesMatching('#-')).toHaveLength(1);          // flag cleared
            expect(refreshed.get('res-1')).toBe('processed-1');     // already correct, so servable
        });

        it('drops the marker when no file has been served yet, so it cannot sit there for good', async () => {
            const noFile = row({
                processedfilestoreid: null,
                additionaldetails: { attendanceRefresh: { state: 'pending', at: 1 } },
            });

            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [noFile] as any);

            expect(queriesMatching('RETURNING id')).toHaveLength(0);  // nothing to claim
            expect(queriesMatching('#-')).toHaveLength(1);            // marker cleared
            expect(refreshed.get('res-1')).toBeNull();
        });

        it('withholds the file and hands the claim back when the rewrite fails', async () => {
            jest.mocked(createAndUploadFileWithOutRequest).mockResolvedValue([] as any);

            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [owed()] as any);

            const handBack = queriesMatching('jsonb_set').filter(([, values]) =>
                String(values?.[1]).includes('"pending"'));
            expect(handBack).toHaveLength(1);
            // Fail closed: a sheet known to still list a deleted register is never handed over
            expect(refreshed.get('res-1')).toBeNull();
        });
    });

    describe('completeOwedRegisterSheetRefresh', () => {
        it('finishes a pending refresh and reports the new file id', async () => {
            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [pendingRow()] as any);

            expect(refreshed.get('res-1')).toBe('processed-2');
        });

        it('ignores a row with no refresh owed', async () => {
            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [row()] as any);

            expect(refreshed.size).toBe(0);
            expect(fetchFileFromFilestore).not.toHaveBeenCalled();
        });

        it('never rewrites a file another worker is already rewriting', async () => {
            const inProgress = row({
                additionaldetails: { attendanceRefresh: { state: 'inProgress', at: Date.now() } },
            });
            // Claim is lost, so the reader may only wait — never duplicate the work
            jest.mocked(executeQuery).mockImplementation(async (query: string) => {
                if (query.includes('RETURNING id')) return { rowCount: 0, rows: [] } as any;
                return { rows: [{ campaignnumber: CAMPAIGN_NUMBER }] } as any;
            });

            await completeOwedRegisterSheetRefresh(TENANT, [inProgress] as any);

            expect(queriesMatching('RETURNING id')).toHaveLength(1); // one attempt, then it waits
            expect(createAndUploadFileWithOutRequest).not.toHaveBeenCalled();
        });

        it('takes over a claim whose owner died, so the flag cannot stick', async () => {
            const abandoned = row({
                additionaldetails: { attendanceRefresh: { state: 'inProgress', at: 1 } },
            });

            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [abandoned] as any);

            expect(refreshed.get('res-1')).toBe('processed-2');
        });

        it('ignores rows of other resource types', async () => {
            const other = pendingRow({ type: 'user' });

            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [other] as any);

            expect(refreshed.size).toBe(0);
        });

        it('still answers the search when a refresh throws, withholding the stale file', async () => {
            jest.mocked(fetchFileFromFilestore).mockRejectedValue(new Error('filestore down'));

            const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [pendingRow()] as any);

            expect(refreshed.get('res-1')).toBeNull();
        });
    });
});

describe('a download landing while the rewrite runs elsewhere', () => {
    const TENANT = 'dev' as any;

    const claimedRow = () => ({
        id: 'res-1',
        type: 'attendanceRegister',
        campaignid: 'campaign-1',
        parentresourceid: null,
        status: 'completed',
        processedfilestoreid: 'processed-1',
        lastmodifiedby: 'user-1',
        additionaldetails: { attendanceRefresh: { state: 'inProgress', at: Date.now() } },
    });

    beforeEach(() => {
        jest.clearAllMocks();
        // The claim is always lost: another worker owns this refresh
        jest.mocked(executeQuery).mockImplementation(async (query: string) => {
            if (query.includes('RETURNING id')) return { rowCount: 0, rows: [] } as any;
            return { rowCount: 1, rows: [{ campaignnumber: 'CMP-1' }] } as any;
        });
    });

    it('waits for the other worker and answers with the file it produced', async () => {
        jest.mocked(getResourceDetailById)
            .mockResolvedValueOnce({ ...claimedRow() } as any)                                   // still running
            .mockResolvedValueOnce({ ...claimedRow(), additionaldetails: {}, processedfilestoreid: 'processed-2' } as any);

        const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [claimedRow()] as any);

        expect(refreshed.get('res-1')).toBe('processed-2');
        expect(fetchFileFromFilestore).not.toHaveBeenCalled(); // never rewrote it twice
    });

    it('withholds the stale file when the wait runs out', async () => {
        jest.mocked(getResourceDetailById).mockResolvedValue(claimedRow() as any);

        const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [claimedRow()] as any);

        expect(refreshed.get('res-1')).toBeNull();
    });

    it('takes the work over when the other worker hands it back as pending', async () => {
        jest.mocked(getResourceDetailById).mockResolvedValue({
            ...claimedRow(),
            additionaldetails: { attendanceRefresh: { state: 'pending', at: Date.now() } },
        } as any);
        // First claim lost, second (after the hand-back) won
        let claims = 0;
        jest.mocked(executeQuery).mockImplementation(async (query: string) => {
            if (query.includes('RETURNING id')) {
                claims += 1;
                return { rowCount: claims === 1 ? 0 : 1, rows: claims === 1 ? [] : [{ id: 'res-1' }] } as any;
            }
            return { rowCount: 1, rows: [{ campaignnumber: 'CMP-1' }] } as any;
        });
        jest.mocked(getRelatedDataWithCampaign).mockResolvedValue([
            { uniqueIdentifier: 'REG-2', isDeleted: true },
        ] as any);
        jest.mocked(fetchFileFromFilestore).mockResolvedValue('http://filestore/processed-1' as any);
        jest.mocked(getExcelWorkbookFromFileURL).mockImplementation(async () =>
            workbookWith([
                { code: 'MZ_01', id: 'REG-1', event: 'Registration', status: 'CREATED' },
                { code: 'MZ_02', id: 'REG-2', event: 'Distribution', status: 'CREATED' },
            ]).workbook as any
        );
        jest.mocked(createAndUploadFileWithOutRequest).mockResolvedValue([{ fileStoreId: 'processed-3' }] as any);

        const refreshed = await completeOwedRegisterSheetRefresh(TENANT, [claimedRow()] as any);

        expect(refreshed.get('res-1')).toBe('processed-3');
    });
});
