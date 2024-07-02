# Plan Service

Plan service is a Health Microplanning Service that facilitates creation of micro plan configurations and micro plans. This functionality is exposed via REST API.

### DB UML Diagram

<img width="586" alt="DB UML Diagram" src="">

### Service Dependencies
- MDMS Service

### Swagger API Contract
[Link](https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/DIGIT-Specs/grouped-service-contracts/Domain%20Services/Plan%20Service/plan-1.0.0.yaml) to the swagger API contract yaml and editor link

### Service Details

#### API Details
BasePath `/plan-service`

Plan config APIs - contains create, update and search end point

* POST `/plan-service/config/_create` - Create Project, This API is used to create/add a new Project.

* POST `/plan-service/config/_update` - Update Project, This API is used to update the details of an existing Project.

* POST `/plan-service/config/_search` - Search Project, This API is used to search details of an existing Project.


* POST `/plan-service/_create` - Create Project Beneficiary, This API is used to create/add a new beneficiary for Project.

* POST `/plan-service/_update` - Update Project Beneficiary, This API is used to update beneficiary registration for Project.

* POST `/plan-service/_search` - Search Project Beneficiary, This API is used to search beneficiary registration for Project.

### Kafka Consumers
NA

### Kafka Producers
- plan-config-create-topic
- plan-config-update-topic

- save-plan
- update-plan
