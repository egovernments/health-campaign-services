# Excel Ingestion Service - TODO

### Code Quality Issues
- [x] Fix typo: Rename `refernceId` to `referenceId` in GenerateResource model (requires migration strategy for backward compatibility)

### Unused Methods to Remove
- [x] ~~Remove unused `getFilename()` method from `FileStoreService.java`~~ (Not removed - this is actually used as an override in anonymous ByteArrayResource class)
- [x] Remove unused `createPayloadWithCriteria()` method from `ApiPayloadBuilder.java`
- [x] Remove unused `createMdmsPayload()` method from `ApiPayloadBuilder.java`
- [x] Remove unused `createCampaignConfigMdmsPayload()` method from `ApiPayloadBuilder.java`

### Model Enhancements
- [x] Move boundaries to additionalDetails in GenerateResource model
- [x] Align logic with boundaries in additionalDetails change