/**
 * Tests for Kafka Listener — consumer subscription and message routing.
 *
 * Verifies that:
 * - Topics use regex subscription when prefix is set
 * - Topics use exact subscription when prefix is empty
 * - Message routing strips prefix and dispatches to the correct handler
 * - Startup throws when central instance enabled but prefix empty
 * - Handler errors are caught and logged without crashing the consumer
 */

const mockSubscribe = jest.fn().mockResolvedValue(undefined);
const mockConnect = jest.fn().mockResolvedValue(undefined);
const mockRun = jest.fn().mockResolvedValue(undefined);
const mockConsumerOn = jest.fn();
const mockListTopics = jest.fn().mockResolvedValue([]);
const mockCreateTopics = jest.fn().mockResolvedValue(undefined);
const mockAlterConfigs = jest.fn().mockResolvedValue(undefined);
const mockDescribeConfigs = jest.fn().mockResolvedValue({ resources: [], throttleTime: 0 });

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
      admin: jest.fn().mockReturnValue({
        connect: jest.fn().mockResolvedValue(undefined),
        listTopics: mockListTopics,
        createTopics: mockCreateTopics,
        describeConfigs: mockDescribeConfigs,
        alterConfigs: mockAlterConfigs,
        disconnect: jest.fn().mockResolvedValue(undefined),
      }),
    })),
    logLevel: { NOTHING: 0 },
    ConfigResourceTypes: { TOPIC: 2 },
  };
});

