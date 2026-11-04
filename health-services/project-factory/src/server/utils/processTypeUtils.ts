export function isAttendanceRegisterFamilyType(type: unknown): boolean {
    return typeof type === "string" && type.trim().startsWith("attendanceRegister");
}

/**
 * Normalizes incoming process/resource types to known internal keys.
 * Example: `attendanceRegister-validation` -> `attendanceRegisterValidation`.
 */
export function normalizeProcessType(type: string, knownTypes: string[]): string {
    if (typeof type !== "string") return type as unknown as string;
    const trimmed = type.trim();
    if (!trimmed) return trimmed;
    if (knownTypes.includes(trimmed)) return trimmed;

    const parts = trimmed.split(/[-_\s]+/).filter(Boolean);
    if (!parts.length) return trimmed;

    const normalized = parts
        .map((part, index) => (index === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1)))
        .join("");

    if (knownTypes.includes(normalized)) return normalized;
    return trimmed;
}
