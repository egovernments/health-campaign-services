import { searchMDMSDataViaV2Api } from "../api/coreApis";

// Delivery-method code that adds the Outside Household sheet to the unified template (excel-ingestion)
export const OUTSIDE_HOUSEHOLD_DELIVERY_METHOD = "OUTSIDEHOUSEHOLD";

/** Upper-cased with non-alphanumerics removed, as excel-ingestion compares delivery methods; '' when not a usable code. */
export function normalizeDeliveryMethodCode(code: unknown): string {
    return typeof code === "string" ? code.toUpperCase().replace(/[^A-Z0-9]/g, "") : "";
}

const PROJECT_TYPES_SCHEMA_CODE = "HCM-PROJECT-TYPES.projectTypes";

function listsOutsideHousehold(deliveryMethods: unknown): boolean {
    return Array.isArray(deliveryMethods) && deliveryMethods.some((method: unknown) =>
        normalizeDeliveryMethodCode(method && typeof method === "object" ? (method as { code?: unknown }).code : method) === OUTSIDE_HOUSEHOLD_DELIVERY_METHOD);
}

/**
 * Whether the campaign type offers Outside Household: its active HCM-PROJECT-TYPES.projectTypes record lists OUTSIDEHOUSEHOLD in
 * deliveryMethods, the list the console offers delivery strategies from (excel-ingestion reads it the same way). These are the
 * campaign types with delivery conditions. False without a tenant or type; an MDMS call that fails throws.
 */
export async function campaignTypeOffersOutsideHousehold(tenantId: unknown, projectType: unknown): Promise<boolean> {
    if (typeof tenantId !== "string" || !tenantId.trim() || typeof projectType !== "string" || !projectType.trim()) return false;
    const response = await searchMDMSDataViaV2Api({
        MdmsCriteria: { tenantId, schemaCode: PROJECT_TYPES_SCHEMA_CODE, filters: { code: projectType.trim() }, isActive: true }
    });
    return (response?.mdms || []).some(record => record?.isActive !== false && listsOutsideHousehold(record?.data?.deliveryMethods));
}
