// === Transform Config ===
export const transformConfigs: any = {
    employeeHrms: {
        metadata: {
            tenantId: "mz",
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
    }
};

type RowData = Record<string, any>;

export class DataTransformer {
    private config: any;
    private transformFunctionMap: Record<string, (data: any) => any>;

    constructor(config: any) {
        this.config = config;
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

        const transformedData = rowData.map((data) => this.transformSingle(data)); // Apply single transformation to each item
        const transformFnName = this.config.transFormBulk;

        if (transformFnName && this.transformFunctionMap[transformFnName]) {
            return this.transformFunctionMap[transformFnName](transformedData);
        }

        return transformedData;
    }

    private transformSingle(rowData: RowData): any {
        const transformed: any = {};
        this.processFields(this.config.fields, rowData, transformed);
        const transformFnName = this.config.transFormSingle;

        if (transformFnName && this.transformFunctionMap[transformFnName]) {
            return this.transformFunctionMap[transformFnName](transformed);
        }

        return transformed;
    }

    private resolveTemplate(template: string): string {
        const metadata = this.config.metadata || {};
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

                return parts.map((item: string) => {
                    if (fieldConfig.items?.type === "object") {
                        const obj: Record<string, any> = {};
                        for (const [key, prop] of Object.entries(fieldConfig.items.properties)) {
                            // Remove dollar sign from field keys in the array items
                            const finalKey = key.startsWith("$") ? key.substring(1) : key;

                            if ((prop as any).valueFrom === "self") {
                                obj[finalKey] = item;
                            } else if ("value" in (prop as any)) {
                                obj[finalKey] = this.resolveTemplate((prop as any).value);
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
    private transformEmployee(data: any): any {
        data.status = "ACTIVE";
        return data;
    }

    private transformBulkEmployee(data: any[]): any {
        // Bulk transformation logic here (if needed)
        data.forEach(item => {
            item.status2 = "ACTIVE";
        });
        return data;
    }
}

