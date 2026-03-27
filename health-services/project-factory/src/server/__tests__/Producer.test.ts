/**
 * Tests that produceModifiedMessages applies the correct topic name
 * based on central instance configuration.
 *
 * We mock kafkajs, config, logger, and genericUtils to isolate the producer logic.
 */

const mockSend = jest.fn().mockResolvedValue(undefined);
const mockConnect = jest.fn().mockResolvedValue(undefined);
const mockDisconnect = jest.fn().mockResolvedValue(undefined);
const mockOn = jest.fn();
const mockDescribeCluster = jest.fn().mockResolvedValue({ brokers: [{ nodeId: 0 }] });
const mockAdminConnect = jest.fn().mockResolvedValue(undefined);
const mockAdminDisconnect = jest.fn().mockResolvedValue(undefined);

jest.mock('kafkajs', () => ({
  Kafka: jest.fn().mockImplementation(() => ({
    producer: () => ({
      connect: mockConnect,
      disconnect: mockDisconnect,
      send: mockSend,
      on: mockOn,
    }),
    admin: () => ({
      connect: mockAdminConnect,
      disconnect: mockAdminDisconnect,
      describeCluster: mockDescribeCluster,
    }),
  })),
  logLevel: { INFO: 5, NOTHING: 0 },
}));

jest.mock('../config', () => ({
  default: {
    isEnvironmentCentralInstance: false,
    host: {
      KAFKA_BROKER_HOST: 'localhost:9092',
    },
  },
  __esModule: true,
}));

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
  throwError: jest.fn(),
}));

import config from '../config';

describe('produceModifiedMessages', () => {
  let produceModifiedMessages: any;

  beforeEach(async () => {
    mockSend.mockClear();
    jest.isolateModules(() => {
      const mod = require('../kafka/Producer');
      produceModifiedMessages = mod.produceModifiedMessages;
    });
    // Wait for async producer initialization
    await new Promise(res => setTimeout(res, 100));
  });

  afterEach(() => {
    (config as any).isEnvironmentCentralInstance = false;
  });

  it('should prefix topic when central instance is enabled with dotted tenantId', async () => {
    (config as any).isEnvironmentCentralInstance = true;
    await produceModifiedMessages({ data: 'test' }, 'hcm-mapping-batch', 'ng.kaduna');

    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({
        topic: 'ng-hcm-mapping-batch',
      })
    );
  });

  it('should NOT prefix topic when central instance is disabled', async () => {
    (config as any).isEnvironmentCentralInstance = false;
    await produceModifiedMessages({ data: 'test' }, 'hcm-mapping-batch', 'ng.kaduna');

    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({
        topic: 'hcm-mapping-batch',
      })
    );
  });

  it('should prefix with full tenantId when no dot present', async () => {
    (config as any).isEnvironmentCentralInstance = true;
    await produceModifiedMessages({ data: 'test' }, 'save-sheet-data', 'ng');

    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({
        topic: 'ng-save-sheet-data',
      })
    );
  });
});
