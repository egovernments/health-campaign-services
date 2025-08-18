# Excel Ingestion Service - TODO

### Code Quality Issues
- [x] Fix typo: Rename `refernceId` to `referenceId` in GenerateResource model (requires migration strategy for backward compatibility)

### Unused Methods to Remove
- [ ] Remove unused `getFilename()` method from `FileStoreService.java`
- [ ] Remove unused `createPayloadWithCriteria()` method from `ApiPayloadBuilder.java`
- [ ] Remove unused `createMdmsPayload()` method from `ApiPayloadBuilder.java`
- [ ] Remove unused `createCampaignConfigMdmsPayload()` method from `ApiPayloadBuilder.java`

### Model Enhancements
- [x] Move boundaries to additionalDetails in GenerateResource model
- [x] Align logic with boundaries in additionalDetails change