import { isAttendanceRegisterFamilyType } from "../utils/processTypeUtils";

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
});
