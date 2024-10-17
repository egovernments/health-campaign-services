package org.egov.product.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {
    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ID_OR_ID";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS_MESSAGE = "Invalid or incorrect tenant id or id. No mdms data found for provided tenant id and id.";

    public static final String JSONPATH_ERROR_CODE = "JSONPATH_ERROR";
    public static final String JSONPATH_ERROR_MESSAGE = "Failed to parse mdms response with given Jsonpath" ;

    public static final String MDMS_PRODUCT_MODULE_NAME = "HCM-Product";
    public static final String MDMS_PRODUCT_MASTER_NAME = "Products";

    public static final String MDMS_PRODUCT_VARIANT_MODULE_NAME = "HCM-Product";
    public static final String MDMS_PRODUCT_VARIANT_MASTER_NAME = "ProductVariants";

    public static final String JSON_ROOT_PATH = "$.";

    public static final String DOT_SEPARATOR = ".";

    public static final String STAR_OPERATOR = "*";

    public static final String FILTER_CODE = "$.*.code";

    public static final String FILTER_ID = "$.*.id";

    public static final String FILTER_DATA = "$.*.data";
}
