import { generateUserPassword } from "../api/campaignApis";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import {
    createIdRequests,
    createUniqueUserNameViaIdGen,
    getBoundaryCodeAndBoundaryTypeMapping
} from "./campaignUtils";
import { logger } from "./logger";
import config from "../config";

type RowData = Record<string, any>;

interface FieldDescriptor {
    path: string;                        // e.g. "$.user.roles[*].code"
    source?: {
        header: string;                    // sheet column name
        transform?: {
            mapping: Record<string, string>;
        };
    };
    splitBy?: string;                    // for arrays: delimiter
    value?: string | number;             // constant or template "${metadata.xxx}"
}

interface TransformConfig {
    metadata?: Record<string, any>;
    fields: FieldDescriptor[];
    transFormSingle?: string;
    transFormBulk?: string;
    reverseTransformSingle?: string;
    reverseTransformBulk?: string;
}

export class DataTransformer {
    private cfg: TransformConfig;
    private hooks: Record<string, Function>;

    constructor(transformConfig: TransformConfig) {
        this.cfg = transformConfig;
        this.hooks = {
            transformEmployee: this.transformEmployee.bind(this),
            transformBulkEmployee: this.transformBulkEmployee.bind(this),
            transformBulkFacility: this.transformBulkFacility.bind(this),
            // reverse hooks if needed
        };
    }

    /** Bulk entrypoint */
    public async transform(rows: RowData[]): Promise<any> {
        if (!Array.isArray(rows)) {
            throw new Error("Input must be an array of row objects");
        }
        const mapped = rows.map(r => this.transformSingle(r));
        if (this.cfg.transFormBulk) {
            return await this.hooks[this.cfg.transFormBulk](mapped, this.cfg);
        }
        return mapped;
    }

    /** Single row → JSON */
    private transformSingle(row: RowData): any {
        const out: any = {};

        // 1) non-array fields
        for (const desc of this.cfg.fields.filter(d => !d.path.includes("[*]"))) {
            const val = this.resolveField(desc, row);
            this.assignByPath(out, desc.path, val);
        }

        // 2) array-of-objects fields
        const arrayDescriptors = this.cfg.fields
            .filter(d => d.path.includes("[*]"))
            .map(d => {
                const [base, prop] = d.path.split("[*].");
                return { base, prop, desc: d };
            });

        const groups: Record<string, typeof arrayDescriptors> = {};
        for (const item of arrayDescriptors) {
            (groups[item.base] ||= []).push(item);
        }

        for (const [base, items] of Object.entries(groups)) {
            const columns: Record<string, string[]> = {};
            let maxLen = 0;

            // split each source column
            for (const { prop, desc } of items) {
                if (desc.source?.header) {
                    const raw = (row[desc.source.header] ?? "").toString();
                    const arr = raw
                        .split(desc.splitBy ?? ",")
                        .map((s: string) => s.trim())
                        .filter((s: string) => s !== "");
                    columns[prop] = arr;
                    maxLen = Math.max(maxLen, arr.length);
                }
            }

            // if no source fields produced anything, still emit one slot
            if (maxLen === 0) maxLen = 1;

            const arrOut: any[] = [];
            for (let i = 0; i < maxLen; i++) {
                const obj: any = {};
                for (const { prop, desc } of items) {
                    if (desc.value !== undefined) {
                        obj[prop] = this.resolveTemplate(desc.value.toString());
                    } else if (desc.source?.header) {
                        obj[prop] = columns[prop][i] ?? null;
                    } else {
                        obj[prop] = null;
                    }
                }
                arrOut.push(obj);
            }

            this.assignByPath(out, `${base}[*]`, arrOut);
        }

        // 3) optional single‐row hook
        if (this.cfg.transFormSingle) {
            return this.hooks[this.cfg.transFormSingle](out, this.cfg);
        }
        return out;
    }

    /** Compute a single field value from rowData */
    private resolveField(desc: FieldDescriptor, row: RowData): any {
        // 1. constant/template
        if (desc.value !== undefined) {
            return this.resolveTemplate(desc.value.toString());
        }

        // 2. from sheet column
        if (desc.source) {
            const cell = row[desc.source.header];
            const raw = (cell === undefined || cell === "") ? null : cell;

            // if no cell, give mapping a crack at default
            if (raw == null) {
                if (desc.source.transform?.mapping) {
                    return this.applyMapping(desc.source.transform.mapping, raw);
                }
                return null;
            }

            // apply mapping (handles non-null via map or default)
            const withMapping = desc.source.transform
                ? this.applyMapping(desc.source.transform.mapping, raw)
                : raw;

            // split arrays
            if (desc.path.includes("[*]")) {
                return (withMapping as string)
                    .split(desc.splitBy ?? ",")
                    .map(s => s.trim())
                    .filter(s => s !== "");
            }

            return withMapping;
        }

        return null;
    }

    /** Template substitution for "${metadata.xxx}" */
    private resolveTemplate(tpl: string): any {
        return tpl.replace(
            /\$\{metadata\.(\w+)\}/g,
            (_, k) => this.cfg.metadata?.[k] ?? ""
        );
    }

