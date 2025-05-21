import { generateUserPassword } from "../api/campaignApis";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { createIdRequests, createUniqueUserNameViaIdGen, getBoundaryCodeAndBoundaryTypeMapping } from "../utils/campaignUtils";
import { logger } from "../utils/logger";
import config from ".";
// === transformConfigs ===
export const transformConfigs: any = {
    employeeHrms: {
        metadata: {
            tenantId: "dev",
            hierarchy: "MICROPLAN"
        },
        fields: {
            "$user.name": {
                type: "string",
                source: { header: "HCM_ADMIN_CONSOLE_USER_NAME" }
            },
            "$user.mobileNumber": {
                type: "string",
                source: { header: "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER" }
            },
            "$user.roles": {
                type: "array",
                source: {
                    header: "HCM_ADMIN_CONSOLE_USER_ROLE",
                    delimiter: ","
                },
                items: {
                    type: "object",
                    properties: {
                        "$name": { type: "string", valueFrom: "self" },
                        "$code": { type: "string", valueFrom: "self" },
                        "$tenantId": { type: "string", value: "${metadata.tenantId}" }
                    }
                }
            },
            "$user.userName": {
                type: "string",
                source: { header: "UserName" }
            },
            "$user.password": {
                type: "string",
                source: { header: "Password" }
            },
            "$user.tenantId": {
                type: "string",
                value: "${metadata.tenantId}"
            },
            "$user.dob": {
                type: "number",
                value: 0
            },
            "$employeeType": {
                type: "string",
                source: {
                    header: "HCM_ADMIN_CONSOLE_USER_EMPLOYMENT_TYPE",
                    transform: {
                        mapping: {
                            Permanent: "PERMANENT",
                            Temporary: "TEMPORARY",
                            "%default%": "TEMPORARY"
                        }
                    }
                }
            },
            "$jurisdictions": {
                type: "array",
                source: {
                    header: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY",
                    delimiter: ","
                },
                items: {
                    type: "object",
                    properties: {
                        "$boundary": {
                            type: "string",
                            valueFrom: "self"
                        },
                        "$tenantId": {
                            type: "string",
                            value: "${metadata.tenantId}"
                        },
                        "$hierarchy": {
                            type: "string",
                            value: "${metadata.hierarchy}"
                        },
                        "$boundaryType": {
                            type: "string",
                            value: "${metadata.hierarchy}"
                        }
                    }
                }
            },
            "$tenantId": {
                type: "string",
                value: "${metadata.tenantId}"
            },
            "$code": {
                type: "string",
                source: { header: "UserName" }
            }
        },
        transFormSingle: "transformEmployee",
        transFormBulk: "transformBulkEmployee"
    },
    Facility: {
        metadata: {
            tenantId: "dev",
            hierarchy: "MICROPLAN"
        },
        fields: {
            "$Facility.tenantId": {
                type: "string",
                value: "${metadata.tenantId}"
            },
            "$Facility.name": {
                type: "string",
                source: { header: "HCM_ADMIN_CONSOLE_FACILITY_NAME" }
            },
            "$Facility.isPermanent": {
                type: "string",
                source: {
                    header: "HCM_ADMIN_CONSOLE_FACILITY_STATUS",
                    transform: {
                        mapping: {
                            "Permanent": "true",
                            "Temporary": "false",
                            "%default%": "true"
                        }
                    }
                }
            },
            "$Facility.usage": {
                type: "string",
                source: { header: "HCM_ADMIN_CONSOLE_FACILITY_TYPE" }
            },
            "$Facility.storageCapacity": {
                type: "number",
                source: { header: "HCM_ADMIN_CONSOLE_FACILITY_CAPACITY" }
            },
            "$Facility.address.locality.code": {
                type: "string",
                source: { header: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY" }
            }
        },
        transFormSingle: "transformFacility",
        transFormBulk: "transformBulkFacility"
    }
};
type RowData = Record<string, any>;
export class DataTransformer {
    private transformConfig: any;
    private transformFunctionMap: Record<string, (data: any, transformConfig: any) => any>;
    constructor(transformConfig: any) {
        this.transformConfig = transformConfig;
        this.transformFunctionMap = {
            transformEmployee: this.transformEmployee.bind(this),
            transformBulkEmployee: this.transformBulkEmployee.bind(this)
        };
    }
    public async transform(rowData: RowData[]): Promise<any> {
        // Ensure rowData is always an array
        if (!Array.isArray(rowData)) {
            throw new Error("Input data must be an array for transformation.");
        }
        // Proceed with bulk transformation
        return this.transformBulk(rowData);
    }
    private transformBulk(rowData: RowData[]): any {
        if (!Array.isArray(rowData)) {
            throw new Error("Bulk transformation requires an array of data.");
        }
        const transformedData = rowData.map((data) => this.transformSingle(data));
        const transformFnName = this.transformConfig.transFormBulk;
        if (transformFnName && this.transformFunctionMap[transformFnName]) {
            return this.transformFunctionMap[transformFnName](transformedData, this.transformConfig); // ✅ Pass transformConfig
        }
        return transformedData;
    }
    private transformSingle(rowData: RowData): any {
        const transformed: any = {};
        this.processFields(this.transformConfig.fields, rowData, transformed);
        const transformFnName = this.transformConfig.transFormSingle;
        if (transformFnName && this.transformFunctionMap[transformFnName]) {
            return this.transformFunctionMap[transformFnName](transformed, this.transformConfig);  // ✅ Pass transformConfig
        }
        return transformed;
    }
    private resolveTemplate(template: string): string {
        const metadata = this.transformConfig.metadata || {};
        return template.replace(/\$\{metadata\.(\w+)\}/g, (_, key) => metadata[key] ?? "");
    }
    private applyMapping(mapping: Record<string, any>, value: any): any {
        return mapping[value] ?? mapping["%default%"] ?? value;
    }
    private processField(fieldConfig: any, rowData: RowData): any {
        if ("value" in fieldConfig) {
            return this.resolveTemplate(fieldConfig.value.toString());
        }
        if (fieldConfig.source) {
            const rawValue = rowData[fieldConfig.source.header] ?? null;  // Change empty string to null
            if (fieldConfig.type === "array") {
                const parts = rawValue
                    .split(fieldConfig.source.delimiter || ",")
                    .map((v: string) => v.trim());
                return parts.map((item: any) => {
                    if (fieldConfig.items?.type === "object") {
                        const obj: Record<string, any> = {};
                        for (const [key, prop] of Object.entries(fieldConfig.items.properties)) {
                            const finalKey = key.startsWith("$") ? key.substring(1) : key;
                            if ((prop as any).valueFrom === "self") {
                                // same‑element copy
                                obj[finalKey] = item;
                            } else if ("value" in (prop as any)) {
                                // constant / template
                                obj[finalKey] = this.resolveTemplate((prop as any).value);
                            } else if ((prop as any).source) {
                                // NEW  ➜ support nested `source`
                                const v = this.processField(prop, rowData);   // reuse existing logic
                                obj[finalKey] = v;
                            }
                        }
                        return obj;
                    }
                    return item;
                });
            }
            let value = rawValue;
            if (fieldConfig.source.transform?.mapping) {
                value = this.applyMapping(fieldConfig.source.transform.mapping, rawValue);
            }
            return value;
        }
        return null;  // If the field doesn't exist, return null
    }
    private setDeepValue(obj: any, path: string, value: any): void {
        const parts = path.split(".");
        let current = obj;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!current[parts[i]]) {
                current[parts[i]] = {};
            }
            current = current[parts[i]];
        }
        current[parts[parts.length - 1]] = value;
    }
    private processFields(fields: any, rowData: RowData, result: Record<string, any>): void {
        for (const [key, field] of Object.entries(fields)) {
            const value = this.processField(field, rowData);
            // Remove dollar sign from field key before setting it in the transformed data
            const finalKey = key.startsWith("$") ? key.substring(1) : key;
            this.setDeepValue(result, finalKey, value);
        }
    }
    // === Custom Transform Functions ===
    private transformEmployee(data: any, transformConfig: any): any {
        data.status = "ACTIVE";
        // Example use of transformConfig.metadata
        if (transformConfig.metadata?.hierarchy) {
            data.hierarchyUsed = transformConfig.metadata.hierarchy;
        }
        return data;
    }
    private async transformBulkEmployee(data: any[], transformConfig: any): Promise<any> {
        const idRequests = createIdRequests(data);
        let result = await createUniqueUserNameViaIdGen(idRequests);
        let curr = 0;
        logger.info("Enriching boundary type in jurisdictions for employee create data.");
        const boundaryRelationshipResponse = await searchBoundaryRelationshipData(transformConfig?.metadata?.tenantId, transformConfig?.metadata?.hierarchy, true);
        if (!boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary) {
            throw new Error("Boundary relationship search failed");
        }
        const boundaryCodeAndBoundaryTypeMapping = getBoundaryCodeAndBoundaryTypeMapping(boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary);
        if (data?.length > 0) {
            data.forEach(item => {
                if (item?.jurisdictions?.length > 0) {
                    item?.jurisdictions?.forEach((jurisdiction: any) => {
                        jurisdiction.boundaryType = boundaryCodeAndBoundaryTypeMapping[jurisdiction.boundary];
                    })
                }
                if (config.user.userPasswordAutoGenerate) {
                    item.user.password = generateUserPassword();
                }
                else {
                    item.user.password = config.user.userDefaultPassword;
                }
                if(!item?.user?.userName || item?.user?.userName === "undefined" || item?.user?.userName.trim() === "")
                {
                    item.user.userName = result?.idResponses?.[curr]?.id;
                    item.code = result?.idResponses?.[curr]?.id;
                    curr++;
                }
            });
        }
        return data;
    }
}
