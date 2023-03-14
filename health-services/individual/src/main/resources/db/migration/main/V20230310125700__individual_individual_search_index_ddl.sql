CREATE INDEX IF NOT EXISTS idx_id_clientReferenceId_givenName_familyName_otherNames_dateOfBirth_gender
    ON INDIVIDUAL
    (id,
    clientReferenceId,
    givenName,
    familyName,
    otherNames,
    dateOfBirth,
    gender);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);

CREATE INDEX IF NOT EXISTS
    idx_id_individualId_identifierType_identifierId_isDeleted_createdBy_lastModifiedBy_createdTime_lastModifiedTime
    ON INDIVIDUAL_IDENTIFIER
    (id,
    individualId,
    identifierType,
    identifierId,
    isDeleted,
    createdBy,
    lastModifiedBy,
    createdTime,
    lastModifiedTime);


