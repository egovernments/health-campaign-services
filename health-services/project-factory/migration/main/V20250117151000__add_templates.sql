CREATE TABLE IF NOT EXISTS eg_cm_template (
    id UUID PRIMARY KEY,
    tenantId VARCHAR(50) NOT NULL, -- Added tenantId for completeness
    locale VARCHAR(10) NOT NULL,
    type VARCHAR(50) NOT NULL,
    isactive BOOLEAN NOT NULL DEFAULT true,
    filestoreid VARCHAR(255) NOT NULL,
    createdtime BIGINT NOT NULL, -- Audit field for creation timestamp
    lastmodifiedtime BIGINT NOT NULL, -- Audit field for last modification timestamp
    createdby VARCHAR(255), -- Audit field for creator
    lastmodifiedby VARCHAR(255), -- Audit field for modifier
    UNIQUE (locale, type) -- Ensure unique combination of locale and type
);
