/**
 * Resolve the axios request timeout (ms) from an env value.
 * Uses parseInt (not Number) so an unset / blank / non-numeric value falls back to the
 * default instead of silently becoming 0 (timeout disabled) or NaN (indefinite hang).
 * An explicit "0" parses to 0 and disables the timeout as documented (used for large file downloads).
 */
export function resolveHttpTimeout(raw: string | undefined, defaultMs: number = 300_000): number {
  const parsed = parseInt(raw ?? "", 10);
  return Number.isNaN(parsed) ? defaultMs : parsed;
}
