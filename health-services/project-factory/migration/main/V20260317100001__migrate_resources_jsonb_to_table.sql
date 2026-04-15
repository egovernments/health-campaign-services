-- One-time idempotent migration — copy resources from campaigndetails JSONB
-- into eg_cm_resource_details.
--
-- Runs AFTER V20260317100000 (which adds parentresourceid, filename, isactive columns).
--
-- Safety:
--   - Skips rows where type or filestoreid is missing in the JSONB.
--   - Idempotent: NOT EXISTS check prevents duplicate insertion on re-run.
--   - Sets parentresourceid = NULL and isactive = true for all migrated records.
--   - Preserves original audit timestamps from the parent campaign row.

INSERT INTO eg_cm_resource_details (
    id,
    tenantid,
    campaignid,
    type,
    filestoreid,
    processedfilestoreid,
    filename,
    status,
    action,
    isactive,
    parentresourceid,
    hierarchytype,
    additionaldetails,
    createdby,
    lastmodifiedby,
    createdtime,
    lastmodifiedtime
)
SELECT
    gen_random_uuid()::text                               AS id,
    cd.tenantid                                           AS tenantid,
    cd.id                                                 AS campaignid,
    (resource ->> 'type')                                 AS type,
    COALESCE(resource ->> 'filestoreId', resource ->> 'fileStoreId')
                                                          AS filestoreid,
    NULLIF(COALESCE(resource ->> 'processedFileStoreId',
                   resource ->> 'processedfilestoreid'), '')
                                                          AS processedfilestoreid,
    NULLIF(resource ->> 'filename', '')                   AS filename,
    COALESCE(NULLIF(resource ->> 'status', ''), 'completed')
                                                          AS status,
    'create'                                              AS action,
    true                                                  AS isactive,
    NULL                                                  AS parentresourceid,
    NULLIF(COALESCE(resource ->> 'hierarchyType',
                   resource ->> 'hierarchytype'), '')     AS hierarchytype,
    '{}'::jsonb                                           AS additionaldetails,
    cd.createdby                                          AS createdby,
    cd.lastmodifiedby                                     AS lastmodifiedby,
    cd.createdtime                                        AS createdtime,
    cd.lastmodifiedtime                                   AS lastmodifiedtime
FROM eg_cm_campaign_details cd,
     jsonb_array_elements(cd.campaigndetails -> 'resources') AS resource
WHERE
    -- Only process campaigns that have a non-empty resources array
    cd.campaigndetails -> 'resources' IS NOT NULL
    AND jsonb_array_length(cd.campaigndetails -> 'resources') > 0
    -- Skip resources with missing mandatory fields
    AND COALESCE(resource ->> 'filestoreId', resource ->> 'fileStoreId') IS NOT NULL
    AND COALESCE(resource ->> 'filestoreId', resource ->> 'fileStoreId') <> ''
    AND (resource ->> 'type') IS NOT NULL
    AND (resource ->> 'type') <> ''
    -- Idempotency: skip if already migrated
    AND NOT EXISTS (
        SELECT 1
        FROM eg_cm_resource_details rd
        WHERE rd.campaignid  = cd.id
          AND rd.type        = (resource ->> 'type')
          AND rd.filestoreid = COALESCE(resource ->> 'filestoreId', resource ->> 'fileStoreId')
          AND rd.isactive    = true
    );
