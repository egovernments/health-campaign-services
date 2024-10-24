# Changelog
## 1.0.0 - 2024-06-24
#### Base Resource Estimation Service
  1. Resource Estimation Service manages file processing: validating data, calculating for files, updating plans, and integrating with campaigns.
  2. File Processing: In file processing, it processes files present in plan configuration by calculating resources.
  3. Updating Plan: It creates plans based on rows and updates those by putting them on topics that are consumed by the plan service.
  4. Integrate with Campaign Manager: After processing calculations, it also integrates resources and boundary with the Campaign Manager.
  5. Boundary and Data Validation: Validates boundaries and excel data during calculations.