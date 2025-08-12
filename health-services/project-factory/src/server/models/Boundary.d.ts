import { AuditDetails } from "./MDMS";

/**
 * Represents the response structure for a boundary entity search.
 */
export interface BoundaryEntityResponse {
    /** Array of boundary entities returned in the response */
    Boundary: BoundaryEntity[];
}

/**
 * Represents a boundary entity within the MDMS system.
 */
interface BoundaryEntity {
    /** Unique identifier for the boundary entity */
    id: string;
    /** Tenant ID associated with the boundary entity */
    tenantId: string;
    /** Unique code representing the boundary entity */
    code: string;
    /** Geographical data for the boundary entity */
    geometry: any;
    /** Audit information such as created/modified timestamps and users */
    auditDetails: AuditDetails;
    /** Additional details related to the boundary entity */
    additionalDetails: any;
}

/**
 * Represents the response structure for a boundary hierarchy definition search.
 */
export interface BoundaryHierarchyDefinitionResponse {
    /** Array of boundary hierarchy definitions in the response */
    BoundaryHierarchy: BoundaryHierarchy[];
}

/**
 * Defines a boundary hierarchy within the MDMS system.
 */
interface BoundaryHierarchy {
    /** Unique identifier for the hierarchy */
    id: string;
    /** Tenant ID associated with the hierarchy */
    tenantId: string;
    /** Type of hierarchy (e.g., administrative, geographic) */
    hierarchyType: string;
    /** Elements representing each level within the hierarchy */
    boundaryHierarchy: BoundaryHierarchyElement[];
    /** Audit information such as created/modified timestamps and users */
    auditDetails: AuditDetails;
}

/**
 * Represents an element in the boundary hierarchy.
 */
interface BoundaryHierarchyElement {
    /** Type of boundary at this level of the hierarchy */
    boundaryType: string;
    /** Type of the parent boundary, if applicable */
    parentBoundaryType: null | string;
    /** Indicates if the boundary is active */
    active: boolean;
}

/**
 * Represents the response structure for a boundary hierarchy relationship search.
 */
export interface BoundaryHierarchyRelationshipResponse {
    /** Array of tenant boundaries returned in the response */
    TenantBoundary: TenantBoundary[];
}

/**
 * Represents a tenant boundary structure within the MDMS system.
 */
interface TenantBoundary {
    /** Tenant ID associated with this boundary */
    tenantId: string;
    /** Type of hierarchy within which the boundary is categorized */
    hierarchyType: string;
    /** Array of boundaries within this tenant's hierarchy */
    boundary: Boundary[];
}

/**
 * Represents an individual boundary within a hierarchy, which may contain nested child boundaries.
 */
interface Boundary {
    /** Unique identifier for the boundary */
    id: string;
    /** Code representing the boundary */
    code: string;
    /** Type of boundary (e.g., city, region) */
    boundaryType: string;
    /** Array of child boundaries, allowing nested hierarchies */
    children: Boundary[];
}

/**
 * Represents the search criteria for querying boundary hierarchy definitions.
 */
export interface BoundaryHierarchyDefinitionSearchCriteria {
    /** Contains criteria for boundary type hierarchy search */
    BoundaryTypeHierarchySearchCriteria: BoundaryTypeHierarchySearchCriteria;
}

/**
 * Defines the criteria for searching a specific boundary type hierarchy.
 */
interface BoundaryTypeHierarchySearchCriteria {
    /** Tenant ID for filtering the search results */
    tenantId: string;
    /** Type of hierarchy being queried (e.g., administrative levels) */
    hierarchyType: string;
}
