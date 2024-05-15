# Household

### Household Service
Household registry is a Health Campaign Service that facilitates maintenance of a household registry. The functionality is exposed via REST API.

### DB UML Diagram

<img width="607" alt="Screenshot 2023-03-29 at 2 37 44 PM" src="https://user-images.githubusercontent.com/123379163/228484993-30fb0910-0f53-47b7-b373-5e8ced21c7c7.png">

### Service Dependencies
- Idgen Service
<<<<<<< Updated upstream

[//]: # (- confirm it)
=======
- 
[//]: # (- confirm *
>>>>>>> Stashed changes

### Swagger API Contract
Link to the swagger API contract yaml and editor link like below

https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/health-campaign-services/v1.0.0/docs/health-api-specs/contracts/registries/household.yml

### Service Details

#### API Details
BasePath `/Household`

Household service APIs - contains create, update, delete and search end point

* POST `/member/v1/_create` - Create Household member, This API is used to create/add a new Household member.

* POST `/member/v1/bulk/_create` - Create bulk Household member, This API is used to create/add new household member in bulk.

* POST `/member/v1/_update` - Update Household member, This API is used to update the details of a existing Household member.

* POST `/member/v1/bulk/_update` - Update bulk Household member, This API is used to update the details of existing household member in bulk.

* POST `/member/v1/_delete` - Delete Household member, This API is used to soft delete details of an existing Household member.

* POST `/member/v1/bulk/_delete` - Delete bulk Household member, This API is used to soft delete details of an existing Household member in bulk.

* POST `/member/v1/_search` - Search Household member, This API is used to search existing Household member.

* POST `/v1/_create` - Create Household, This API is used to create/add a new Household.

* POST `/v1/bulk/_create` - Create bulk Household, This API is used to create/add new household in bulk.

* POST `/v1/_update` - Update Household, This API is used to update the details of a existing Household.

* POST `/v1/bulk/_update` - Update bulk Household, This API is used to update the details of existing household in bulk.

* POST `/v1/_delete` - Delete Household, This API is used to soft delete details of an existing Household.

* POST `/v1/bulk/_delete` - Delete bulk Household, This API is used to soft delete details of an existing Household in bulk.

* POST `/v1/_search` - Search Household, This API is used to search existing Household.


### Kafka Consumers

- create-household-bulk-topic
- update-household-bulk-topic
- delete-household-bulk-topic
- household-member-consumer-bulk-create-topic
- household-member-consumer-bulk-update-topic
- household-member-consumer-bulk-delete-topic

### Kafka Producers

- save-household-topic
- update-household-topic
- delete-household-topic
- save-household-member-topic
- update-household-member-topic
- delete-household-member-topic

## Pre commit script

[commit-msg](https://gist.github.com/jayantp-egov/14f55deb344f1648503c6be7e580fa12)
