CREATE TABLE project (
                         id varchar(50) NOT NULL PRIMARY KEY,
                         tenantId varchar(255) NOT NULL,
                         projectTypeId varchar(255),
                         addressId varchar(255),
                         startDate BIGINT,
                         endDate BIGINT,
                         isTaskEnabled BOOLEAN,
                         parent varchar(255),
                         additionalDetails json,
                         createdBy varchar(255),
                         createdTime BIGINT,
                         lastModifiedBy varchar(255),
                         lastModifiedTime BIGINT,
                         rowVersion BIGINT,
                         isDeleted BOOLEAN
);