/**
 * Tests for decrypt() guard in cryptUtils.ts
 * Fix: decrypt() must not crash (Buffer.from(undefined)) on non-encrypted / undefined input.
 * Adopted (already-in-HRMS) user rows carry a plaintext username and no generated password, so
 * credential-sheet generation calls decrypt() with such values — it must pass them through, not throw.
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────
jest.mock('../config', () => ({
    __esModule: true,
    default: { basesecret: 'unit-test-base-secret' },
}));

import { encrypt, decrypt } from '../utils/cryptUtils';

describe('decrypt guard', () => {
    describe('non-encrypted / missing input is passed through (no crash)', () => {
        it('returns "" for undefined', () => {
            expect(decrypt(undefined as unknown as string)).toBe('');
        });
        it('returns "" for empty string', () => {
            expect(decrypt('')).toBe('');
        });
        it('returns a plaintext value (no ":") as-is', () => {
            expect(decrypt('testuser1')).toBe('testuser1');
        });
        it('does not throw on a plaintext username', () => {
            expect(() => decrypt('Test User 1')).not.toThrow();
        });
    });

    describe('real ciphertext still round-trips', () => {
        it('decrypt(encrypt(x)) === x', () => {
            const secret = 'hunter2-password';
            const cipher = encrypt(secret);
            expect(cipher).toContain(':');            // encrypt yields ivB64:cipherB64
            expect(cipher).not.toBe(secret);
            expect(decrypt(cipher)).toBe(secret);
        });
    });
});
