import config from "../config";
import { AttendanceRegisterId, IndividualId } from "../config/models/brandedTypes";

/**
 * The sheet slugs the identity is keyed by. Declared here rather than per file because the upload
 * path writes them and the de-enrolment consumer rebuilds them: if the two drift, no event matches.
 */
export const attendeeSheetTypes = {
    worker: "worker",
    marker: "marker",
    approver: "approver",
} as const;

export type AttendeeSheetType = (typeof attendeeSheetTypes)[keyof typeof attendeeSheetTypes];

/** The only attendee key both the write path and the de-enrolment consumer can build: events carry UUIDs, the row key does not. */
export function attendeeIdentity(
    registerUuid: AttendanceRegisterId,
    individualId: IndividualId,
    sheetType: AttendeeSheetType
): string {
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
