export function isAttendanceRegisterFamilyType(type: unknown): boolean {
    return typeof type === "string" && type.trim().startsWith("attendanceRegister");
}
