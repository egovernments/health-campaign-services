package org.egov.processor.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    public static final String IDGEN_ERROR = "IDGEN ERROR";
    public static final String NO_IDS_FOUND_ERROR = "No ids returned from idgen Service";

    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String TENANTID_REPLACER = "{tenantId}";
    public static final String FILESTORE_ID_REPLACER = "{fileStoreId}";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE = "Invalid or incorrect TenantId. No mdms data found for provided Tenant.";

    public static final String ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE = "Exception occurred while fetching plan configuration from plan service ";

    public static final String MICROPLANNING_MODULE = "microplan";

    public static final String FILES = "files";
    public static final String FILESTORE_ID = "fileStoreId";


}
