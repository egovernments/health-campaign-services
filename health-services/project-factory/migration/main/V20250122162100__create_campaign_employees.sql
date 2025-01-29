-- Create the table
CREATE TABLE eg_cm_campaign_employees (
    id CHARACTER VARYING(128) PRIMARY KEY,
    campaignnumber CHARACTER VARYING(128) NOT NULL,
    "name" CHARACTER VARYING(128) NOT NULL,
    "role" CHARACTER VARYING(128) NOT NULL,
    mobilenumber CHARACTER VARYING(64) NOT NULL,
    userserviceuuid CHARACTER VARYING(128),
    employeeType CHARACTER VARYING(128) NOT NULL,
    tokenString TEXT,
    additionaldetails JSONB,  -- Nullable JSONB column
    isactive BOOLEAN NOT NULL,
    createdby CHARACTER VARYING(128) NOT NULL,
    lastmodifiedby CHARACTER VARYING(128) NOT NULL,
    createdtime BIGINT NOT NULL,
    lastmodifiedtime BIGINT NOT NULL
);

-- Adding index on campaignnumber
CREATE INDEX idx_campaignemployees_campaignnumber ON eg_cm_campaign_employees (campaignnumber);

-- Adding index on combination of campaignnumber and mobileNumber
CREATE INDEX idx_campaignnumber_mobilenumber ON eg_cm_campaign_employees (campaignnumber, mobileNumber);

-- Adding unique constraint for combination of campaignnumber and mobileNumber
CREATE UNIQUE INDEX uniq_active_campaign_mobile
ON eg_cm_campaign_employees (campaignnumber, mobileNumber);
