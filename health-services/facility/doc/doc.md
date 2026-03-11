The Facility Registry service manages healthcare facility data including creation, updates, deletion, and search of facilities. It supports both synchronous API operations and asynchronous bulk processing using Kafka.

Facility Service Architecture
Facility Create Operation Flow
System Components
Controller Layer
Component	Responsibility
FacilityApiController	Exposes REST APIs for facility operations and routes requests to service layer
Consumer Layer
Component	Responsibility
FacilityConsumer	Listens to Kafka topics and processes bulk operations asynchronously
Service Layer
Component	Responsibility
FacilityService	Core business logic and orchestration of validation and enrichment
FacilityEnrichmentService	Handles entity enrichment including ID generation and audit fields
Validator Layer

The facility service uses multiple validators to ensure data consistency.

Validator	Responsibility
FBoundaryValidator	Validates request boundaries and mandatory fields
FIsDeletedValidator	Prevents operations on deleted entities
FNonExistentValidator	Ensures entity exists for update/delete
FNullIdValidator	Ensures ID is present where required
FRowVersionValidator	Handles concurrency control
FUniqueEntityValidator	Prevents duplicate facility creation
Repository Layer
Component	Responsibility
FacilityRepository	Handles database persistence, caching, and Kafka publishing
FacilityRowMapper	Maps database rows to entity models
External Services
Service	Purpose
IdGen Service	Generates unique IDs for facilities
Redis Cache	Stores frequently accessed facility data
PostgreSQL Database	Persistent storage for facility records
Kafka	Handles asynchronous messaging for bulk operations
API Endpoints
Method	Endpoint	Type	Description
POST	/v1/_create	Sync	Create a single facility
POST	/v1/_update	Sync	Update a facility
POST	/v1/_delete	Sync	Soft delete a facility
POST	/v1/_search	Sync	Search facilities
POST	/v1/bulk/_create	Async	Bulk facility creation via Kafka
POST	/v1/bulk/_update	Async	Bulk facility update via Kafka
POST	/v1/bulk/_delete	Async	Bulk facility deletion via Kafka
Key Architecture Characteristics
Synchronous Operations

Used for single facility operations

Immediate response to the client

Validation and persistence occur in the same request cycle

Asynchronous Operations

Used for bulk facility processing

Implemented using Kafka messaging

Improves scalability and resilience

Data Consistency

Multiple validators enforce data integrity

Row version validation prevents concurrent update conflicts

Performance Optimization

Redis caching for frequently accessed facility records

Kafka ensures decoupled processing
