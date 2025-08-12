# Changelog
All notable changes to this module will be documented in this file.

## 0.1.0 - 2024-05-28
#### Base ProjectFactory service version 0.1
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

## 0.3.0 - 2024-12-03
#### ProjectFactory service version 0.3
  1.  Campaign Details Table Updates -> added new columns: parentId and active,removed unique constraint on campaignName.
  2.  Update Ongoing Campaign (can add new boundaries , edit facilities , user and target sheet).
  3.  Boundary Management Apis added.
  4.  Microplan Integration api (fetch-from-microplan api) added.


## 0.3.1 - 2025-02-13
#### ProjectFactory service version 0.3.1
  1. Facility creation changed from bulk to individual api for better error handling.
  2. Fixed the template generation cache to support multiple language template at same time.  
  3. Introduced the template validation based on meta data like locale & camapign id
  4. Target to dashboard mapping was introduced for additional configurations
  5. Boundary Bulk creation patch by trimming the boundary names if it exceeds the max limit
