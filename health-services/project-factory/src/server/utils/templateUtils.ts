import { createTemplateService, searchTemplateService } from "../service/templateManageService";
import { defaultRequestInfo, searchMDMSDataViaV2Api } from "../api/coreApis";
import config from "../config/index";
import { executeQuery } from "./db";
import { TemplateTransformer } from "./transforms/searchResponseConstructor";
import { logger } from "./logger";
import { v4 as uuidv4 } from 'uuid';

const searchTemplateUtil = async (templateSearchCriteria: any) => {
    const query = buildWhereClauseForTemplateSearch(templateSearchCriteria);
    const queryResult = await executeQuery(query.query, query.values);
    return TemplateTransformer(queryResult?.rows);
}

function buildWhereClauseForTemplateSearch(SearchCriteria: any): {
    query: string;
    values: any[];
} {
    const { id, locale, type, tenantId, isActive } = SearchCriteria;
    let conditions = [];
    let values = [];

    // Check for id (if provided, allowing search by ID dynamically)
    if (id) {
        conditions.push(`id = $${values.length + 1}`);
        values.push(id);
    }

    // Check for tenantId
    if (tenantId) {
        conditions.push(`tenantId = $${values.length + 1}`);
        values.push(tenantId);
    }

    // Check for locale
    if (locale) {
        conditions.push(`locale = $${values.length + 1}`);
        values.push(locale);
    }

    // Check for type
    if (type) {
        conditions.push(`type = $${values.length + 1}`);
        values.push(type);
    }

    // Check for isActive
    if (isActive !== undefined) {
        conditions.push(`is_active = $${values.length + 1}`);
        values.push(isActive);
    }

    // Build the WHERE clause
    const whereClause =
        conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";

    // Return the query and values array
    return {
        query: `
              SELECT *
              FROM  ${config?.DB_CONFIG.DB_TEMPLATE_TABLE_NAME}
              ${whereClause};`,
        values,
    };
}

function enrichCreateTemplateRequest(requestBody: any) {
    requestBody.template.auditDetails = {
        createdBy: requestBody?.requestInfo?.userInfo?.uuid || null,
        lastModifiedBy: requestBody?.requestInfo?.userInfo?.uuid || null,
        createdTime: Date.now(),
        lastModifiedTime: Date.now(),
    };
    requestBody.template.id = uuidv4();
}

// Fetch project types from MDMS
export async function fetchProjectTypesFromMDMS() {
    const MdmsCriteria: any = {
        tenantId: config?.app?.defaultTenantId,
        schemaCode: `${config.moduleNameForProjectTypes}.${config.masterNameForProjectTypes}`,
    };
    const mdmsResponse = await searchMDMSDataViaV2Api(MdmsCriteria);
    return mdmsResponse?.mdms?.map((item: any) => item?.data)?.flat() || [];
}

// Fetch locales from MDMS
export async function fetchLocalesFromMDMS() {
    const MdmsCriteriaForLocale: any = {
        tenantId: config?.app?.defaultTenantId,
        schemaCode: `${config.commonMastersModule}.${config.stateInfoMasters}`,
    };
    const mdmsResponseOfLocale = await searchMDMSDataViaV2Api(MdmsCriteriaForLocale);
    return mdmsResponseOfLocale?.mdms?.[0]?.data?.languages?.map((e: any) => e.value) || [];
}

// Handle template creation
export async function handleTemplateCreation(locale: string, type: string) {
    try {
        const templateSearchCriteria = {
            locale,
            type,
            tenantId: config?.app?.defaultTenantId,
        };
        const data = {
            templateSearchCriteria,
            ...defaultRequestInfo,
        };
        const searchResponse = await searchTemplateService(data);

        if (!searchResponse?.data?.length) {
            logger.info(`Locale: ${locale} ,Type: ${type} not found. Creating...`);
            // const requestInfo = defaultRequestInfo;
            const requestBody = {
                ...defaultRequestInfo,
                template: {
                    id: null,
                    tenantId: config?.app?.defaultTenantId,
                    locale: locale,
                    type: type,
                    isActive: true,
                    fileStoreId: null,
                },
            };
            await createTemplateService(requestBody);
        } else {
            logger.info(`Locale: ${locale}, Type: ${type} already exists.`);
        }
    } catch (error: any) {
        logger.error(`Error processing Locale: ${locale}, Type: ${type}. Error: ${error.message}`);
    }
}


export { searchTemplateUtil, enrichCreateTemplateRequest };
