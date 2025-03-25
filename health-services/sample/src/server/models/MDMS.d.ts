/**
 * Criteria for MDMS v1 request
 */
interface MDMSv1Criteria {
  tenantId: string; // Unique identifier for the tenant
  moduleDetails: ModuleDetail[]; // Array of module details to fetch
}

/**
 * Represents details of a specific module in MDMS v1
 */
interface ModuleDetail {
  moduleName: string; // Name of the module
  masterDetails: MasterDetail[]; // Array of master data details for this module
}

/**
 * Details of a specific master entity in a module
 */
interface MasterDetail {
  name: string; // Name of the master entity
  filter?: string; // Optional filter criteria for fetching specific master data
}

/**
 * Criteria for MDMS v2 request
 */
interface MDMSv2Criteria {
  tenantId: string; // Unique identifier for the tenant
  schemaCode: string; // Code representing the schema to query
  ids?: string[]; // Optional array of specific IDs to fetch
  uniqueIdentifiers?: string[]; // Optional array of unique identifiers for the data
}

/**
 * Response structure for MDMS v1 response
 */
export interface MDMSv1Response {
  MdmsRes: any; // Response payload, structure may vary
}

/**
 * Response structure for MDMS v2 response
 */
export interface MDMSv2Response {
  mdms: MDMS[]; // Array of MDMS records
}

/**
 * Represents a single MDMS record in v2 response
 */
interface MDMS {
  id: string; // Unique identifier for the MDMS record
  tenantId: string; // Tenant identifier for the record
  schemaCode: string; // Schema code associated with this record
  uniqueIdentifier: string; // Unique identifier within this schema
  data: any; // Data payload for the record
  isActive: boolean; // Indicates if the record is active
  auditDetails: AuditDetails; // Audit details for tracking changes
}

/**
 * Audit details containing metadata about record creation and modification
 */
interface AuditDetails {
  createdBy: string; // ID of the user who created the record
  lastModifiedBy: string; // ID of the user who last modified the record
  createdTime: number; // Timestamp when the record was created
  lastModifiedTime: number; // Timestamp when the record was last modified
}

/**
 * Response structure for MDMS schema definitions
 */
export interface MDMSSchemaResponse {
  SchemaDefinitions: SchemaDefinition[]; // Array of schema definitions
}

/**
 * Represents a single schema definition in the MDMS response
 */
interface SchemaDefinition {
  id: string; // Unique identifier for the schema definition
  tenantId: string; // Tenant identifier associated with this schema
  code: string; // Code identifying the schema
  description: null | string; // Description of the schema, if available
  definition: Definition; // Schema structure definition
  isActive: boolean; // Indicates if the schema is active
  auditDetails: AuditDetails; // Audit metadata for the schema definition
}

/**
 * Detailed structure of a schema definition
 */
interface Definition {
  type: string; // Type of schema (e.g., object, array)
  title?: string; // Optional title of the schema
  $schema: string; // URI of the schema
  required: string[]; // Array of required fields
  "x-unique": string[]; // Array of unique constraints
  properties?: any; // Properties within the schema
  "x-ref-schema"?: XRefSchema[]; // Array of cross-references to other schemas
  description?: string; // Optional description of the schema
  additionalProperties?: boolean; // Indicates if additional properties are allowed
  unique?: string[]; // Array of fields that must be unique
}

/**
 * Cross-reference schema for related fields
 */
interface XRefSchema {
  fieldPath: string; // Path to the field within the schema
  schemaCode: string; // Code of the schema that is referenced
}

/**
 * Criteria for requesting MDMS schema definitions
 */
export interface MDMSSchemaRequestCriteria {
  SchemaDefCriteria: SchemaDefCriteria; // Criteria details for schema definition request
}

/**
 * Detailed criteria structure for schema definition requests
 */
interface SchemaDefCriteria {
  tenantId: string; // Tenant identifier for the schema definition
  limit: number; // Limit on the number of schema definitions to fetch
  codes?: string[]; // Optional array of schema codes to retrieve
}

/**
 * Criteria for requesting MDMS schema definitions
 */
export interface MDMSv1RequestCriteria {
  MdmsCriteria: MDMSv1Criteria; // Criteria details for schema definition request
}

/**
 * Criteria for requesting MDMS schema definitions
 */
export interface MDMSv2RequestCriteria {
  MdmsCriteria: MDMSv2Criteria; // Criteria details for schema definition request
}
