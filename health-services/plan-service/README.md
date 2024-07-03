# Plan Service Documentation

## Overview

The PlanService is a tool designed for managing plan configurations and microplans. It supports various functionalities such as creating, updating, validating, and processing plan configurations, microplans, and related elements. This service can operate independently for micro planning or integrate with DIGIT HCM for campaign execution and monitoring.

## Key Features

- **Data Upload for Microplanning**: Upload essential data in formats including .xlsx (Excel), Shapefiles, and GeoJSON.
- **Assumptions Configuration**: Configure assumptions crucial for estimation processes.
- **Formula Configuration**: Set up formulae to calculate estimated resources (e.g., human resources, commodities, budgets).
- **Visualization**: Visualize microplans on a map layer using GIS technologies.
- **Output Generation**: Generate, save, and print microplans.

## Service Dependencies

- **Mdms-service**: Dependency for managing Master Data Management System.

## API Documentation

- **Swagger Link**: [Plan Service Swagger](https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/DIGIT-Specs/grouped-service-contracts/Domain%20Services/Plan%20Service/plan-1.0.0.yaml)

## Database and Persistence

- **DB Diagrams**: Detailed diagrams for Microplan and Plan Configuration are available.

## Configuration and Deployment

- **Persister Config**: Configuration for persistence can be found in [plan-service-persister.yml](https://github.com/egovernments/configs/blob/UNIFIED-QA/health/egov-persister/plan-service-persister.yml).
- **Helm Chart**: Deployment details available in the [Helm chart](https://github.com/egovernments/DIGIT-DevOps/tree/unified-env/deploy-as-code/helm/charts/health-services/plan-service).

## Role and Access Control

- **Access Control Role**: Configure the role `MICROPLAN_ADMIN` in the ‘ACCESSCONTROL-ROLES’ module.
- **Role-Action Mapping**: Map actions to role codes in the ‘ACCESSCONTROL-ROLEACTIONS’ module as per the provided mappings.

### Role-Action Mappings (Dev Environment)

- `/plan-service/plan/_create`: `MICROPLAN_ADMIN`
- `/plan-service/plan/_search`: `MICROPLAN_ADMIN`
- `/plan-service/plan/_update`: `MICROPLAN_ADMIN`
- `/plan-service/config/_create`: `MICROPLAN_ADMIN`
- `/plan-service/config/_search`: `MICROPLAN_ADMIN`
- `/plan-service/config/_update`: `MICROPLAN_ADMIN`

## Environment Variables

- Configure environment variables such as `db-host`, `db-name`, `db-url`, `domain`, and other DIGIT core platform services configurations before deployment. Example configurations can be found in the [unified-qa.yaml](https://github.com/egovernments/DIGIT-DevOps/blob/unified-env/deploy-as-code/helm/environments/unified-qa.yaml).

## MDMS Configuration

- Configure MDMS data for Plan Service including UOM config, Metric config, Assumptions config, Input rules, Output rules, Campaign Based Schema, Microplan status, Map layers, Map filters, Preview Aggregates, and UI configs. Reference data can be found [here](https://github.com/egovernments/egov-mdms-data/tree/UNIFIED-QA/data/mz/health/hcm-microplanning).

## Reference Documents

- **MDMS Technical Document**: [Mdms service](https://core.digit.org/platform/core-services/mdms-master-data-management-service)
- **Persister Technical Document**: [Persister service](https://core.digit.org/platform/core-services/persister-service)
- **API Contract**: [API Contract](https://github.com/egovernments/SANITATION/blob/develop/API-CONTRACTS/pqm/PQM_API_ANOMALY_Contract.yaml)
