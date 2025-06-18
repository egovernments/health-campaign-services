All notable changes to this module will be documented in this file.

## 1.0.22 - 2025-06-04

* Introduced BeneficiaryIdGenService to enable integration conditionally with the Beneficiary ID Generation system.
* Search/Update for Beneficiary ID records using `/id/id_pool/_search` and `/id/id_pool/_update`.

## 1.0.21 - 2025-02-28
- Fixed populate error details bug in common utils

## 1.0.18 - 2024-08-09
- Added validateClientReferenceIdsFromDB method to GenericRepository.

## 1.0.16 - 2024-05-29
- Introduced multiple reusable functions to streamline and simplify the codebase.
- Enhanced function modularity for better maintainability and readability.
- Integrated Core 2.9LTS
- Refactored existing code to utilize new reusable functions, reducing redundancy.
- Improved overall code structure for more efficient execution and easier future modifications.
- Changed Dockerfile, base image required for Java 17.

## 1.0.12
- The exception was replaced with CustomException in the service request client.

  
## 1.0.0
- Base version
