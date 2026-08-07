import configDefault from '../config';

/**
 * Every batch/chunk size in the service is env-configurable via config/index.ts.
 * These tests pin the default (no env var) and the override (env var set) branches
 * so the literals can never silently drift back into the call sites.
 */

// path -> [env var, default literal]
const BATCH_SIZE_KEYS: Record<string, [string, number]> = {
  'batchSize': ['BATCH_SIZE', 100],
  'project.creationBatchSize': ['PROJECT_CREATION_BATCH_SIZE', 100],
  'boundary.mappingPersistBatchSize': ['BOUNDARY_MAPPING_PERSIST_BATCH_SIZE', 100],
  'boundary.persistBatchSize': ['BOUNDARY_PERSIST_BATCH_SIZE', 100],
  'facility.persistBatchSize': ['FACILITY_PERSIST_BATCH_SIZE', 100],
  'facility.creationBatchSize': ['FACILITY_CREATION_BATCH_SIZE', 100],
  'facility.kafkaCreateBatchSize': ['FACILITY_KAFKA_CREATE_BATCH_SIZE', 30],
  'facility.searchBatchSize': ['FACILITY_SEARCH_BATCH_SIZE', 50],
  'user.mappingPersistBatchSize': ['USER_MAPPING_PERSIST_BATCH_SIZE', 100],
  'user.persistBatchSize': ['USER_PERSIST_BATCH_SIZE', 100],
  'user.creationBatchSize': ['USER_CREATION_BATCH_SIZE', 100],
  'user.kafkaCreateBatchSize': ['USER_KAFKA_CREATE_BATCH_SIZE', 30],
  'user.searchBatchSize': ['USER_SEARCH_BATCH_SIZE', 50],
  'user.validationSearchBatchSize': ['USER_VALIDATION_SEARCH_BATCH_SIZE', 50],
  'user.individualSearchBatchSize': ['USER_INDIVIDUAL_SEARCH_BATCH_SIZE', 50],
  'workerRegistry.searchBatchSize': ['WORKER_REGISTRY_SEARCH_BATCH_SIZE', 50],
  'workerRegistry.updateBatchSize': ['WORKER_REGISTRY_UPDATE_BATCH_SIZE', 100],
  'mapping.kafkaBatchSize': ['MAPPING_KAFKA_BATCH_SIZE', 30],
  'mapping.persistBatchSize': ['MAPPING_PERSIST_BATCH_SIZE', 100],
  'attendanceRegister.attendeePersistBatchSize': ['ATTENDANCE_ATTENDEE_PERSIST_BATCH_SIZE', 100],
  'attendanceRegister.registerPersistBatchSize': ['ATTENDANCE_REGISTER_PERSIST_BATCH_SIZE', 100],
  'attendanceRegister.registerApiBatchSize': ['ATTENDANCE_REGISTER_API_BATCH_SIZE', 100],
  'localisation.messageChunkSize': ['LOCALIZATION_MESSAGE_CHUNK_SIZE', 100],
  'resource.activityBatchSize': ['RESOURCE_ACTIVITY_BATCH_SIZE', 10],
  'productVariant.searchBatchSize': ['PRODUCT_VARIANT_SEARCH_BATCH_SIZE', 100],
  'sheetData.persistBatchSize': ['SHEET_DATA_PERSIST_BATCH_SIZE', 100],
};

const readPath = (obj: any, path: string): unknown =>
  path.split('.').reduce((acc, key) => acc?.[key], obj);

describe('configurable batch sizes', () => {
  describe('defaults (no env var set)', () => {
    it.each(Object.entries(BATCH_SIZE_KEYS))(
      'config.%s defaults to the documented literal',
      (path, [, defaultValue]) => {
        expect(readPath(configDefault, path)).toBe(defaultValue);
      }
    );
  });

  describe('env var override', () => {
    const originalEnv = process.env;

    afterEach(() => {
      process.env = originalEnv;
      jest.resetModules();
    });

    it.each(Object.entries(BATCH_SIZE_KEYS))(
      'config.%s reads %s from the environment',
      (path, [envVar]) => {
        jest.resetModules();
        process.env = { ...originalEnv, [envVar]: '7' };
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const freshConfig = require('../config').default;
        expect(readPath(freshConfig, path)).toBe(7);
      }
    );

    it('ignores other batch env vars when one is set (no cross-talk)', () => {
      jest.resetModules();
      process.env = { ...originalEnv, PROJECT_CREATION_BATCH_SIZE: '99' };
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const freshConfig = require('../config').default;
      expect(freshConfig.project.creationBatchSize).toBe(99);
      // a sibling key keeps its default
      expect(freshConfig.facility.kafkaCreateBatchSize).toBe(30);
    });
  });
});
