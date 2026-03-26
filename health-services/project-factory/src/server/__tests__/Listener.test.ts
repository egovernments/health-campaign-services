/**
 * Tests for Kafka Listener — consumer subscription and message routing.
 *
 * Verifies that:
 * - Topics use regex subscription when prefix is set
 * - Topics use exact subscription when prefix is empty
 * - Message routing strips prefix and dispatches to the correct handler
 * - Startup throws when central instance enabled but prefix empty
 */

const mockSubscribe = jest.fn().mockResolvedValue(undefined);
const mockConnect = jest.fn().mockResolvedValue(undefined);
const mockRun = jest.fn().mockResolvedValue(undefined);
const mockConsumerOn = jest.fn();

jest.mock('kafkajs', () => {
  return {
    Kafka: jest.fn().mockImplementation(() => ({
      consumer: jest.fn().mockReturnValue({
        connect: mockConnect,
        subscribe: mockSubscribe,
        run: mockRun,
        on: mockConsumerOn,
        events: { CRASH: 'consumer.crash', DISCONNECT: 'consumer.disconnect' },
      }),
    })),
    logLevel: { NOTHING: 0 },
  };
});

jest.mock('../config', () => {
  return {
    default: {
      isEnvironmentCentralInstance: false,
      kafkaConsumerTopicPrefix: '',
      host: {
        KAFKA_BROKER_HOST: 'localhost:9092',
      },
      app: {
      },
      kafka: {
        KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC: 'start-admin-console-task',
        KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC: 'start-admin-console-mapping-task',
        KAFKA_TEST_TOPIC: 'test-topic-project-factory',
        KAFKA_HCM_PROCESSING_RESULT_TOPIC: 'hcm-processing-result',
        KAFKA_FACILITY_CREATE_BATCH_TOPIC: 'hcm-facility-create-batch',
        KAFKA_USER_CREATE_BATCH_TOPIC: 'hcm-user-create-batch',
        KAFKA_MAPPING_BATCH_TOPIC: 'hcm-mapping-batch',
        KAFKA_CAMPAIGN_MARK_FAILED_TOPIC: 'hcm-campaign-mark-failed',
      },
    },
    __esModule: true,
  };
});

jest.mock('../utils/logger', () => ({
  logger: {
    info: jest.fn(),
    debug: jest.fn(),
    error: jest.fn(),
    warn: jest.fn(),
  },
  getFormattedStringForDebug: jest.fn().mockReturnValue('{}'),
}));

jest.mock('../utils/genericUtils', () => ({
  shutdownGracefully: jest.fn(),
}));

// Mock all handler modules
const mockHandleTaskForCampaign = jest.fn().mockResolvedValue(undefined);
const mockHandleMappingTaskForCampaign = jest.fn().mockResolvedValue(undefined);
const mockHandleProcessingResult = jest.fn().mockResolvedValue(undefined);
const mockHandleFacilityBatch = jest.fn().mockResolvedValue(undefined);
const mockHandleUserBatch = jest.fn().mockResolvedValue(undefined);
const mockHandleMappingBatch = jest.fn().mockResolvedValue(undefined);
const mockHandleCampaignFailure = jest.fn().mockResolvedValue(undefined);

jest.mock('../utils/taskUtils', () => ({ handleTaskForCampaign: mockHandleTaskForCampaign }));
jest.mock('../utils/campaignMappingUtils', () => ({ handleMappingTaskForCampaign: mockHandleMappingTaskForCampaign }));
jest.mock('../utils/processingResultHandler', () => ({ handleProcessingResult: mockHandleProcessingResult }));
jest.mock('../utils/facilityBatchHandler', () => ({ handleFacilityBatch: mockHandleFacilityBatch }));
jest.mock('../utils/userBatchHandler', () => ({ handleUserBatch: mockHandleUserBatch }));
jest.mock('../utils/mappingBatchHandler', () => ({ handleMappingBatch: mockHandleMappingBatch }));
jest.mock('../utils/campaignFailureHandler', () => ({ handleCampaignFailure: mockHandleCampaignFailure }));

import config from '../config';
import { logger } from '../utils/logger';
import { listener } from '../kafka/Listener';

