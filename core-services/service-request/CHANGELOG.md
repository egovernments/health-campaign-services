All notable changes to this module will be documented in this file.

## 1.2.0 - 2025-05-07

* Upgraded service-request module to Java 17 with updated pom.xml
* Replaced all javax.* imports with jakarta.* to align with Jakarta EE and Java 17 standards
* Introduced multi-tenancy support with schema resolution and tenant-aware request handling

## 1.1.0 - 2025-06-11

- Added update service request api support
- Added `clientReferenceId` and `serviceClientReferenceId` for `AttributeValue`
- Introduced `Numeric` data type for `AttributeDefinition`
- Validation for service request for client id already exists on create

## 1.0.1 - 2024-08-29 

- Added `BOOLEAN` DataType in `AttributeDefinition`

## 1.0.0

- Base version