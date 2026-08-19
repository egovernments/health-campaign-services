jest.mock('../config', () => ({
  __esModule: true,
  default: { persistenceWaitTimeMs: 0 },
}));

import config from '../config';
import { getPersistenceWaitTime } from '../utils/persistenceWaitUtils';

describe('getPersistenceWaitTime', () => {
  afterEach(() => {
    (config as any).persistenceWaitTimeMs = 0;
  });

  describe('when PERSISTENCE_WAIT_TIME_MS is unset (0)', () => {
    it('returns the 5000 ms floor for small counts', () => {
      expect(getPersistenceWaitTime(10)).toBe(5000);
    });

    it('returns count * 8 when it exceeds the floor', () => {
      expect(getPersistenceWaitTime(1000)).toBe(8000);
    });

    it('returns the floor at the exact break-even count', () => {
      expect(getPersistenceWaitTime(625)).toBe(5000);
    });

    it('returns the floor for zero count', () => {
      expect(getPersistenceWaitTime(0)).toBe(5000);
    });
  });

  describe('when PERSISTENCE_WAIT_TIME_MS is set', () => {
    it('returns the flat override for small counts', () => {
      (config as any).persistenceWaitTimeMs = 10000;
      expect(getPersistenceWaitTime(10)).toBe(10000);
    });

    it('returns the flat override even when the formula would be larger', () => {
      (config as any).persistenceWaitTimeMs = 2000;
      expect(getPersistenceWaitTime(100000)).toBe(2000);
    });

    it('returns the flat override for zero count', () => {
      (config as any).persistenceWaitTimeMs = 7000;
      expect(getPersistenceWaitTime(0)).toBe(7000);
    });
  });
});
