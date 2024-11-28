# Changelog
All notable changes to this module will be documented in this file.

## 1.0.0 - 2024-11-28
#### Census Service
The Census Service introduces core functionalities for managing census data:

1. Validation of Census: Ensures data integrity by validating all census requests before processing.
2. Census Create: Creates new census records after validation and enrichment, publishing request to the designated Kafka topic to handle the creation process asynchronously.
3. Census Update: Updates existing records post-validation and enrichment by sending request to the designated Kafka update topic.
4. Census Bulk Update: Updates multiple census records in one operation after successful validation.
5. Census Search: Enables searching for census records with the provided search criteria.