jest.mock('../config', () => {
  return {
    default: {
      isEnvironmentCentralInstance: false,
      kafkaConsumerTopicPrefix: '',
      centralInstanceTenantIds: '',
      host: {
        KAFKA_BROKER_HOST: 'localhost:9092',
      },
      kafka: {
        CONSUMER_GROUP_ID: 'test-group',
        KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC: 'start-admin-console-task',
        KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC: 'start-admin-console-mapping-task',
        KAFKA_TEST_TOPIC: 'test-topic-project-factory',
        KAFKA_HCM_PROCESSING_RESULT_TOPIC: 'hcm-processing-result',
        KAFKA_FACILITY_CREATE_BATCH_TOPIC: 'hcm-facility-create-batch',
        KAFKA_USER_CREATE_BATCH_TOPIC: 'hcm-user-create-batch',
        KAFKA_MAPPING_BATCH_TOPIC: 'hcm-mapping-batch',
        KAFKA_CAMPAIGN_MARK_FAILED_TOPIC: 'hcm-campaign-mark-failed',
        KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC: 'save-project-campaign-details',
        KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC: 'update-project-campaign-details',
        KAFKA_NON_CENTRAL_INSTANCE_TOPICS: '',
        KAFKA_TOPIC_LARGE_MESSAGE_MAX_BYTES: 4194304,
        KAFKA_CONSUMER_RETRIES: 10,
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

jest.mock('../utils/requestContext', () => ({
  requestContextStore: {
    run: jest.fn().mockImplementation((_ctx: any, fn: () => Promise<any>) => fn()),
  },
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

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Returns the eachMessage callback registered with consumer.run() */
function getEachMessageCallback(): (payload: any) => Promise<void> {
  const runCall = mockRun.mock.calls[0][0];
  return runCall.eachMessage;
}

function createMessage(topic: string, data: any, offset = '0') {
  return {
    topic,
    partition: 0,
    message: {
      value: Buffer.from(JSON.stringify(data)),
      offset,
    },
  };
}

/** Flush pending microtasks so fire-and-forget handlers complete. */
const flushPromises = () => new Promise(resolve => setImmediate(resolve));

// ─────────────────────────────────────────────────────────────────────────────

describe('Kafka Listener', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (config as any).isEnvironmentCentralInstance = false;
    (config as any).kafkaConsumerTopicPrefix = '';
    (config as any).centralInstanceTenantIds = '';
    (config as any).kafka.KAFKA_NON_CENTRAL_INSTANCE_TOPICS = '';
  });

  // ── SUBSCRIPTION ───────────────────────────────────────────────────────────

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
      subscribedTopics.forEach((t: any) => expect(typeof t).toBe('string'));
    });

    it('Scenario 2: single-state prefix — all topics subscribed as RegExp', async () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      await listener();

      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);

      expect(subscribedTopics).toHaveLength(8);
      subscribedTopics.forEach((t: any) => expect(t).toBeInstanceOf(RegExp));

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

    it('Scenario 3b: central instance + tenant ids (no explicit prefix) — creates prefixed topics and subscribes via derived regex', async () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';
      (config as any).centralInstanceTenantIds = 'ba,ko';
      await listener();

      // All required topics are pre-created per tenant before subscribing
      const createdTopics: string[] = mockCreateTopics.mock.calls
        .flatMap((c: any[]) => c[0].topics.map((t: any) => t.topic));
      expect(createdTopics).toContain('ba-hcm-processing-result');
      expect(createdTopics).toContain('ko-hcm-processing-result');
      expect(createdTopics).toContain('ba-start-admin-console-task');
      expect(createdTopics).toContain('ko-start-admin-console-task');
      // Bare base topics must not be created in central instance
      expect(createdTopics).not.toContain('hcm-processing-result');

      // Subscription uses a regex derived from the same tenant list
      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);
      subscribedTopics.forEach((t: any) => expect(t).toBeInstanceOf(RegExp));
      const processingRegex = subscribedTopics.find((t: any) =>
        t instanceof RegExp && t.test('ko-hcm-processing-result')
      ) as RegExp;
      expect(processingRegex).toBeDefined();
      expect(processingRegex.test('ba-hcm-processing-result')).toBe(true);
      expect(processingRegex.test('ng-hcm-processing-result')).toBe(false);
    });
  });

  // ── STARTUP VALIDATION ─────────────────────────────────────────────────────

  describe('Startup Validation', () => {
    it('Scenario 4: central instance enabled + empty prefix — throws error', async () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).kafkaConsumerTopicPrefix = '';

      await expect(async () => listener()).rejects.toThrow('CENTRAL_INSTANCE_TENANT_IDS');
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

  // ── MESSAGE ROUTING ────────────────────────────────────────────────────────

  describe('Message Routing', () => {
    it('Scenario 7: no prefix — routes unprefixed topic to correct handler', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const eachMessage = getEachMessageCallback();
      await eachMessage(createMessage('hcm-mapping-batch', { test: true }, '5'));
      await flushPromises();

      expect(mockHandleMappingBatch).toHaveBeenCalledWith({ test: true });
    });

    it('Scenario 8: single-state prefix — strips prefix and routes correctly', async () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      await listener();

      const eachMessage = getEachMessageCallback();
      await eachMessage(createMessage('ng-hcm-mapping-batch', { test: true }));
      await flushPromises();

      expect(mockHandleMappingBatch).toHaveBeenCalledWith({ test: true });
    });

    it('Scenario 9: multi-state prefix — strips any matching state prefix', async () => {
      (config as any).kafkaConsumerTopicPrefix = '(ba|ke|cg)-';
      await listener();

      const eachMessage = getEachMessageCallback();

      await eachMessage(createMessage('ba-hcm-facility-create-batch', { state: 'ba' }));
      await flushPromises();
      expect(mockHandleFacilityBatch).toHaveBeenCalledWith({ state: 'ba' });

      mockHandleUserBatch.mockClear();
      await eachMessage(createMessage('ke-hcm-user-create-batch', { state: 'ke' }));
      await flushPromises();
      expect(mockHandleUserBatch).toHaveBeenCalledWith({ state: 'ke' });
    });

    it('Scenario 10: unknown topic — logs warning, no handler called', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const eachMessage = getEachMessageCallback();
      await eachMessage(createMessage('unknown-topic', { test: true }));
      await flushPromises();

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
        await eachMessage(createMessage(topic, { from: topic }));
        await flushPromises();
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
        await eachMessage(createMessage(topic, { from: topic }));
        await flushPromises();
        expect(handler).toHaveBeenCalledWith({ from: topic });
      }
    });
  });

  // ── CROSS-SERVICE ──────────────────────────────────────────────────────────

  describe('Cross-Service Topic Matching', () => {
    it('Scenario 13: prefix set — excel-ingestion prefixed topic matches subscription + routes', async () => {
      (config as any).kafkaConsumerTopicPrefix = 'ng-';
      await listener();

      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);
      const processingResultRegex = subscribedTopics.find((t: any) =>
        t instanceof RegExp && t.test('ng-hcm-processing-result')
      );
      expect(processingResultRegex).toBeDefined();

      const eachMessage = getEachMessageCallback();
      await eachMessage(createMessage('ng-hcm-processing-result', { result: 'done' }));
      await flushPromises();
      expect(mockHandleProcessingResult).toHaveBeenCalledWith({ result: 'done' });
    });

    it('Scenario 14: no prefix — excel-ingestion unprefixed topic matches subscription + routes', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const subscribedTopics = mockSubscribe.mock.calls.map((c: any[]) => c[0].topic);
      expect(subscribedTopics).toContain('hcm-processing-result');

      const eachMessage = getEachMessageCallback();
      await eachMessage(createMessage('hcm-processing-result', { result: 'done' }));
      await flushPromises();
      expect(mockHandleProcessingResult).toHaveBeenCalledWith({ result: 'done' });
    });
  });

  // ── LARGE MESSAGE TOPIC CONFIG ─────────────────────────────────────────────

  describe('Large Message Topic Config', () => {
    const largeTopics = [
      'save-project-campaign-details',
      'update-project-campaign-details',
      'start-admin-console-task',
      'start-admin-console-mapping-task',
    ];
    const smallTopics = [
      'hcm-facility-create-batch',
      'hcm-user-create-batch',
      'hcm-mapping-batch',
    ];

    it('Scenario L1: new large-message topics get max.message.bytes + gzip configEntries on create', async () => {
      mockListTopics.mockResolvedValueOnce([]);
      await listener();

      const createdTopics: any[] = mockCreateTopics.mock.calls
        .flatMap((c: any[]) => c[0].topics);

      for (const name of largeTopics) {
        const topic = createdTopics.find((t: any) => t.topic === name);
        expect(topic).toBeDefined();
        expect(topic.configEntries).toEqual(
          expect.arrayContaining([
            { name: 'max.message.bytes', value: '4194304' },
            { name: 'compression.type', value: 'gzip' },
          ])
        );
      }
    });

    it('Scenario L2: non-large topics are created without configEntries', async () => {
      mockListTopics.mockResolvedValueOnce([]);
      await listener();

      const createdTopics: any[] = mockCreateTopics.mock.calls
        .flatMap((c: any[]) => c[0].topics);

      for (const name of smallTopics) {
        const topic = createdTopics.find((t: any) => t.topic === name);
        if (topic) {
          expect(topic.configEntries).toBeUndefined();
        }
      }
    });

    it('Scenario L3: already-existing large-message topics without target config get alterConfigs', async () => {
      mockListTopics.mockResolvedValueOnce(largeTopics);
      // describeConfigs returns no max.message.bytes entry → all need updating
      mockDescribeConfigs.mockResolvedValueOnce({
        resources: largeTopics.map(name => ({ resourceName: name, configEntries: [], errorCode: 0, errorMessage: null, resourceType: 2 })),
        throttleTime: 0,
      });
      await listener();

      expect(mockAlterConfigs).toHaveBeenCalledTimes(1);
      const call = mockAlterConfigs.mock.calls[0][0];
      const alteredNames: string[] = call.resources.map((r: any) => r.name);

      for (const name of largeTopics) {
        expect(alteredNames).toContain(name);
      }
      for (const r of call.resources) {
        expect(r.configEntries).toEqual(
          expect.arrayContaining([
            { name: 'max.message.bytes', value: '4194304' },
            { name: 'compression.type', value: 'gzip' },
          ])
        );
      }
    });

    it('Scenario L4: non-large existing topics are not passed to describeConfigs or alterConfigs', async () => {
      mockListTopics.mockResolvedValueOnce([...largeTopics, ...smallTopics]);
      mockDescribeConfigs.mockResolvedValueOnce({
        resources: largeTopics.map(name => ({ resourceName: name, configEntries: [], errorCode: 0, errorMessage: null, resourceType: 2 })),
        throttleTime: 0,
      });
      await listener();

      const describedNames: string[] = mockDescribeConfigs.mock.calls[0][0].resources.map((r: any) => r.name);
      for (const name of smallTopics) {
        expect(describedNames).not.toContain(name);
      }

      const alteredNames: string[] = mockAlterConfigs.mock.calls[0][0].resources.map((r: any) => r.name);
      for (const name of smallTopics) {
        expect(alteredNames).not.toContain(name);
      }
    });

    it('Scenario L4b: topics already at 4MB are skipped — alterConfigs not called for them', async () => {
      const alreadyConfigured = largeTopics.slice(0, 2);
      const needsUpdate = largeTopics.slice(2);
      mockListTopics.mockResolvedValueOnce(largeTopics);
      mockDescribeConfigs.mockResolvedValueOnce({
        resources: [
          ...alreadyConfigured.map(name => ({
            resourceName: name,
            configEntries: [{ configName: 'max.message.bytes', configValue: '4194304' }],
            errorCode: 0, errorMessage: null, resourceType: 2,
          })),
          ...needsUpdate.map(name => ({
            resourceName: name,
            configEntries: [{ configName: 'max.message.bytes', configValue: '1048576' }],
            errorCode: 0, errorMessage: null, resourceType: 2,
          })),
        ],
        throttleTime: 0,
      });
      await listener();

      expect(mockAlterConfigs).toHaveBeenCalledTimes(1);
      const alteredNames: string[] = mockAlterConfigs.mock.calls[0][0].resources.map((r: any) => r.name);
      for (const name of alreadyConfigured) {
        expect(alteredNames).not.toContain(name);
      }
      for (const name of needsUpdate) {
        expect(alteredNames).toContain(name);
      }
    });

    it('Scenario L4c: all large topics already at 4MB — alterConfigs not called at all', async () => {
      mockListTopics.mockResolvedValueOnce(largeTopics);
      mockDescribeConfigs.mockResolvedValueOnce({
        resources: largeTopics.map(name => ({
          resourceName: name,
          configEntries: [{ configName: 'max.message.bytes', configValue: '4194304' }],
          errorCode: 0, errorMessage: null, resourceType: 2,
        })),
        throttleTime: 0,
      });
      await listener();

      expect(mockAlterConfigs).not.toHaveBeenCalled();
    });

    it('Scenario L5: central instance — tenant-prefixed large-message topics get configEntries on create', async () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).centralInstanceTenantIds = 'ba,ko';
      mockListTopics.mockResolvedValueOnce([]);
      await listener();

      const createdTopics: any[] = mockCreateTopics.mock.calls
        .flatMap((c: any[]) => c[0].topics);

      for (const base of largeTopics) {
        for (const tenant of ['ba', 'ko']) {
          const topic = createdTopics.find((t: any) => t.topic === `${tenant}-${base}`);
          expect(topic).toBeDefined();
          expect(topic.configEntries).toEqual(
            expect.arrayContaining([
              { name: 'max.message.bytes', value: '4194304' },
              { name: 'compression.type', value: 'gzip' },
            ])
          );
        }
      }
    });

    it('Scenario L6: central instance — existing tenant-prefixed large topics get alterConfigs when not yet at 4MB', async () => {
      (config as any).isEnvironmentCentralInstance = true;
      (config as any).centralInstanceTenantIds = 'ba,ko';
      const existingPrefixed = largeTopics.flatMap(b => [`ba-${b}`, `ko-${b}`]);
      mockListTopics.mockResolvedValueOnce(existingPrefixed);
      // None have target config yet
      mockDescribeConfigs.mockResolvedValueOnce({
        resources: existingPrefixed.map(name => ({ resourceName: name, configEntries: [], errorCode: 0, errorMessage: null, resourceType: 2 })),
        throttleTime: 0,
      });
      await listener();

      expect(mockAlterConfigs).toHaveBeenCalledTimes(1);
      const call = mockAlterConfigs.mock.calls[0][0];
      const alteredNames: string[] = call.resources.map((r: any) => r.name);

      for (const name of existingPrefixed) {
        expect(alteredNames).toContain(name);
      }
    });
  });

  // ── ERROR HANDLING ─────────────────────────────────────────────────────────

  describe('Error Handling', () => {
    it('Scenario 15: handler throws — error logged, eachMessage resolves without crash', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      mockHandleTaskForCampaign.mockRejectedValueOnce(new Error('unexpected crash'));
      await listener();

      const eachMessage = getEachMessageCallback();
      await expect(
        eachMessage(createMessage('start-admin-console-task', { campaignId: '123' }, '3'))
      ).resolves.toBeUndefined();

      await flushPromises();
      expect(logger.error).toHaveBeenCalledWith(
        expect.stringContaining('Error processing message')
      );
    });

    it('Scenario 16: two sequential messages — second failure logged independently, first succeeds', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      mockHandleTaskForCampaign
        .mockResolvedValueOnce(undefined)
        .mockRejectedValueOnce(new Error('crash'));
      await listener();

      const eachMessage = getEachMessageCallback();
      await eachMessage(createMessage('start-admin-console-task', { id: 'msg1' }));
      await flushPromises();
      expect(mockHandleTaskForCampaign).toHaveBeenCalledWith({ id: 'msg1' });

      await eachMessage(createMessage('start-admin-console-task', { id: 'msg2' }));
      await flushPromises();
      expect(logger.error).toHaveBeenCalledWith(expect.stringContaining('Error processing message'));
    });

    it('Scenario 17: unknown topic — logs warning, eachMessage resolves', async () => {
      (config as any).kafkaConsumerTopicPrefix = '';
      await listener();

      const eachMessage = getEachMessageCallback();
      await expect(
        eachMessage(createMessage('totally-unknown-topic', { data: 1 }))
      ).resolves.toBeUndefined();

      await flushPromises();
      expect(logger.warn).toHaveBeenCalledWith(expect.stringContaining('Unhandled topic'));
    });
  });
});
