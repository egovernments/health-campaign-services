# Resource Estimation Service Documentation

## Overview

The resource estimation service processes various file formats to estimate resources, generate microplans, upload result sheets, and update the HCM Admin Console. It integrates with other services like Mdms, Filestore, Project Factory, and Plan Service to ensure accurate resource estimation and campaign planning.

## Key Features

- **Parsing and Estimating Resources**: Processes file formats (Excel, Shapefiles, GeoJSON) to estimate necessary resources for micro planning by applying assumptions and formulas.
- **Validation**: Validates file data for proper data types and updates plan configuration status based on exceptions.
- **Generating Microplans**: Triggers the creation of microplans based on estimated resources and input data, detailing resource distribution for campaigns.
- **Uploading Results**: Uploads updated result sheets to a filestore, ensuring secure storage and accessibility.
- **Integrating with HCM Admin Console**: Updates the HCM Admin Console with estimated resources for effective campaign planning and execution.

## Service Dependencies

- **Mdms service**
- **Filestore service**
- **Project Factory service**
- **Plan service**

## API Specification

- Not Applicable (NA)

## Sequence Diagram

- To be provided (Placeholder)

## Producer Details

### Topics

| Topic                          | Description                                                             |
|--------------------------------|-------------------------------------------------------------------------|
| resource-microplan-create-topic | Pushes to Plan Service for microplan creation after resource estimation. |
| resource-plan-config-update-topic | Updates a plan configuration with INVALID_DATA status in case of processing exceptions. |

### Topics

| Topic                     | Description                                                            |
|---------------------------|------------------------------------------------------------------------|
| plan-config-update-topic  | Triggers resource estimation, microplan creation, and campaign manager integration. |

## Configuration and Deployment

- **Persister Config**: NA
- **Helm Chart**: Deployment details available [here](https://github.com/egovernments/DIGIT-DevOps/tree/unified-env/deploy-as-code/helm/charts/health-services/resource-estimation-service).

## Environment Variables

- Configure environment variables such as `db-host`, `db-name`, `db-url`, `domain`, and other DIGIT core platform services configurations before deployment.

## MDMS Configuration

- Configure MDMS data for Plan Service as per the documentation [here](https://github.com/egovernments/egov-mdms-data/tree/UNIFIED-QA/data/mz/health/hcm-microplanning).

## Reference Documents

- **MDMS Technical Document**: [Mdms service](https://core.digit.org/platform/core-services/mdms-master-data-management-service)

