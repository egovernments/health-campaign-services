// Boundary Management Service Database Schema
// Compatible with dbdiagram.io

Table eg_bm_generated_template {
  id varchar(128) [pk, not null, note: 'Primary key for generated template records']
  filestoreid varchar(128) [note: 'File store ID for generated Excel file']
  status varchar(128) [note: 'Status: inprogress, completed, failed']
  tenantid varchar(128) [note: 'Tenant identifier']
  hierarchytype varchar(128) [note: 'Boundary hierarchy type (ADMIN, Health, etc.)']
  locale varchar(50) [note: 'Localization locale']
  createdby varchar(128) [note: 'User who created the record']
  createdtime bigint [note: 'Creation timestamp']
  lastmodifiedby varchar(128) [note: 'User who last modified the record']
  lastmodifiedtime bigint [note: 'Last modification timestamp']
  additionaldetails jsonb [note: 'Additional metadata in JSON format']
  referenceid varchar(128) [note: 'Reference ID for tracking']
  
  note: 'Stores generated boundary template information for download API'
}

Table eg_bm_processed_template {
  id varchar(128) [pk, not null, note: 'Primary key for processed template records']
  status varchar(128) [not null, note: 'Processing status: inprogress, completed, failed']
  tenantid varchar(128) [not null, note: 'Tenant identifier']
  hierarchytype varchar(128) [note: 'Boundary hierarchy type (ADMIN, Health, etc.)']
  filestoreid varchar(128) [not null, note: 'Original uploaded file store ID']
  processedfilestoreid varchar(128) [note: 'Processed result file store ID']
  action varchar(128) [not null, note: 'Action type: create, validate']
  createdby varchar(128) [not null, note: 'User who created the record']
  createdtime bigint [not null, note: 'Creation timestamp']
  lastmodifiedby varchar(128) [note: 'User who last modified the record']
  lastmodifiedtime bigint [note: 'Last modification timestamp']
  additionaldetails jsonb [note: 'Additional metadata including error details']
  referenceid varchar(128) [note: 'Reference ID for tracking']
  
  note: 'Stores processed boundary data information for process API'
}

// Indexes for performance
Note: '''
Recommended indexes:
- CREATE INDEX idx_eg_bm_generated_template_tenant_hierarchy ON eg_bm_generated_template(tenantid, hierarchytype);
- CREATE INDEX idx_eg_bm_generated_template_status ON eg_bm_generated_template(status);
- CREATE INDEX idx_eg_bm_generated_template_referenceid ON eg_bm_generated_template(referenceid);

- CREATE INDEX idx_eg_bm_processed_template_tenant_hierarchy ON eg_bm_processed_template(tenantid, hierarchytype);
- CREATE INDEX idx_eg_bm_processed_template_status ON eg_bm_processed_template(status);
- CREATE INDEX idx_eg_bm_processed_template_referenceid ON eg_bm_processed_template(referenceid);
'''