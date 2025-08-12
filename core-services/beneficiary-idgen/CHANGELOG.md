# Changelog
All notable changes to this module will be documented in this file.

## 1.1.0 - 2025-07-23

- Changed dispatch user id flow with DB query
- Updated Redis usage with Redisson
- Fixed max limit reached issue for user device per day
- upgraded health-services-models to 1.0.28
- upgraded health-services-common to 1.1.0
- Enhanced beneficiary id generation flow to work in async manner

## 1.0.0 - 2025-04-29

- Added APIs for generating, dispatching, searching, and updating IDs.
  - `POST /id/id_pool/_generate`
  - `POST /id/id_pool/_dispatch`
  - `POST /id/id_pool/_search`
  - `POST /id/id_pool/_update`
- Integrated Redis and Kafka for caching, distributed locking (using redisson), and asynchronous processing.
- Enhanced logging for better traceability of ID pool requests.
- Created new DB tables for ID pool and transaction logs.
- Added documentation for setup instructions, features and APIs.
