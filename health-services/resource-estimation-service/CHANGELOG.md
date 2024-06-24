# Changelog
All notable changes to this module will be documented in this file.

## 1.0.0 - 2024-06-24
#### Base Resource Estimation Service 
  1. Resource Estimation Service manages file processing: validating data, calculation for files, updating plan, integration with campaign.
  2. File Processing : In file processing , it process files present in plan configuration by calculating resources.  
  3. Updating Plan: It create a plans based on row and update those by putting it on topic that is consumed by plan service.
  4. Integrate with Campaign manager : After processing calculations it also integrate resources and boundary with Campaign Manager.
  5. Boundary and Data Validation: Validates boundaries and excel data during calculations.