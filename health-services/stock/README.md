# Stock

### Stock Service
Stock registry is a Health Campaign Service that facilitates operations for Stock on the DIGIT platform. The functionality is exposed via REST API.

### DB UML Diagram

<img width="319" alt="Screenshot 2023-03-29 at 2 47 13 PM" src="https://user-images.githubusercontent.com/123379163/228487504-7da08d7f-9240-4c62-a255-c03e6efabe33.png">

### Service Dependencies
- Idgen Service
- Facility Service
- Project Facility Service
- Product Service

### Swagger API Contract
Link to the swagger API contract yaml and editor link like below

https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/health-campaign-services/v1.0.0/docs/health-api-specs/contracts/stock.yml

### Service Details

#### API Details
BasePath `/stock/v1`

Stock service APIs - contains create, update, delete and search end point

* POST `/stock/v1/_create` - Create Stock, This API is used to Create/Add a new record for transfer of product variant stock.

* POST `/stock/v1/bulk/_create` - Create bulk Stock, This API is used to Create/Add a new record for transfer of product variant stock in bulk.

* POST `/stock/v1/_update` - Update Stock, This API is used to Update record for transfer of a stock transaction.

* POST `/stock/v1/bulk/_update` - Update bulk Stock, This API is used to Update records for transfer of a stock transaction in bulk.

* POST `/stock/v1/_delete` - Delete Stock, This API is used to Soft delete record for transfer of a stock transaction.

* POST `/stock/v1/bulk/_delete` - Delete bulk Stock, This API is used to Soft delete records for transfer of a stock transaction.

* POST `/stock/v1/_search` - Search Stock, This API is used to Search for stock transaction.

* POST `/stock/reconciliation/v1/_create` - Create Stock reconciliation, This API is used to Create/Add a new stock reconciliation record for a product variant.

* POST `/stock/reconciliation/v1/bulk/_create` - Create bulk Stock reconciliation, This API is used to Create/Add a new stock reconciliation record for a product variant in bulk.

* POST `/stock/reconciliation/v1/_update` - Update Stock reconciliation, This API is used to Update stock reconciliation record.

* POST `/stock/reconciliation/v1/bulk/_update` - Update bulk Stock reconciliation, This API is used to Update stock reconciliation records.

* POST `/stock/reconciliation/v1/_delete` - Delete Stock reconciliation, This API is used to Soft delete stock reconciliation record.

* POST `/stock/reconciliation/v1/bulk/_delete` - Delete bulk Stock reconciliation, This API is used to Soft delete stock reconciliation records.

* POST `/stock/reconciliation/v1/_search` - Search Stock reconciliation, This API is used to Search for stock reconciliation record.


### Kafka Consumers

- create-stock-bulk-topic
- update-stock-bulk-topic
- delete-stock-bulk-topic
- create-stock-reconciliation-bulk-topic
- update-stock-reconciliation-bulk-topic
- delete-stock-reconciliation-bulk-topic

### Kafka Producers

- save-stock-topic
- update-stock-topic
- delete-stock-topic
- save-stock-reconciliation-topic
- update-stock-reconciliation-topic
- delete-stock-reconciliation-topic

## Pre commit script

[commit-msg](https://gist.github.com/jayantp-egov/14f55deb344f1648503c6be7e580fa12)

## Updates 
- Stock Search 
  - `productVariantId`, and `waybillNumber` now accepts a list of entities instead of single entity to search stock
- Stock Reconciliation Search
  - `facilityId`, and `productVariantId` now accepts a list of entities instead of single entity to search stock reconciliation
## Usage
- Start the service
- Access the API endpoints for searching `stock` and `stock reconciliation`
- Pass list parameters for the search fields mentioned in updates 
