All notable changes to this module will be documented in this file.

## 1.0.25 - 2025-01-03
- Added BeneficiaryType Enum, HouseHoldType Enum
- Updated ProjectType, Target for BeneficiaryType change, Household, HouseholdSearch for HouseHoldType changes
- Added DownsyncCLFHousehold, DownsyncCLFHouseholdResponse, HouseholdMemberMap
- Updated DownsyncCriteria to include householdId

## 1.0.21 - 2024-08-07
- Added UserActionEnum, UserAction Entities, TaskStatus enum
- Added isCascadingProjectDateUpdate in ProjectRequest model
- Removed status field from EgovModel 


## 1.0.20 - 2024-05-29
- Added EgovModel, EgovSearchModel, EgovOfflineModel, EgovOfflineSearchModel
- Updated Lombok to 1.18.22 for SuperBuilder annotation support.
- Upgraded to 2.9 LTS
- Changed Dockerfile, base image required for Java 17.

## 1.0.19 - 2024-02-26
- Updated project staff search to accept a list of staffIds
- Added senderId and receiverId fields to stock information.

## 1.0.18 - 2024-02-13
- Adding user uuid in individual search

## 1.0.11 - 2023-11-15
- Client reference id added for member of household
- revert of household search change

## 1.0.10
- Downsync models added
- Boundarycode changed to localityCode in household search

## 1.0.9
- stock models updated with sender and receiver information fields.

  
## 1.0.0
- Base version
