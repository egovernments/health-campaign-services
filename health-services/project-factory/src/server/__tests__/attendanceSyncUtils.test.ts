jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

jest.mock('../utils/db', () => ({
    executeQuery: jest.fn(),
    getTableName: jest.fn((table: string, tenantId: string) => `${tenantId.split('.')[0]}.${table}`),
}));

import {
    handleAttendanceRegisterDelete,
    handleAttendanceAttendeeDeEnrolment,
    handleAttendanceStaffDeEnrolment,
} from '../utils/attendanceSyncUtils';
import { executeQuery } from '../utils/db';
import { logger } from '../utils/logger';

const mockExecuteQuery = jest.mocked(executeQuery);

const register = (id: string, tenantId = 'dev') => ({ id, tenantId, isDeleted: true, status: 'INACTIVE' });

describe('handleAttendanceRegisterDelete', () => {
    afterEach(() => jest.clearAllMocks());

    describe('message shape', () => {
        it('skips a message with no attendanceRegister array', async () => {
            await handleAttendanceRegisterDelete({});
            expect(mockExecuteQuery).not.toHaveBeenCalled();
        });

        it('skips a message with an empty attendanceRegister array', async () => {
            await handleAttendanceRegisterDelete({ attendanceRegister: [] });
            expect(mockExecuteQuery).not.toHaveBeenCalled();
        });

        it('skips entries missing an id or tenantId rather than issuing a bad update', async () => {
            await handleAttendanceRegisterDelete({
                attendanceRegister: [{ tenantId: 'dev' }, { id: 'reg-1' }, { id: '  ', tenantId: 'dev' }],
            });
            expect(mockExecuteQuery).not.toHaveBeenCalled();
            expect(logger.warn).toHaveBeenCalled();
        });
    });

    describe('marking rows deleted', () => {
        it('matches on register id alone, since the event carries no campaignNumber', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 1 });

            await handleAttendanceRegisterDelete({ attendanceRegister: [register('reg-1')] });

            const [query, values] = mockExecuteQuery.mock.calls[0];
            expect(query).toContain('SET isDeleted = true');
            expect(query).toContain('type = $1');
            expect(query).toContain('uniqueIdAfterProcess = ANY($2)');
            expect(query).not.toContain('campaignNumber');
            expect(values).toEqual(['attendanceRegister', ['reg-1']]);
        });

        it('issues one update per tenant rather than one per register', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 2 });

            await handleAttendanceRegisterDelete({
                attendanceRegister: [register('reg-1'), register('reg-2'), register('reg-3', 'mz')],
            });

            expect(mockExecuteQuery).toHaveBeenCalledTimes(2);
            expect(mockExecuteQuery.mock.calls[0][1]).toEqual(['attendanceRegister', ['reg-1', 'reg-2']]);
            expect(mockExecuteQuery.mock.calls[1][1]).toEqual(['attendanceRegister', ['reg-3']]);
        });

        it('routes the update to the tenant schema', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 1 });

            await handleAttendanceRegisterDelete({ attendanceRegister: [register('reg-1', 'mz.moz')] });

            expect(mockExecuteQuery.mock.calls[0][0]).toContain('mz.');
        });

        it('warns when a register had no row to mark, so a lost deletion is visible', async () => {
            // campaign_data is written asynchronously, so a delete can arrive before our row exists.
            mockExecuteQuery.mockResolvedValue({ rowCount: 0 });

            await handleAttendanceRegisterDelete({ attendanceRegister: [register('reg-1')] });

            expect(logger.warn).toHaveBeenCalledWith(expect.stringContaining('no campaign_data row to mark'));
        });

        it('does not warn when every register matched a row', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 2 });

            await handleAttendanceRegisterDelete({
                attendanceRegister: [register('reg-1'), register('reg-2')],
            });

            expect(logger.warn).not.toHaveBeenCalled();
        });

        it('is idempotent - redelivery reissues the same update', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 1 });
            const message = { attendanceRegister: [register('reg-1')] };

            await handleAttendanceRegisterDelete(message);
            await handleAttendanceRegisterDelete(message);

            expect(mockExecuteQuery).toHaveBeenCalledTimes(2);
            expect(mockExecuteQuery.mock.calls[0][1]).toEqual(mockExecuteQuery.mock.calls[1][1]);
        });
    });
});

