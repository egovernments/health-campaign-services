import { outsideHouseholdFacilityFields, outsideHouseholdLinkAdditionalFieldsSchema } from '../config/constants';

/**
 * additionalFields of a site's project-facility link, built from the site's stored campaign data row: the schema
 * is always set; the fields carry the frontline worker, if given (this campaign only, never on the site).
 */
export function outsideHouseholdLinkAdditionalFields(data: Record<string, any> = {}): Record<string, any> {
    const fields = [
        { key: outsideHouseholdLinkAdditionalFieldsSchema.workerNameKey, value: data[outsideHouseholdFacilityFields.workerName] },
        { key: outsideHouseholdLinkAdditionalFieldsSchema.workerPhoneKey, value: data[outsideHouseholdFacilityFields.workerPhone] },
    ]
        .map(f => ({ key: f.key, value: String(f.value ?? '').trim() }))
        .filter(f => f.value);
    return {
        schema: outsideHouseholdLinkAdditionalFieldsSchema.schema,
        version: outsideHouseholdLinkAdditionalFieldsSchema.version,
        fields
    };
}

/** True when a link already carries exactly these worker fields (so no update is needed). */
export function sameOutsideHouseholdLinkFields(existing: any, wanted: Record<string, any>): boolean {
    const asMap = (af: any) => JSON.stringify(((af?.fields || []) as any[])
        .map(f => [f?.key, String(f?.value ?? '').trim()])
        .filter(([, v]) => v)
        .sort());
    return existing?.schema === wanted.schema && asMap(existing) === asMap(wanted);
}
