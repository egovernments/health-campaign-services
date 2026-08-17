import config from "../config";

/**
 * Identity stamped on an attendee campaign_data row, shared by the write path and the de-enrolment
 * consumer. De-enrolment events carry only UUIDs while the row's own key is
 * serviceCode_username_sheetType, so this is the only value both sides can build.
 *
 * Kept free of DB/Kafka imports on purpose: the process class imports it, and pulling in the DB pool
 * at module load would break every test that does not mock the database.
 */
export function attendeeIdentity(registerUuid: string, individualId: string, sheetType: string): string {
    return `${registerUuid}_${individualId}_${sheetType}`;
}

/**
 * dd-MM-yyyy, the format the template documents and the upload validator accepts. Resolved in the
 * configured app timezone, not the pod's: the same epoch has to read as the same calendar day here
 * as it does in the upload-side date checks.
 */
export function formatEpochAsSheetDate(epochMs: number): string {
    const parts = new Intl.DateTimeFormat("en-GB", {
        timeZone: config.appTimezone,
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).formatToParts(new Date(epochMs));
    const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
    return `${get("day")}-${get("month")}-${get("year")}`;
}
