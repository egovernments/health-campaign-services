# ProjectFactory-Service

The Project Factory Service is responsible for managing project-type campaigns, including creating, updating, searching, and creating campaigns.

### DB UML Diagram

![image](https://github.com/egovernments/DIGIT-Frontend/assets/137176738/8c43998d-742b-4629-ae90-63ab2b18772b)
![image](https://github.com/egovernments/DIGIT-Frontend/assets/137176738/3ff9609d-771a-4c6e-a769-54766e7111f7)


### Service Dependencies

#### Core services

- egov-localization
- egov-filestore
- egov-persister
- egov-mdms
- egov-idgen
- egov-boundaryservice-v2

#### Health services
- health-project
- health-hrms
- health-facility

### Swagger API Contract
Please refer to the below Swagger API contract, for ProjectFactory service to understand the structure of APIs and to have visualization of all internal APIs [Swagger API contract](https://editor.swagger.io/?url=https://raw.githubusercontent.com/jagankumar-egov/DIGIT-Specs/hcm-workbench/Domain%20Services/Health/project-factory.yaml)


## Service Details

### Funcatinality
1. ProjectFactory Service manages campaigns: creation, updating, searching, and data generation.
2. Project Mapping : In campaign creation full project mapping is done with staff, facility and resources along with proper target values.
3. Create Data: Validates and creates resource details of type facility,user and boundary.
4. Generate Data: Generates sheet data of type facility,user and boundary.
5. Boundary and Resource Validation: Validates boundaries and resources during campaign creation and updating.

### Feature
1. Functionality to create campaigns easily.
2. Uploading generated datas sheets to filestore and return filestore id for easy access.
3. Supports localisation.
4. Customizable Delivery Rules: Allows defining delivery rules for projects based on specific criteria.
5. Search and Filtering: Enables searching and filtering campaigns based on various parameters like status, date, and creator.
6. Batch Processing: Supports batch processing for creating and updating multiple campaigns simultaneously.

### External Libraries Used
[xlsx](https://github.com/SheetJS/sheetjs):- For reading and writing Excel files.

[ajv](https://github.com/ajv-validator/ajv):- For JSON schema validation.

[lodash](https://github.com/lodash/lodash):- For utility functions like data manipulation and object iteration.


### Configuration

-   Persister config: [here](https://github.com/egovernments/configs/blob/UNIFIED-UAT/health/egov-persister/project-factory-persister.yml)
-   Helm chart details: [here](https://github.com/egovernments/DIGIT-DevOps/blob/unified-env/deploy-as-code/helm/charts/health-services/project-factory/values.yaml)
  
### API Endpoints

-   `/project-factory/v1/project-type/create`: Creates a new project type campaign.
-   `/project-factory/v1/project-type/update`: Updates an existing project type campaign.
-   `/project-factory/v1/project-type/search`: Searches for project type campaigns based on specified criteria.
-   `/project-factory/v1/data/_create`: Creates or validates resource data (e.g., facility, user, boundary).
-   `/project-factory/v1/data/_search`: Searches for resource data based on specified criteria.
-   `/project-factory/v1/data/_generate`: Initiates the generation of new data based on provided parameters.
-   `/project-factory/v1/data/_download`: Downloads resource data based on specified criteria.


### Kafka Consumers

-   start-campaign-mapping: This topic is used by the service to initiate the mapping process for campaigns.

### Kafka Producers

-   save-project-campaign-details: This topic is used to save project campaign details after creation.
-   update-project-campaign-details: This topic is used to update project campaign details.
-   create-resource-details: This topic is used to create resource details.
-   update-resource-details: This topic is used to update resource details.
-   create-resource-activity: This topic is used to create resource activity creation.
-   create-generated-resource-details: This topic is used to save details for generated resources.
-   update-generated-resource-details: This topic is used to update details for generated resources.
