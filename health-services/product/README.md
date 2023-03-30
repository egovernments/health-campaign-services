# Product

### Product Service
Product registry is a Health Campaign Service that facilitates maintenance of a Product registry on the DIGIT platform. The functionality is exposed via REST API.

### DB UML Diagram

<img width="552" alt="Screenshot 2023-03-29 at 2 43 14 PM" src="https://user-images.githubusercontent.com/123379163/228486455-cfa9ebf0-defe-4f47-a568-4db6fe0684c7.png">


### Service Dependencies
- Idgen Service

### Swagger API Contract
Link to the swagger API contract yaml and editor link like below

https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/health-campaign-services/v1.0.0/docs/health-api-specs/contracts/registries/product.yml

### Service Details

#### API Details
BasePath `/product`

Product service APIs - contains create, update, delete and search end point

* POST `/v1/_create` - Create Product, This API is used to create/add a new product.

* POST `/v1/_update` - Update Product, This API is used to update the details of an existing product.

* POST `/v1/_search` - Search Product, This API is used to search existing product.

* POST `/variant/v1/_create` - Create Product variant, This API is used to create/add a new product variant.

* POST `/variant/v1/_update` - Update Product variant, This API is used to update the details of an existing product variant.

* POST `/variant/v1/_search` - Search Product variant, This API is used to search existing product variant.

### Kafka Producers

- save-product-topic
- update-product-topic
- save-product-variant-topic
- update-product-variant-topic


## Pre commit script

[commit-msg](https://gist.github.com/jayantp-egov/14f55deb344f1648503c6be7e580fa12)
