import { getTopicName, getConsumerTopicPattern, stripTopicPrefix, validateConsumerTopicPrefix } from '../utils/kafkaTopicUtils';

jest.mock('../config', () => ({
  default: {
    isEnvironmentCentralInstance: false,
    kafkaConsumerTopicPrefix: '',
  },
  __esModule: true,
}));

import config from '../config';

describe('kafkaTopicUtils', () => {
  afterEach(() => {
    (config as any).isEnvironmentCentralInstance = false;
    (config as any).kafkaConsumerTopicPrefix = '';
  });

  // -------------------------------------------------------------------
  // getTopicName (PRODUCER side)
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
    it('should throw when central instance enabled but prefix is empty', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';
      expect(() => validateConsumerTopicPrefix()).toThrow('KAFKA_CONSUMER_TOPIC_PREFIX must be set');
    });

    it('should NOT throw when central instance enabled and prefix is set', () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke)-';
      expect(() => validateConsumerTopicPrefix()).not.toThrow();
    });

    it('should NOT throw when central instance is disabled regardless of prefix', () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).kafkaConsumerTopicPrefix = '';
      expect(() => validateConsumerTopicPrefix()).not.toThrow();
    });
  });
});
