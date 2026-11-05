// Delivery-method code that adds the Outside Household sheet to the unified template (excel-ingestion)
export const OUTSIDE_HOUSEHOLD_DELIVERY_METHOD = "OUTSIDEHOUSEHOLD";

/** Upper-cased with non-alphanumerics removed, as excel-ingestion compares delivery methods; '' when not a usable code. */
export function normalizeDeliveryMethodCode(code: unknown): string {
    return typeof code === "string" ? code.toUpperCase().replace(/[^A-Z0-9]/g, "") : "";
}
