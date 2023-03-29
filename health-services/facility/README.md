# Facility

### Facility Service
Facility registry is a Health Campaign Service that facilitate warehouse on the DIGIT platform. The functionality is exposed via REST API.

### DB UML Diagram

##<img width="541" alt="Screenshot 2023-03-29 at 2 23 24 PM" src="https://user-images.githubusercontent.com/123379163/228481126-e55c4d15-196c-4991-83cf-dffd10d1edc2.png">

# Service Dependencies
- Idgen Service

### Swagger API Contract
Link to the swagger API contract yaml and editor link like below

https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/health-campaign-services/v1.0.0/docs/health-api-specs/contracts/registries/facility.yml#!/

### Service Details

#### API Details
BasePath `/facility/v1`

Facility service APIs - contains create, update, delete and search end point

a) POST `/facility/v1/_create` - Create Facility, This API is internally call from facility controller to create/add a new facility.

b) POST `/facility/v1/bulk/_create` - Create bulk Facility, This API is internally call from facility controller to create/add new facilities in bulk.

c) POST `/facility/v1/_update` - Update Facility, This API is internally call from facility controller to update the details of an existing facility.

d) POST `/facility/v1/bulk/_update` - Update bulk Facility, This API is internally call from facility controller to update the details of existing facilities in bulk.

e) POST `/facility/v1/_delete` - Delete Facility, This API is internally call from facility controller to soft delete details of an existing facility.

f) POST `/facility/v1/bulk/_delete` - Delete bulk Facility, This API is internally call from facility controller to soft delete details of an existing facility in bulk.

g) POST `/facility/v1/_search` - Search Facility, This API is internally call from facility controller to search existing facility.

### Property Dependencies

Below properties define the Facility Configuration

### Kafka Consumers

- create-facility-bulk-topic
- update-facility-bulk-topic
- delete-facility-bulk-topic

### Kafka Producers

- save-facility-topic
- update-facility-topic
- delete-facility-topic

## Pre commit script

[commit-msg](https://gist.github.com/jayantp-egov/14f55deb344f1648503c6be7e580fa12)
