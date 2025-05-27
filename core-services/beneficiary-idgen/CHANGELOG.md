# Changelog
All notable changes to this module will be documented in this file.

## 1.0.0 - 2025-04-29
- Base version
- Added new APIs for generating, dispatching, searching, and updating IDs.
- Depricated/ Removed the existing - 'id/v1/_generate' endpoint
- Integrated Redis and Kafka for caching, distributed locking (using redisson), and asynchronous processing.
- Exposed new APIs:
  - `POST /id/id_pool/_generate`
  - `POST /id/id_pool/_dispatch`
  - `POST /id_pool/_search`
  - `POST /id/id_pool/_update`
- Enhanced logging for better traceability of ID pool requests.
- Updated documentation for new features and API changes.

