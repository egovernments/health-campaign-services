import { isAttendanceRegisterFamilyType, normalizeProcessType } from "../utils/processTypeUtils";

describe("processTypeUtils", () => {
    describe("isAttendanceRegisterFamilyType", () => {
        it("identifies attendance register family types", () => {
            expect(isAttendanceRegisterFamilyType("attendanceRegister")).toBe(true);
            expect(isAttendanceRegisterFamilyType("attendanceRegisterValidation")).toBe(true);
            expect(isAttendanceRegisterFamilyType("attendanceRegisterUserBulkMapping")).toBe(true);
        });

        it("returns false for non-attendance types", () => {
            expect(isAttendanceRegisterFamilyType("facility")).toBe(false);
            expect(isAttendanceRegisterFamilyType(undefined)).toBe(false);
            expect(isAttendanceRegisterFamilyType(null)).toBe(false);
        });
    });

    describe("normalizeProcessType", () => {
        const knownTypes = [
            "attendanceRegisterValidation",
            "attendanceRegisterAttendeeValidation",
            "attendanceRegisterUserBulkMappingValidation",
        ];

        it("normalizes hyphenated attendance validation aliases", () => {
            expect(normalizeProcessType("attendanceRegister-validation", knownTypes)).toBe("attendanceRegisterValidation");
            expect(normalizeProcessType("attendance-register-validation", knownTypes)).toBe("attendanceRegisterValidation");
            expect(normalizeProcessType("attendanceRegister-user-bulk-mapping-validation", knownTypes)).toBe("attendanceRegisterUserBulkMappingValidation");
        });

        it("returns input unchanged when it is unknown", () => {
            expect(normalizeProcessType("unknown-type", knownTypes)).toBe("unknown-type");
        });
    });
});
