const controllerProcessTypeAliases: Record<string, string> = {
    "attendanceRegister-validation": "attendanceRegister",
    attendanceRegisterValidation: "attendanceRegister",
    "attendanceRegisterAttendee-validation": "attendanceRegisterAttendee",
    attendanceRegisterAttendeeValidation: "attendanceRegisterAttendee",
    "attendanceRegisterUserBulkMapping-validation": "attendanceRegisterUserBulkMapping",
    attendanceRegisterUserBulkMappingValidation: "attendanceRegisterUserBulkMapping",
};

export function normalizeControllerProcessType(type: string): string {
    const inputType = String(type || "").trim();
    return controllerProcessTypeAliases[inputType] || inputType;
}

export function isAttendanceRegisterFamilyType(type: unknown): boolean {
    return typeof type === "string" && type.trim().startsWith("attendanceRegister");
}
