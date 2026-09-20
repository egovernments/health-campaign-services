import { isAttendanceRegisterFamilyType, normalizeControllerProcessType } from "../utils/processTypeUtils";

describe("processTypeUtils", () => {
    describe("normalizeControllerProcessType", () => {
        it("maps legacy hyphenated attendance register type to canonical register process type", () => {
            expect(normalizeControllerProcessType("attendanceRegister-validation")).toBe("attendanceRegister");
        });

        it("maps validation-suffixed attendance register type to canonical register process type", () => {
            expect(normalizeControllerProcessType("attendanceRegisterValidation")).toBe("attendanceRegister");
        });

        it("maps attendee validation aliases to attendee process type", () => {
            expect(normalizeControllerProcessType("attendanceRegisterAttendee-validation")).toBe("attendanceRegisterAttendee");
            expect(normalizeControllerProcessType("attendanceRegisterAttendeeValidation")).toBe("attendanceRegisterAttendee");
        });

        it("maps bulk validation aliases to bulk process type", () => {
            expect(normalizeControllerProcessType("attendanceRegisterUserBulkMapping-validation")).toBe("attendanceRegisterUserBulkMapping");
            expect(normalizeControllerProcessType("attendanceRegisterUserBulkMappingValidation")).toBe("attendanceRegisterUserBulkMapping");
        });

        it("preserves already-canonical or unrelated types", () => {
            expect(normalizeControllerProcessType("attendanceRegister")).toBe("attendanceRegister");
            expect(normalizeControllerProcessType("facility")).toBe("facility");
        });
    });

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
});
