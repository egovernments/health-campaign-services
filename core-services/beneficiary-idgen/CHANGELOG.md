# Changelog
All notable changes to this module will be documented in this file.

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
