# Changelog
All notable changes to this module will be documented in this file.

## 1.0.0 - 2024-11-28
#### Census Service
1. Census Service manages: validation of census, census create, census search, census update, census bulk update.
2. Validation of Census: Census service validates census request before it takes action on it like update or create.
3. Census Create: Census service creates a census after successful validation is done. It sends create request on topic to create census.
4. Census Update : Census service updates a census after successful validation is done. It sends update request on topic to update census.
5. Census Search: This enables to search census based on provided search string.
6. Census Bulk Update: Census service updates a list of census records after successful validation is done. It sends update request on topic to update census bulk record. 