# Changelog
All notable changes to this module will be documented in this file.

## 0.1.0 - 2024-05-28
#### Base ProjectFactory service 
  1. ProjectFactory Service manages campaigns: creation, updating, searching, and data generation.
  2. Project Mapping : In campaign creation full project mapping is done with staff, facility and resources along with proper target values.
  3. Create Data: Validates and creates resource details of type facility,user and boundary.
  4. Generate Data: Generates sheet data of type facility,user and boundary.
  5. Boundary and Resource Validation: Validates boundaries and resources during campaign creation and updating.

## 0.2.0 - 2024-08-7
#### ProjectFactory service version 0.2
   1. Timeline integration for workflow of campaign.
   2. Call user, facility and boundary generate when boundaries changed in campaign update flow
   3. Generate target template based on delivery conditions changed to anything from default.
