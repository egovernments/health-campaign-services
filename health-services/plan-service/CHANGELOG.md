# Changelog
All notable changes to this module will be documented in this file.

## 1.0.0 - 2024-06-24
#### Plan Service 
  1. Plan Service manages: validation of plans, plan search, plan create, plan update.
  2. Validation of  plan: Plan service validates plan request before it takes action on it like update or create.  
  3. Plan Create: Plan service creates a plan after successful validation is done. It sends create request on topic to create plan. 
  4. Plan Update : Plan service creates a plan after successful validation is done. It sends update request on topic to resource estimation service to further process.
  5. Plan Search: This enables to search plan based on provided search string.