describe('Kafka Listener', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (config as any).isEnvironmentCentralInstance = false;
    (config as any).kafkaConsumerTopicPrefix = '';
  });

  function getEachMessageCallback(): (payload: any) => Promise<void> {
    const runCall = mockRun.mock.calls[0][0];
    return runCall.eachMessage;
  }

  const createMessage = (data: any) => ({
    value: Buffer.from(JSON.stringify(data)),
  });

  // -------------------------------------------------------------------
  // SUBSCRIPTION TESTS
  // -------------------------------------------------------------------

  describe('Topic Subscription', () => {
    it('Scenario 1: no prefix — all topics subscribed as exact strings', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);

      expect(subscribedTopics).toContain('start-admin-console-task');
      expect(subscribedTopics).toContain('start-admin-console-mapping-task');
      expect(subscribedTopics).toContain('hcm-processing-result');
      expect(subscribedTopics).toContain('hcm-facility-create-batch');
      expect(subscribedTopics).toContain('hcm-user-create-batch');
      expect(subscribedTopics).toContain('hcm-mapping-batch');
      expect(subscribedTopics).toContain('hcm-campaign-mark-failed');
      expect(subscribedTopics).toContain('test-topic-project-factory');
      expect(subscribedTopics).toHaveLength(8);
      // All should be strings, not RegExp
      subscribedTopics.forEach((t: any) => expect(typeof t).toBe('string'));
    });

    it('Scenario 2: single-state prefix — all topics subscribed as RegExp', async () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      await listener();

      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);

      expect(subscribedTopics).toHaveLength(8);
      subscribedTopics.forEach((t: any) => expect(t).toBeInstanceOf(RegExp));

      // Verify the regex matches the prefixed topic
      const mappingBatchRegex = subscribedTopics.find((t: any) =>
        t instanceof RegExp && t.test('ng-hcm-mapping-batch')
      );
      expect(mappingBatchRegex).toBeDefined();
    });

    it('Scenario 3: multi-state prefix — regex matches all configured states', async () => {
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke|cg)-';
      await listener();

      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);
      const mappingBatchRegex = subscribedTopics.find((t: any) =>
        t instanceof RegExp && t.test('ba-hcm-mapping-batch')
      ) as RegExp;

      expect(mappingBatchRegex).toBeDefined();
      expect(mappingBatchRegex.test('ba-hcm-mapping-batch')).toBe(true);
      expect(mappingBatchRegex.test('ke-hcm-mapping-batch')).toBe(true);
      expect(mappingBatchRegex.test('cg-hcm-mapping-batch')).toBe(true);
      expect(mappingBatchRegex.test('ng-hcm-mapping-batch')).toBe(false);
    });
  });

  // -------------------------------------------------------------------
  // STARTUP VALIDATION
  // -------------------------------------------------------------------

  describe('Startup Validation', () => {
    it('Scenario 4: central instance enabled + empty prefix — throws error', async () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';

      await expect(async () => listener()).rejects.toThrow('KAFKA_CONSUMER_TOPIC_PREFIX must be set');
    });

    it('Scenario 5: central instance enabled + prefix set — no error', async () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = 'ng-';

      await listener();
      expect(mockConnect).toHaveBeenCalled();
    });

    it('Scenario 6: central instance disabled + empty prefix — no error', async () => {
      (config as any).isEnvironmentCentralInstance = false;
      (config as any).kafkaConsumerTopicPrefix = '';

      await listener();
      expect(mockConnect).toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------
  // MESSAGE ROUTING TESTS
  // -------------------------------------------------------------------

  describe('Message Routing', () => {
    it('Scenario 7: no prefix — routes unprefixed topic to correct handler', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const eachMessage = getEachMessageCallback();
      await eachMessage({
        topic: 'hcm-mapping-batch',
        message: createMessage({ test: true }),
        partition: 0,
      });

      expect(mockHandleMappingBatch).toHaveBeenCalledWith({ test: true });
    });

    it('Scenario 8: single-state prefix — strips prefix and routes correctly', async () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      await listener();

      const eachMessage = getEachMessageCallback();
      await eachMessage({
        topic: 'ng-hcm-mapping-batch',
        message: createMessage({ test: true }),
        partition: 0,
      });

      expect(mockHandleMappingBatch).toHaveBeenCalledWith({ test: true });
    });

    it('Scenario 9: multi-state prefix — strips any matching state prefix', async () => {
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke|cg)-';
      await listener();

      const eachMessage = getEachMessageCallback();

      await eachMessage({
        topic: 'ba-hcm-facility-create-batch',
        message: createMessage({ state: 'ba' }),
        partition: 0,
      });
      expect(mockHandleFacilityBatch).toHaveBeenCalledWith({ state: 'ba' });

      mockHandleUserBatch.mockClear();
      await eachMessage({
        topic: 'ke-hcm-user-create-batch',
        message: createMessage({ state: 'ke' }),
        partition: 0,
      });
      expect(mockHandleUserBatch).toHaveBeenCalledWith({ state: 'ke' });
    });

    it('Scenario 10: unknown topic — logs warning, no handler called', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const eachMessage = getEachMessageCallback();
      await eachMessage({
        topic: 'unknown-topic',
        message: createMessage({ test: true }),
        partition: 0,
      });

      expect(logger.warn).toHaveBeenCalledWith(expect.stringContaining('Unhandled topic'));
      expect(mockHandleMappingBatch).not.toHaveBeenCalled();
    });

    it('Scenario 11: no prefix — all 7 handler topics route correctly', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const eachMessage = getEachMessageCallback();
      const testCases = [
        { topic: 'start-admin-console-task', handler: mockHandleTaskForCampaign },
        { topic: 'start-admin-console-mapping-task', handler: mockHandleMappingTaskForCampaign },
        { topic: 'hcm-processing-result', handler: mockHandleProcessingResult },
        { topic: 'hcm-facility-create-batch', handler: mockHandleFacilityBatch },
        { topic: 'hcm-user-create-batch', handler: mockHandleUserBatch },
        { topic: 'hcm-mapping-batch', handler: mockHandleMappingBatch },
        { topic: 'hcm-campaign-mark-failed', handler: mockHandleCampaignFailure },
      ];

      for (const { topic, handler } of testCases) {
        handler.mockClear();
        await eachMessage({ topic, message: createMessage({ from: topic }), partition: 0 });
        expect(handler).toHaveBeenCalledWith({ from: topic });
      }
    });

    it('Scenario 12: single prefix — all 7 handler topics route correctly after stripping', async () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      await listener();

      const eachMessage = getEachMessageCallback();
      const testCases = [
        { topic: 'ng-start-admin-console-task', handler: mockHandleTaskForCampaign },
        { topic: 'ng-start-admin-console-mapping-task', handler: mockHandleMappingTaskForCampaign },
        { topic: 'ng-hcm-processing-result', handler: mockHandleProcessingResult },
        { topic: 'ng-hcm-facility-create-batch', handler: mockHandleFacilityBatch },
        { topic: 'ng-hcm-user-create-batch', handler: mockHandleUserBatch },
        { topic: 'ng-hcm-mapping-batch', handler: mockHandleMappingBatch },
        { topic: 'ng-hcm-campaign-mark-failed', handler: mockHandleCampaignFailure },
      ];

      for (const { topic, handler } of testCases) {
        handler.mockClear();
        await eachMessage({ topic, message: createMessage({ from: topic }), partition: 0 });
        expect(handler).toHaveBeenCalledWith({ from: topic });
      }
    });
  });

  // -------------------------------------------------------------------
  // CROSS-SERVICE TESTS (excel-ingestion -> project-factory)
  // -------------------------------------------------------------------

  describe('Cross-Service Topic Matching', () => {
    it('Scenario 13: prefix set — excel-ingestion prefixed topic matches subscription + routes', async () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      await listener();

      // Verify subscription regex matches the topic excel-ingestion would produce
      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);
      const processingResultRegex = subscribedTopics.find((t: any) =>
        t instanceof RegExp && t.test('ng-hcm-processing-result')
      );
      expect(processingResultRegex).toBeDefined();

      // Verify routing works
      const eachMessage = getEachMessageCallback();
      await eachMessage({
        topic: 'ng-hcm-processing-result',
        message: createMessage({ result: 'done' }),
        partition: 0,
      });
      expect(mockHandleProcessingResult).toHaveBeenCalledWith({ result: 'done' });
    });

    it('Scenario 14: no prefix — excel-ingestion unprefixed topic matches subscription + routes', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);
      expect(subscribedTopics).toContain('hcm-processing-result');

      const eachMessage = getEachMessageCallback();
      await eachMessage({
        topic: 'hcm-processing-result',
        message: createMessage({ result: 'done' }),
        partition: 0,
      });
      expect(mockHandleProcessingResult).toHaveBeenCalledWith({ result: 'done' });
    });
  });
});
