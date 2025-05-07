# Changelog
All notable changes to this module will be documented in this file.

## 1.0.2 - 2025-05-07
1. Added indexes to the Plan, Plan Facility, and Plan Employee Assignment tables to improve lookup performance on large datasets.
2. Addressed technical debt to improve code maintainability, structure, and performance.

## 1.0.1 - 2025-01-30
1. Enabled support for Mixed Distribution Strategy for Registration and Service Delivery
2. Adding additional search filters for Estimation Dashboard

## 1.0.0 - 2024-12-03
### Plan Service

##### Plan Configuration

1. Validation of Plan Configuration: Validates all plan configuration requests against MDMS and Project Factory before processing the requests.
2. Plan Configuration Create: Validates and enriches new plan configurations before publishing them to the Kafka topic for asynchronous processing.
3. Plan Configuration Update: Updates existing plan configurations post-validation and enrichment by pushing requests to the Kafka update topic.
4. Plan Configuration Search: Facilitates searching for plan configurations using the provided search criteria.
5. Resource Generator Consumer: Listens to resource generator plan configuration update topic to update plan configuration.

#### Plan

1. Plan manages: validation of plans, plan search, plan create, plan update.
2. Validation of  plan: Plan service validates plan request before it takes action on it like update or create.
3. Plan Create: Plan service creates a plan after successful validation is done. It sends create request on topic to create plan.
4. Plan Update : Plan service creates a plan after successful validation is done. It sends update request on topic to resource estimation service to further process.
5. Plan Search: This enables to search plan based on provided search string.
6. Plan Bulk Update: Allows updating multiple plans in a single operation after validation.
7. Resource Generator Consumer: Listens to resource plan create topic to trigger the creation of new plans.

#### Plan Facility

1. Validation: Validates plan facility requests against MDMS, Facility Service and Project Factory before processing the requests.
2. Plan Facility Create: Creates a plan facility after validation and enrichment, pushing the create request to the designated kafka topic.
3. Plan Facility Update: Updates existing facilities post-validation by pushing update requests to the Kafka topic. Also sends the update request to Census service for facility mapping.
4. Plan Facility Search: Searches Plan Facility for the provided search criteria.
5. Project Factory Consumer: Listens to project factory consumer to enrich and create new plan facility. 

#### Plan Employee Assignment

1. Validation: Validates plan employee assignment requests against MDMS, User service and Project Factory before processing the requests.
2. Plan Employee Assignment Create: Assigns employees to plans post-validation and enrichment, considering roles and jurisdictions by pushing request to create kafka topic.
3. Plan Employee Assignment Update: Updates existing assignments after validation, publishing the changes to the designated Kafka update topic. 
4. Plan Employee Assignment Search: Enables searching for employee assignments using provided search criteria.