    /** Swap a mapping, treating null/undefined as "%default%" */
    private applyMapping(map: Record<string, any>, val: any): any {
        const key = (val === null || val === undefined) ? "%default%" : val;
        return map[key] ?? map["%default%"] ?? val;
    }

    private assignByPath(obj: any, path: string, value: any) {
        const clean = path.replace(/^\$\./, "");
        const parts = clean.split(".");
        let cur = obj;

        for (let i = 0; i < parts.length; i++) {
            const p = parts[i];
            const isArr = p.endsWith("[*]");
            const key = isArr ? p.slice(0, -3) : p;
            const last = i === parts.length - 1;

            if (last) {
                cur[key] = value;
            } else {
                if (!cur[key]) cur[key] = isArr ? [] : {};
                cur = cur[key];
            }
        }
    }

    /** ============== REVERSE ============== */

    /** JSON or JSON[] → sheet rows */
    public async reverseTransform(body: any | any[]): Promise<RowData[]> {
        const list = Array.isArray(body) ? body : [body];
        const rows = list.map(b => this.reverseSingle(b));
        if (this.cfg.reverseTransformBulk) {
            return this.hooks[this.cfg.reverseTransformBulk](rows, this.cfg);
        }
        return rows;
    }

    /** One JSON → one sheet row */
    private reverseSingle(obj: any): RowData {
        const row: RowData = {};

        for (const desc of this.cfg.fields) {
            if (!desc.source?.header) continue;

            const raw = this.getByPath(obj, desc.path);
            let out: any = raw;

            // inverse‐map, including "%default%"
            if (desc.source.transform?.mapping && raw != null) {
                const inv: Record<string, string> = {};
                // only invert the *named* keys, skip "%default%"
                for (const [k, v] of Object.entries(desc.source.transform.mapping)) {
                    if (k === "%default%") continue;
                    inv[v] = k;
                }
                // if raw matches one of the named mappings, return its key;
                // otherwise it must be the default‐mapped string, so just return raw itself
                out = inv.hasOwnProperty(raw) ? inv[raw] : raw;
            }


            // arrays → join
            if (Array.isArray(raw) && desc.path.includes("[*]")) {
                out = raw.join(desc.splitBy ?? ",");
            }

            row[desc.source.header] = out;
        }

        if (this.cfg.reverseTransformSingle) {
            return this.hooks[this.cfg.reverseTransformSingle](row, this.cfg);
        }
        return row;
    }

    /** Simple deep-get that handles “[ * ]” wildcards */
    private getByPath(root: any, path: string): any {
        const clean = path.replace(/^\$\./, "");
        const parts = clean.split(".");
        let current: any[] = [root];

        for (const part of parts) {
            const isArr = part.endsWith("[*]");
            const key = isArr ? part.slice(0, -3) : part;
            const next: any[] = [];

            for (const node of current) {
                if (node == null) continue;
                const val = node[key];
                if (isArr && Array.isArray(val)) {
                    next.push(...val);
                } else if (!isArr && val !== undefined) {
                    next.push(val);
                }
            }

            current = next;
            if (current.length === 0) break;
        }

        // if no wildcard, return single
        if (!path.includes("[*]")) {
            return current[0];
        }
        // else return all matches
        return current;
    }

    // ========== YOUR EXISTING HOOKS ==========

    private transformEmployee(data: any, cfg: TransformConfig): any {
        data.status = "ACTIVE";
        if (cfg.metadata?.hierarchy) {
            data.hierarchyUsed = cfg.metadata.hierarchy;
        }
        return data;
    }

    private async transformBulkEmployee(data: any[], cfg: TransformConfig): Promise<any[]> {
        const idReqs = createIdRequests(data);
        const result = await createUniqueUserNameViaIdGen(idReqs);
        let idx = 0;

        logger.info("Enriching boundary type in jurisdictions…");
        const resp = await searchBoundaryRelationshipData(cfg.metadata!.tenantId, cfg.metadata!.hierarchy, true);
        const map = getBoundaryCodeAndBoundaryTypeMapping(resp.TenantBoundary[0].boundary);

        for (const item of data) {
            // set boundaryType
            if (item.jurisdictions) {
                for (const j of item.jurisdictions) {
                    j.boundaryType = map[j.boundary];
                }
            }
            // password logic
            item.user.password = config.user.userPasswordAutoGenerate
                ? generateUserPassword()
                : config.user.userDefaultPassword;

            // username/id
            const userName = item?.user?.userName ? String(item?.user?.userName)?.trim() : null;
            item.user.userName = userName;
        if (!item.user.userName) {
                const id = result.idResponses[idx++].id;
                item.user.userName = id;
                item.code = id;
            }
        }
        return data;
    }

    private transformBulkFacility(data: any[], cfg: TransformConfig): any[] {
        for (const d of data) {
            const codeList = (d.Facility.address.locality.code || "").split(",");
            d.Facility.address.locality.code = codeList[0].trim();
        }
        return data;
    }
  }
