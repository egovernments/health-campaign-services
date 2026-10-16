/** Normalises a generated resource's locality into the key that scopes reuse and expiry; locality-less types resolve to null so their behaviour is unchanged. */
export const localityKeyOf = (resource: any): string | null => {
    const raw = (resource?.additionalDetails as Record<string, unknown> | undefined)?.localityCode;
    const code = typeof raw === "string" ? raw.trim() : "";
    return code ? code : null;
};
