// Tests for Zod schema cross-field validation — Bugs 1+4
// SC-1..SC-10: both campaignId and campaignNumber are optional individually,
// but at least one must be present (superRefine enforcement)

import { resourceDetailsCreateSchema } from '../config/models/resourceDetailsCreateSchema';
import { resourceDetailsUpdateSchema } from '../config/models/resourceDetailsUpdateSchema';
import { resourceDetailsCriteriaSchema } from '../config/models/resourceDetailsCriteria';

// ── resourceDetailsCreateSchema ──────────────────────────────────────────────

describe('resourceDetailsCreateSchema', () => {
  const base = { tenantId: 'ng', type: 'user', fileStoreId: 'fs-1' };

  test('rejects when both campaignId and campaignNumber absent', () => {
    const result = resourceDetailsCreateSchema.safeParse(base);
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map(i => i.message).join(' ');
      expect(messages).toMatch(/campaignId|campaignNumber/i);
    }
  });

  test('accepts when only campaignId provided', () => {
    const result = resourceDetailsCreateSchema.safeParse({ ...base, campaignId: 'uuid-1' });
    expect(result.success).toBe(true);
  });

  test('accepts when only campaignNumber provided', () => {
    const result = resourceDetailsCreateSchema.safeParse({ ...base, campaignNumber: 'HCM-001' });
    expect(result.success).toBe(true);
  });

  test('accepts when both campaignId and campaignNumber provided', () => {
    const result = resourceDetailsCreateSchema.safeParse({ ...base, campaignId: 'uuid-1', campaignNumber: 'HCM-001' });
    expect(result.success).toBe(true);
  });

  test('rejects when campaignId is empty string (min 1)', () => {
    const result = resourceDetailsCreateSchema.safeParse({ ...base, campaignId: '' });
    expect(result.success).toBe(false);
  });
});

// ── resourceDetailsUpdateSchema ──────────────────────────────────────────────

describe('resourceDetailsUpdateSchema', () => {
  const base = { id: 'res-1', tenantId: 'ng', fileStoreId: 'fs-2' };

  test('rejects when both campaignId and campaignNumber absent', () => {
    const result = resourceDetailsUpdateSchema.safeParse(base);
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map(i => i.message).join(' ');
      expect(messages).toMatch(/campaignId|campaignNumber/i);
    }
  });

  test('accepts when only campaignId provided', () => {
    const result = resourceDetailsUpdateSchema.safeParse({ ...base, campaignId: 'uuid-1' });
    expect(result.success).toBe(true);
  });

  test('accepts when only campaignNumber provided', () => {
    const result = resourceDetailsUpdateSchema.safeParse({ ...base, campaignNumber: 'HCM-001' });
    expect(result.success).toBe(true);
  });
});

// ── resourceDetailsCriteriaSchema ────────────────────────────────────────────

describe('resourceDetailsCriteriaSchema', () => {
  const base = { tenantId: 'ng' };

  test('rejects when campaignId, campaignNumber, AND ids all absent', () => {
    const result = resourceDetailsCriteriaSchema.safeParse(base);
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map(i => i.message).join(' ');
      expect(messages).toMatch(/campaignId|campaignNumber|ids/i);
    }
  });

  test('accepts when only campaignId provided', () => {
    const result = resourceDetailsCriteriaSchema.safeParse({ ...base, campaignId: 'uuid-1' });
    expect(result.success).toBe(true);
  });

  test('accepts when only campaignNumber provided', () => {
    const result = resourceDetailsCriteriaSchema.safeParse({ ...base, campaignNumber: 'HCM-001' });
    expect(result.success).toBe(true);
  });

  test('accepts when only ids provided (no campaign identifier needed)', () => {
    const result = resourceDetailsCriteriaSchema.safeParse({ ...base, ids: ['res-1'] });
    expect(result.success).toBe(true);
  });

  test('rejects when ids is an empty array (no effective identifier)', () => {
    const result = resourceDetailsCriteriaSchema.safeParse({ ...base, ids: [] });
    expect(result.success).toBe(false);
  });
});
