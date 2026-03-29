# FHIR Transformer

A Spring Boot service that validates, converts and forwards FHIR R5 resources into eGov/DIGIT domain models and APIs (facilities, product variants, stock, stock reconciliation, boundaries). 
It also builds FHIR Bundles from backend search responses.


## Quick summary
- Language / build: Java 17, Maven (mvn wrapper included).
- Key technologies: Spring Boot, HAPI-FHIR (R5), Kafka
- Package base: org.egov.fhirtransformer
- Purpose: Provide a lightweight application to receive, validate, transform, and forward FHIR resources.

## Features
- Validate incoming FHIR JSON against loaded profiles (custom profiles in /profiles).</br>
- Convert backend responses (Facility, ProductVariant, Stock, StockReconciliation, Boundary) to FHIR resources and produce Bundles.</br>
- Parse incoming FHIR Bundles and convert Location / SupplyDelivery / InventoryItem / InventoryReport entries into domain models and call backend create/update endpoints.</br>
- Publish invalid FHIR Bundles to a DLQ Kafka topic with validation errors.</br>
- Publish failed Resources to Failed Kafka Topis</br>

## Build:
  ./mvnw -DskipTests package</br>
  or </br>
  mvn -DskipTests package</br>


Run:</br>
  ./mvnw spring-boot:run</br>
  or </br>
  java -jar target/fhirtransformer-0.0.1-SNAPSHOT.jar</br>
  Configuration (application.properties / environment)</br>


## Recommended architecture and components
- Controllers: src/main/java/org/egov/fhirtransformer/web/controller/
  - Accept HTTP requests containing FHIR JSON or Request payloads.
  - Exposes endpoints under /fhir-api (health, validate, fetchAll*, consumeFHIR).
- Validator:  src/main/java/org/egov/fhirtransformer/validator
  - Loads JSON profiles from resources/profiles, registers with HAPI FHIR ValidationSupportChain
  - validates incoming payloads using a FHIR library .
- Services (org.egov.fhirtransformer.service)
  - FhirParseNLoadService: parses incoming Bundle and distributes entries to mapping/request-builder services (SupplyDeliveryToStockService, LocationToFacilityService, LocationToBoundaryService, InventoryItemToProductVariant, InventoryReportToStockReconciliationService). DIGITHCMFacilityMapper, DIGITHCMBoundaryMapper, DIGITHCMStockMapper: convert between FHIR resources (Location, SupplyDelivery, InventoryReport, InventoryItem) and domain objects (Facility, BoundaryRelation, Stock, StockReconciliation, ProductVariant).
  - ApiIntegrationService: wraps RestTemplate calls to backend services and forms URIs.
  - FhirTransformerService: Transforms responses (Facility, ProductVariant, Stock, StockReconciliation, Boundary) to FHIR resources and produce Bundles.
- Mappers:
  - DIGITHCMFacilityMapper, DIGITHCMBoundaryMapper, DIGITHCMStockMapper: convert between FHIR resources (Location, SupplyDelivery, InventoryReport, InventoryItem) and domain objects (Facility, BoundaryRelation, Stock, StockReconciliation, ProductVariant).
- Utilities:
  - BundleBuilder: builds FHIR Bundles (SEARCHSET) from resource lists.
  - MapUtils: small helpers for splitting IDs and safe getters.
  - Kafka: KafkaProducerService publishes DLQ messages and failed messages (validation errors) to configured topic.
- Constants:
   - central place for FHIR profile URIs, identifier systems, UOM, and other constants.
 

## Public REST endpoints (FhirApiController)

http://localhost:8006/fhir-api/health - Health check endpoint for service availability
</br>
http://localhost:8006/fhir-api/validate - Validates a FHIR JSON payload against configured FHIR profiles.
</br>
http://localhost:8006/fhir-api/fetchAllFacilities?offset=0&limit=1&tenantId=dev - Fetches Facility data and returns it as a FHIR Location Bundle.
</br>
http://localhost:8006/fhir-api/fetchAllProductVariants?offset=0&limit=1&tenantId=dev - Fetches ProductVariant data and returns it as a FHIR InventoryItem Bundle.
</br>
http://localhost:8006/fhir-api/fetchAllStocks?offset=0&limit=1&tenantId=dev - Fetches Stock data and returns it as a FHIR SupplyDelivery Bundle.
</br>
http://localhost:8006/fhir-api/fetchAllStockReconciliation?offset=0&limit=1&tenantId=dev - Fetches StockReconciliation data and returns it as a FHIR InventoryReport Bundle. </br>
http://localhost:8006/fhir-api/fetchAllBoundaries?offset=0&limit=1&tenantId=dev - Fetches boundary hierarchy data and returns it as a FHIR Location Bundle.</br>
http://localhost:8006/fhir-api/consumeFHIR - Consumes a FHIR Bundle payload and loads supported resources into DIGIT services.</br>



- GET /fhir-api/health
  - Returns a simple health message.
- POST /fhir-api/validate
  - Body: raw FHIR JSON (resource or bundle)
  - Response: “Valid FHIR resource” or an “Invalid FHIR resource. errors: […]” message (validation result messages are returned as a single string).
- POST /fhir-api/fetchAllFacilities
  - ModelAttribute: URLParams (limit, offset, tenantId)
  - Body: FacilitySearchRequest (org.egov.common.models.facility)
  - Response: FHIR Bundle (stringified) of Location resources representing facilities.
- POST /fhir-api/fetchAllProductVariants
  - ModelAttribute: URLParams
  - Body: ProductVariantSearchRequest
  - Response: FHIR Bundle of InventoryItem resources.
- POST /fhir-api/fetchAllStocks
  - ModelAttribute: URLParams
  - Body: StockSearchRequest
  - Response: FHIR Bundle of SupplyDelivery resources.
- POST /fhir-api/fetchAllStockReconciliation
  - ModelAttribute: URLParams
  - Body: StockReconciliationSearchRequest
  - Response: FHIR Bundle of InventoryReport resources.
- POST /fhir-api/fetchAllBoundaries
  - ModelAttribute: BoundaryRelationshipSearchCriteria
  - Body: RequestInfo
  - Response: FHIR Bundle of Location resources representing boundaries.
- POST /fhir-api/consumeFHIR
  - Body: raw FHIR Bundle JSON
  - Behavior: FhirParseNLoadService parses bundle, converts relevant entries and calls backend APIs to create/update domain resources; returns a map of processed metrics (counts of total, new, existing per entity)


## Deployment notes

- Ensure backend endpoints are reachable and credentials/URLs set via properties.
- Ensure Kafka configurations are correct for your environment.
- Profiles for FHIR validation must be present at runtime under resources/profiles (CustomFHIRValidator expects to load JSON profile files).

