/**
 * Identity stamped on an attendee campaign_data row, shared by the write path and the de-enrolment
 * consumer. De-enrolment events carry only UUIDs while the row's own key is
 * serviceCode_username_sheetType, so this is the only value both sides can build.
 *
 * Kept dependency-free on purpose: the process class imports it, and pulling in the DB pool at
 * module load would break every test that does not mock the database.
 */
export function attendeeIdentity(registerUuid: string, individualId: string, sheetType: string): string {
    return `${registerUuid}_${individualId}_${sheetType}`;
}
