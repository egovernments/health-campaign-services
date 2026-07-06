/**
 * R8 security tests — getTableName tenantId→schema SQL-identifier hardening (boundary-management).
 *
 * Identical logic to the project-factory fix: in central-instance mode the schema segment derived
 * from tenantId is interpolated RAW into the SQL FROM identifier, so it must match
 * /^[A-Za-z_][A-Za-z0-9_]{0,62}$/ or getTableName throws INVALID_TENANT_ID. Only the first
 * dot-segment (tenantId.split('.')[0]) becomes the schema. Reachable via POST /v1/_process-search.
 */

jest.mock('../config', () => ({
  __esModule: true,
  default: { isEnvironmentCentralInstance: true, DB_CONFIG: { DB_SCHEMA: 'health' } },
}));
jest.mock('../config/dbPoolConfig', () => ({ __esModule: true, default: { query: jest.fn() } }));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
  getFormattedStringForDebug: (x: any) => JSON.stringify(x),
}));
jest.mock('../utils/genericUtils', () => ({
  throwError: (_m: string, status: number, code: string, description?: string) => {
    const e: any = new Error(description || code);
    e.code = code;
    e.status = status;
    throw e;
  },
}));

import config from '../config';
import { getTableName } from '../utils/db';

const TABLE = 'eg_bm_processed_template';

function expectRejected(tenantId: string) {
  let err: any;
  try { getTableName(TABLE, tenantId); } catch (e) { err = e; }
  expect(err).toBeDefined();
  expect(err.status).toBe(400);                       // rejected as a client error, never reaches SQL
  expect(String(err.message)).toMatch(/Invalid tenantId/i);
}

describe('R8 · getTableName tenantId→schema hardening (boundary-management)', () => {
  beforeEach(() => {
    (config as any).isEnvironmentCentralInstance = true;
    (config as any).DB_CONFIG = { DB_SCHEMA: 'health' };
  });

  describe('central-instance — POSITIVE', () => {
    const valid: Array<[string, string]> = [
      ['ng', `ng.${TABLE}`],
      ['ng.kaduna', `ng.${TABLE}`],
      ['oy', `oy.${TABLE}`],
      ['mz.maputo.zone', `mz.${TABLE}`],
      ['_internal', `_internal.${TABLE}`],
      ['A1_b2', `A1_b2.${TABLE}`],
      ['a', `a.${TABLE}`],
      ['a'.repeat(63), `${'a'.repeat(63)}.${TABLE}`],
    ];
    it.each(valid)('accepts %p → %p', (tenantId, expected) => {
      expect(getTableName(TABLE, tenantId)).toBe(expected);
    });
  });

  describe('central-instance — NEGATIVE (throws INVALID_TENANT_ID)', () => {
    const bad: Array<[string, string]> = [
      ['UNION payload (space)', 't WHERE false UNION SELECT 1 --'],
      ['derived-table break-out', 'x) z WHERE false UNION SELECT 1 --'],
      ['stacked DROP', 'ng; DROP TABLE eg_bm_processed_template'],
      ['single quote', "ng'"],
      ['double quote', 'ng"'],
      ['line comment', 'ng--'],
      ['block comment', 'ng/*c*/'],
      ['embedded space', 'ng kaduna'],
      ['hyphen', 'ng-kaduna'],
      ['trailing newline', 'ng\n'],
      ['embedded newline', 'ng\nDROP'],
      ['tab', 'ng\t'],
      ['carriage return', 'ng\r'],
      ['leading space', ' ng'],
      ['starts with digit', '1ng'],
      ['digit only', '9'],
      ['empty string', ''],
      ['leading dot → empty seg', '.ng'],
      ['64 chars (over max)', 'a'.repeat(64)],
      ['dollar sign', 'pg_catalog$x'],
      ['unicode (Armenian)', 'ngա'],
      ['fullwidth homoglyph', 'ｎg'],
      ['backslash', 'ng\\x'],
      ['parenthesis', 'ng(1)'],
      ['percent', 'ng%'],
    ];
    it.each(bad)('rejects %s', (_label, tenantId) => {
      expectRejected(tenantId);
    });
  });

  describe('central-instance — by design (first dot-segment only)', () => {
    it('reduces "public.<junk>" to validated "public"', () => {
      expect(getTableName(TABLE, "public.anything WHERE 1=1 -- ")).toBe(`public.${TABLE}`);
    });
    it('rejects a malicious FIRST segment regardless of later segments', () => {
      expectRejected("ev il.ng");
    });
  });

  describe('non-central mode — tenantId ignored (injection inert)', () => {
    it('uses DB_SCHEMA', () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).DB_CONFIG = { DB_SCHEMA: 'health' };
      expect(getTableName(TABLE, 'ng')).toBe(`health.${TABLE}`);
    });
    it('maps "egov" → "public"', () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).DB_CONFIG = { DB_SCHEMA: 'egov' };
      expect(getTableName(TABLE, 'ng')).toBe(`public.${TABLE}`);
    });
    it('ignores an injection tenantId (no throw)', () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).DB_CONFIG = { DB_SCHEMA: 'health' };
      expect(getTableName(TABLE, "t WHERE false UNION SELECT 1 --")).toBe(`health.${TABLE}`);
    });
  });
});
