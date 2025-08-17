# Excel Ingestion Service - TODO

### Code Quality Issues
- [ ] Fix typo: Rename `refernceId` to `referenceId` in GenerateResource model (requires migration strategy for backward compatibility)

### Unused Methods to Remove
- [ ] Remove unused `getFilename()` method from `FileStoreService.java`
- [ ] Remove unused `createPayloadWithCriteria()` method from `ApiPayloadBuilder.java`
- [ ] Remove unused `createMdmsPayload()` method from `ApiPayloadBuilder.java`
- [ ] Remove unused `createCampaignConfigMdmsPayload()` method from `ApiPayloadBuilder.java`