describe('de-enrolment consumers', () => {
    afterEach(() => jest.clearAllMocks());

    const FUTURE = 4102444800000; // year 2100

    describe('discriminating de-enrolments from tag edits', () => {
        it('ignores attendee entries with no de-enrolment date, since the topic also carries tag edits', async () => {
            await handleAttendanceAttendeeDeEnrolment({
                attendees: [{ tenantId: 'dev', registerId: 'reg-1', individualId: 'ind-1', tag: 'TEAM_A' }],
            });
            expect(mockExecuteQuery).not.toHaveBeenCalled();
        });

        it('ignores a zero or non-numeric de-enrolment date', async () => {
            await handleAttendanceAttendeeDeEnrolment({
                attendees: [
                    { tenantId: 'dev', registerId: 'reg-1', individualId: 'ind-1', denrollmentDate: 0 },
                    { tenantId: 'dev', registerId: 'reg-1', individualId: 'ind-2', denrollmentDate: 'soon' },
                ],
            });
            expect(mockExecuteQuery).not.toHaveBeenCalled();
        });

        it('skips entries missing tenantId, registerId or person id', async () => {
            await handleAttendanceAttendeeDeEnrolment({
                attendees: [{ registerId: 'reg-1', individualId: 'ind-1', denrollmentDate: FUTURE }],
            });
            expect(mockExecuteQuery).not.toHaveBeenCalled();
            expect(logger.warn).toHaveBeenCalled();
        });
    });

    describe('recording the date', () => {
        it('stores the date rather than a deleted flag, so a future removal stays active until then', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 1 });

            await handleAttendanceAttendeeDeEnrolment({
                attendees: [{ tenantId: 'dev', registerId: 'reg-1', individualId: 'ind-1', denrollmentDate: FUTURE }],
            });

            const [query, values] = mockExecuteQuery.mock.calls[0];
            expect(query).toContain('SET denrollmentDate = $1');
            expect(query).not.toContain('isDeleted');
            expect(values[0]).toBe(FUTURE);
            expect(values[1]).toBe('attendanceRegisterAttendee');
            expect(values[2]).toEqual(['reg-1_ind-1_worker']);
        });

        it('keys staff by userId and maps staff type onto the marker sheet', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 1 });

            await handleAttendanceStaffDeEnrolment({
                staff: [{ tenantId: 'dev', registerId: 'reg-1', userId: 'usr-1', staffType: 'OWNER', denrollmentDate: FUTURE }],
            });

            expect(mockExecuteQuery.mock.calls[0][1][2]).toEqual(['reg-1_usr-1_marker']);
        });

        it('maps APPROVER staff onto the approver sheet', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 1 });

            await handleAttendanceStaffDeEnrolment({
                staff: [{ tenantId: 'dev', registerId: 'reg-1', userId: 'usr-1', staffType: 'APPROVER', denrollmentDate: FUTURE }],
            });

            expect(mockExecuteQuery.mock.calls[0][1][2]).toEqual(['reg-1_usr-1_approver']);
        });

        it('groups a bulk de-enrolment sharing one date into a single update', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 2 });

            await handleAttendanceAttendeeDeEnrolment({
                attendees: [
                    { tenantId: 'dev', registerId: 'reg-1', individualId: 'ind-1', denrollmentDate: FUTURE },
                    { tenantId: 'dev', registerId: 'reg-1', individualId: 'ind-2', denrollmentDate: FUTURE },
                ],
            });

            expect(mockExecuteQuery).toHaveBeenCalledTimes(1);
            expect(mockExecuteQuery.mock.calls[0][1][2]).toEqual(['reg-1_ind-1_worker', 'reg-1_ind-2_worker']);
        });

        it('warns when a row was not found, so pre-existing attendees are visible as unmatched', async () => {
            mockExecuteQuery.mockResolvedValue({ rowCount: 0 });

            await handleAttendanceAttendeeDeEnrolment({
                attendees: [{ tenantId: 'dev', registerId: 'reg-1', individualId: 'ind-1', denrollmentDate: FUTURE }],
            });

            expect(logger.warn).toHaveBeenCalledWith(expect.stringContaining('not found'));
        });
    });
});

describe('failure isolation across tenants', () => {
    afterEach(() => jest.clearAllMocks());

    it('still processes later tenants when one tenant update fails, then rethrows', async () => {
        // The consumer commits the offset regardless, so swallowing the error would lose the event.
        mockExecuteQuery
            .mockRejectedValueOnce(new Error('connection reset'))
            .mockResolvedValueOnce({ rowCount: 1 });

        await expect(handleAttendanceRegisterDelete({
            attendanceRegister: [register('reg-1', 'dev'), register('reg-2', 'mz')],
        })).rejects.toThrow(/1 of 2 group\(s\) failed/);

        expect(mockExecuteQuery).toHaveBeenCalledTimes(2);
        expect(logger.error).toHaveBeenCalledWith(expect.stringContaining('continuing with the rest'));
    });

    it('does not throw when every group succeeds', async () => {
        mockExecuteQuery.mockResolvedValue({ rowCount: 1 });

        await expect(handleAttendanceRegisterDelete({
            attendanceRegister: [register('reg-1', 'dev'), register('reg-2', 'mz')],
        })).resolves.toBeUndefined();
    });
});
