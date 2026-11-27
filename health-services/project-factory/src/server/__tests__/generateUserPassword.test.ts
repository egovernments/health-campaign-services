import { generateUserPassword } from '../api/campaignApis';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn(),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../utils/redisUtils', () => ({
    getCache: jest.fn().mockResolvedValue(null),
    setCache: jest.fn().mockResolvedValue(undefined),
    deleteCache: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../kafka/Producer', () => ({
    producer: { connect: jest.fn(), send: jest.fn() },
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: { facilityHost: 'http://facility/' },
        paths: { facilitySearch: 'facility/v1/_search' },
        facility: { searchBatchSize: 50 },
        DB_CONFIG: {
            DB_USER: 'u', DB_HOST: 'h', DB_NAME: 'n', DB_PASSWORD: 'p', DB_PORT: '5432',
        },
    },
}));

const SAMPLES = 5000;
const DEPLOYED_EGOV_USER_PATTERN = /((?=.*\d)(?=.*[a-z])(?=.*[@#$%])(?=\S+$).*$)/;
const DEFAULT_EGOV_USER_PATTERN = /((?=.*\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%])(?=\S+$).*$)/;

describe('generateUserPassword', () => {
    const passwords = Array.from({ length: SAMPLES }, () => generateUserPassword());

    it('returns 12 characters, inside the egov-user length rule of 8 to 15', () => {
        passwords.forEach((password) => expect(password).toHaveLength(12));
    });

    it('matches the egov-user password pattern set on dev and production', () => {
        passwords.forEach((password) => expect(password).toMatch(DEPLOYED_EGOV_USER_PATTERN));
    });

    it('matches the egov-user default pattern, which also needs an uppercase letter', () => {
        passwords.forEach((password) => expect(password).toMatch(DEFAULT_EGOV_USER_PATTERN));
    });

    it('never starts with @, so a spreadsheet cannot read it as a formula', () => {
        passwords.forEach((password) => expect(password.startsWith('@')).toBe(false));
    });

    it('leaves out the look-alike characters 0, O, 1, l and I', () => {
        passwords.forEach((password) => expect(password).not.toMatch(/[0O1lI]/));
    });

    it('does not repeat across many calls', () => {
        expect(new Set(passwords).size).toBe(SAMPLES);
    });

    it('does not use Math.random', () => {
        const spy = jest.spyOn(Math, 'random');
        generateUserPassword();
        expect(spy).not.toHaveBeenCalled();
        spy.mockRestore();
    });
});
