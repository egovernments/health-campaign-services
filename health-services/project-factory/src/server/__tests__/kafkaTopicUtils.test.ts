import { getTopicName, getConsumerTopicPattern, stripTopicPrefix, validateConsumerTopicPrefix, parseTenantIds, getStartupTopicsToCreate, getEffectiveConsumerPrefix } from '../utils/kafkaTopicUtils';

jest.mock('../config', () => ({
  default: {
    isEnvironmentCentralInstance: false,
    kafkaConsumerTopicPrefix: '',
    centralInstanceTenantIds: '',
    kafka: { KAFKA_NON_CENTRAL_INSTANCE_TOPICS: '' },
  },
  __esModule: true,
}));

import config from '../config';

describe('kafkaTopicUtils', () => {
  afterEach(() => {
    (config as any).isEnvironmentCentralInstance = false;
    (config as any).kafkaConsumerTopicPrefix = '';
    (config as any).centralInstanceTenantIds = '';
    (config as any).kafka.KAFKA_NON_CENTRAL_INSTANCE_TOPICS = '';
  });

  // -------------------------------------------------------------------
  // getTopicName (PRODUCER side — unchanged)
  // -------------------------------------------------------------------

  describe('getTopicName (producer)', () => {
    describe('when central instance is ENABLED', () => {
      beforeEach(() => {
        (config as any).isEnvironmentCentralInstance = true;
      });

      it('should prefix with state code from dotted tenantId', () => {
        expect(getTopicName('hcm-mapping-batch', 'ng.kaduna')).toBe('ng-hcm-mapping-batch');
      });

      it('should prefix with tenantId when no dot present', () => {
        expect(getTopicName('hcm-mapping-batch', 'ng')).toBe('ng-hcm-mapping-batch');
      });

      it('should extract first segment from multi-dot tenantId', () => {
        expect(getTopicName('save-sheet-data', 'ng.kaduna.ward1')).toBe('ng-save-sheet-data');
      });

      it('should handle topic names containing dots', () => {
        expect(getTopicName('egov.core.notification.email', 'ng.kaduna')).toBe('ng-egov.core.notification.email');
      });

      it('should handle empty tenantId', () => {
        expect(getTopicName('hcm-mapping-batch', '')).toBe('-hcm-mapping-batch');
      });
    });

    describe('when central instance is DISABLED', () => {
      beforeEach(() => {
        (config as any).isEnvironmentCentralInstance = false;
      });

      it('should return topic unchanged with dotted tenantId', () => {
        expect(getTopicName('hcm-mapping-batch', 'ng.kaduna')).toBe('hcm-mapping-batch');
      });

      it('should return topic unchanged with single-segment tenantId', () => {
        expect(getTopicName('hcm-mapping-batch', 'ng')).toBe('hcm-mapping-batch');
      });
    });
  });

  // -------------------------------------------------------------------
  // getConsumerTopicPattern (CONSUMER side — subscription)
  // -------------------------------------------------------------------

  describe('getConsumerTopicPattern', () => {
    it('should return exact topic string when prefix is empty', () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      const result = getConsumerTopicPattern('hcm-mapping-batch');
      expect(result).toBe('hcm-mapping-batch');
      expect(typeof result).toBe('string');
    });

    it('should return RegExp when prefix is set (single state)', () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      const result = getConsumerTopicPattern('hcm-mapping-batch');
      expect(result).toBeInstanceOf(RegExp);
      expect((result as RegExp).test('ng-hcm-mapping-batch')).toBe(true);
      expect((result as RegExp).test('hcm-mapping-batch')).toBe(false);
    });

    it('should return RegExp matching multiple states when prefix is regex group', () => {
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke|cg)-';
      const result = getConsumerTopicPattern('hcm-mapping-batch');
      expect(result).toBeInstanceOf(RegExp);
      expect((result as RegExp).test('ba-hcm-mapping-batch')).toBe(true);
      expect((result as RegExp).test('ke-hcm-mapping-batch')).toBe(true);
      expect((result as RegExp).test('cg-hcm-mapping-batch')).toBe(true);
      expect((result as RegExp).test('ng-hcm-mapping-batch')).toBe(false);
      expect((result as RegExp).test('hcm-mapping-batch')).toBe(false);
    });

    it('should escape special regex chars in topic name', () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      const result = getConsumerTopicPattern('egov.core.notification.email');
      expect(result).toBeInstanceOf(RegExp);
      expect((result as RegExp).test('ng-egov.core.notification.email')).toBe(true);
      // dots in topic should not match arbitrary chars
      expect((result as RegExp).test('ng-egovXcoreXnotificationXemail')).toBe(false);
    });

    it('should match exact topic — no partial matches', () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      const result = getConsumerTopicPattern('hcm-mapping-batch');
      expect((result as RegExp).test('ng-hcm-mapping-batch-extra')).toBe(false);
      expect((result as RegExp).test('prefix-ng-hcm-mapping-batch')).toBe(false);
    });
  });

  // -------------------------------------------------------------------
  // stripTopicPrefix (CONSUMER side — routing)
  // -------------------------------------------------------------------

  describe('stripTopicPrefix', () => {
    it('should return topic unchanged when prefix is empty', () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      expect(stripTopicPrefix('hcm-mapping-batch')).toBe('hcm-mapping-batch');
    });

    it('should strip single-state prefix', () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      expect(stripTopicPrefix('ng-hcm-mapping-batch')).toBe('hcm-mapping-batch');
    });

    it('should strip multi-state regex prefix', () => {
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke|cg)-';
      expect(stripTopicPrefix('ba-hcm-mapping-batch')).toBe('hcm-mapping-batch');
      expect(stripTopicPrefix('ke-hcm-mapping-batch')).toBe('hcm-mapping-batch');
      expect(stripTopicPrefix('cg-hcm-mapping-batch')).toBe('hcm-mapping-batch');
    });

    it('should return topic unchanged if prefix does not match', () => {
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke)-';
      expect(stripTopicPrefix('ng-hcm-mapping-batch')).toBe('ng-hcm-mapping-batch');
    });

    it('should handle topic with dots after stripping prefix', () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      expect(stripTopicPrefix('ng-egov.core.notification.email')).toBe('egov.core.notification.email');
    });
  });

  // -------------------------------------------------------------------
  // validateConsumerTopicPrefix (startup validation)
  // -------------------------------------------------------------------

  describe('validateConsumerTopicPrefix', () => {
    it('should throw when central instance enabled but neither prefix nor tenant ids set', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';
      (config as any).centralInstanceTenantIds = '';
      expect(() => validateConsumerTopicPrefix()).toThrow('CENTRAL_INSTANCE_TENANT_IDS');
    });

    it('should NOT throw when central instance enabled and explicit prefix is set', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke)-';
      expect(() => validateConsumerTopicPrefix()).not.toThrow();
    });

    it('should NOT throw when central instance enabled and tenant ids are set', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';
      (config as any).centralInstanceTenantIds = 'ba,ke';
      expect(() => validateConsumerTopicPrefix()).not.toThrow();
    });

    it('should NOT throw when central instance is disabled regardless of prefix', () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).kafkaConsumerTopicPrefix = '';
      expect(() => validateConsumerTopicPrefix()).not.toThrow();
    });
  });

  // -------------------------------------------------------------------
  // parseTenantIds
  // -------------------------------------------------------------------

  describe('parseTenantIds', () => {
    it('should trim, drop empties/extra commas, and preserve order', () => {
      expect(parseTenantIds(' ba , ,oy, ko ,,')).toEqual(['ba', 'oy', 'ko']);
    });

    it('should dedupe while preserving first-seen order', () => {
      expect(parseTenantIds('ba,oy,ba,ko,oy')).toEqual(['ba', 'oy', 'ko']);
    });

    it('should return empty array for empty string', () => {
      expect(parseTenantIds('')).toEqual([]);
    });

    it('should return empty array for whitespace/commas only', () => {
      expect(parseTenantIds('  ,  , ')).toEqual([]);
    });
  });

  // -------------------------------------------------------------------
  // getEffectiveConsumerPrefix
  // -------------------------------------------------------------------

  describe('getEffectiveConsumerPrefix', () => {
    it('should prefer explicit KAFKA_CONSUMER_TOPIC_PREFIX over tenant ids', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '(ng)-';
      (config as any).centralInstanceTenantIds = 'ba,ke';
      expect(getEffectiveConsumerPrefix()).toBe('(ng)-');
    });

    it('should derive prefix from tenant ids when no explicit prefix (central)', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';
      (config as any).centralInstanceTenantIds = ' ba , ko ';
      expect(getEffectiveConsumerPrefix()).toBe('(ba|ko)-');
    });

    it('should return empty when central disabled', () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).centralInstanceTenantIds = 'ba,ko';
      expect(getEffectiveConsumerPrefix()).toBe('');
    });

    it('should return empty when central enabled but no tenant ids and no prefix', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';
      (config as any).centralInstanceTenantIds = '';
      expect(getEffectiveConsumerPrefix()).toBe('');
    });
  });

  // -------------------------------------------------------------------
  // consumer pattern derived from tenant list (single source of truth)
  // -------------------------------------------------------------------

  describe('getConsumerTopicPattern / stripTopicPrefix derived from tenant ids', () => {
    beforeEach(() => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';
      (config as any).centralInstanceTenantIds = 'ba,ko';
    });

    it('should build a regex matching listed tenants and rejecting unlisted', () => {
      const result = getConsumerTopicPattern('hcm-processing-result');
      expect(result).toBeInstanceOf(RegExp);
      expect((result as RegExp).test('ba-hcm-processing-result')).toBe(true);
      expect((result as RegExp).test('ko-hcm-processing-result')).toBe(true);
      expect((result as RegExp).test('ng-hcm-processing-result')).toBe(false);
    });

    it('should strip a tenant prefix derived from the list', () => {
      expect(stripTopicPrefix('ko-hcm-processing-result')).toBe('hcm-processing-result');
    });
  });

  // -------------------------------------------------------------------
  // getStartupTopicsToCreate
  // -------------------------------------------------------------------

  describe('getStartupTopicsToCreate', () => {
    it('should expand each base topic per tenant in central instance', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).centralInstanceTenantIds = 'ko,ba';
      const result = getStartupTopicsToCreate(['hcm-processing-result', 'start-admin-console-task']);
      expect(result).toEqual([
        'ko-hcm-processing-result',
        'ba-hcm-processing-result',
        'ko-start-admin-console-task',
        'ba-start-admin-console-task',
      ]);
    });

    it('should keep non-central topics bare even in central instance', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).centralInstanceTenantIds = 'ko,ba';
      (config as any).kafka.KAFKA_NON_CENTRAL_INSTANCE_TOPICS = 'egov.core.notification.email';
      const result = getStartupTopicsToCreate(['hcm-processing-result', 'egov.core.notification.email']);
      expect(result).toContain('ko-hcm-processing-result');
      expect(result).toContain('ba-hcm-processing-result');
      expect(result).toContain('egov.core.notification.email');
      expect(result).not.toContain('ko-egov.core.notification.email');
    });

    it('should not produce duplicates', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).centralInstanceTenantIds = 'ko,ko';
      const result = getStartupTopicsToCreate(['hcm-processing-result']);
      expect(result).toEqual(['ko-hcm-processing-result']);
    });

    it('should fall back to base topics in central instance when no tenant ids', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).centralInstanceTenantIds = '';
      const result = getStartupTopicsToCreate(['hcm-processing-result', 'start-admin-console-task']);
      expect(result).toEqual(['hcm-processing-result', 'start-admin-console-task']);
    });

    it('should return base topics unchanged when central disabled (no prefix)', () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).centralInstanceTenantIds = 'ko,ba';
      const result = getStartupTopicsToCreate(['hcm-processing-result', 'start-admin-console-task']);
      expect(result).toEqual(['hcm-processing-result', 'start-admin-console-task']);
    });
  });
});
