# Changelog

## 1.0.2 - 2025-05-06
1. Added Draft API : Supports creating draft microplans, enabling estimation review before finalization.
2. Enhanced plan estimation creation logic to operate independently of uploaded Excel sheets.
3. Enabled adding extra editable columns to the microplan sheet by system admins.
4. Addressed technical debt to improve code maintainability, structure, and performance.

## 1.0.1 - 2025-01-30
1. Enhancements for Microplan Estimation Downloadable Excel Report
2. Enabled support for Mixed Distribution Strategy for Registration and Service Delivery
3. Changes for supporting filters in Estimation Dashboard

## 1.0.0 - 2024-12-03
#### Resource Generator Service
The Resource Generator Service introduces comprehensive functionalities for microplanning resource estimation and campaign integration:

1. File Processing: Supports Excel, Shapefiles, and GeoJSON for resource estimation and validates input data against the plan configuration.
2. Resource Estimation: Calculates resources using predefined formulas and updates plans by publishing data to relevant topics.
3. Boundary Validation: Ensures boundary integrity and validates data during resource calculations.
4. Process Triggers: Automates Plan-Facility creation, Census data creation, and Plan generation based on file uploads and validations.
5. Campaign Integration: Integrates estimated resources and boundaries with the Campaign Manager for streamlined planning.
6. Result Upload: Automates the upload of approved resource sheets to the filestore and updates plan configurations.
7. HCM Integration: Updates project factory with resource estimates for accurate campaign planning.