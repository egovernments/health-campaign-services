package digit.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    // Error Constants
    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String ERROR_CREATING_BOUNDARY_HIERARCHY_WITH_GIVEN_HIERARCHY = "Error encountered while creating boundary hierarchy with given hierarchy ";

    // Common constants
    public static final String BOUNDARY_CREATION_RESPONSE = "GeoPoDe Boundary Creation started successfully!";
    public static final String LOG_PLACEHOLDER = "{}";

    public static final String[] HIERARCHY_ORDER = { "ADM0_NAME", "ADM1_NAME", "ADM2_NAME", "ADM3_NAME" };
    public static final String ERROR_IN_SEARCH="Error when fetching from Boundary-definition";
    public static final String ERROR_IN_ARC_SEARCH="Error when fetching from Arcgis";
    public static final String ERROR_FETCHING_FROM_MDMS="Error Fetching Data from mdms";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ISO_CODE="For given tenantId and ISO code no country exists";
    public static final String FORMAT_VALUE="json";
    public static final String ERROR_FETCHING_FROM_BOUNDARY="Error Fetching Data from boundary";
    public static final String ROOT_BOUNDARY_ALREADY_EXISTS="Root Boundary already created";
    public static final String MDMS_ISO_CODE ="isoCode";
    public static final String MDMS_NAME="name";
    public static final String  FAILED_TO_CREATE_CHILDREN="Failed to create children";
    public static final String FAILED_TO_DESERIALIZE="Failed to deseralize hierarchy defintion";
    public static final String BOUNDARY_CREATION_INITIATED="The process of creating boundaries has been initiated for ";
    public static final String ROOT_HIERARCHY_LEVEL="ADM0";
    public static final String ALREADY_EXISTS="Duplicate Record";
    public static final String COUNTRY_NAME_NOT_FOUND="Country Not Found";
    public static final String ERROR_IN_ARC_SEARCH_CODE="Fetching error";
    // ArcGIS query parameter keys
    public static final String QUERY_PARAM_WHERE = "where";
    public static final String QUERY_PARAM_OUT_FIELDS = "outFields";
    public static final String QUERY_PARAM_FORMAT = "f";
    public static final String QUERY_PARAM_RESULT_COUNT = "resultRecordCount";

    public static final String QUERY_PARAM_TENANT_ID="tenantId";
    public static final String QUERY_PARAM_CODES="codes";
    public static final String UNDERSCORE = "_";
    public static final String NON_ALPHANUMERIC_REGEX = "[^a-zA-Z0-9]";
    public static final String ERROR_COUNTRY_ALREADY_EXISTS = "Country already exists for given HierarchyType";
    // ArcGIS fixed values
    public static final int RECORD_COUNT_ONE = 1;
    public static final String INVALID_NUMBER_OF_OPERANDS_CODE = "INVALID_NUMBER_OF_OPERANDS";
    public static final String DIVISION_ONLY_TWO_OPERANDS_MSG = "Division operation can be performed only with 2 operands.";
    public static final String ADM0 = "ADM0";
    public static final String NAME_SUFFIX = "_NAME";
    public static final String EQ_WITH_QUOTES = "='";
    public static final String CLOSING_QUOTE = "'";

    // Field name constants
    public static final String COUNTRY_OUTFIELDS="ADM0_NAME";

    public static final String ADM1 = "ADM1";
    public static final String ADM2 = "ADM2";
    public static final String ADM3 = "ADM3";
    public static final String ADM4 = "ADM4";

    public static final String ERROR_IN_BOUNDARY_RELATIONSHIP_CREATE = "Error creating boundary relationship";
    public static final String BOUNDARY_RELATIONSHIP_CREATE_FAILED = "Boundary relationship creation failed";
    public static final String BOUNDARY_RELATIONSHIP_CREATE_FAILED_MSG = "Failed to create boundary relationship for child: %s with parent: %s";
    public static final String ERROR_IN_BOUNDARY_CREATE = "Error creating boundary entity";
    public static final String BOUNDARY_CREATE_FAILED = "BOUNDARY_CREATE_FAILED";
    public static final String BOUNDARY_CREATE_FAILED_MSG = "Failed to create boundary entity for child: %s at level: %s";
    public static final String ROOT_BOUNDARY_NOT_FOUND = "ROOT_BOUNDARY_NOT_FOUND";
    public static final String ROOT_BOUNDARY_NOT_FOUND_MSG = "No root boundary (with null parent) found in hierarchy";
    public static final String  MDMS_NOT_FOUND = "Data not found";




    public static final String RESPONSE_FROM_GEOPODE_API = "[\n" +
            "  {\n" +
            "    \"feature\": \"admin_0\",\n" +
            "    \"type_code\": \"Country\",\n" +
            "    \"level\": 0,\n" +
            "    \"parent\": null\n" +
            "  },\n" +
            "  {\n" +
            "    \"feature\": \"admin_1\",\n" +
            "    \"type_code\": \"State\",\n" +
            "    \"level\": 1,\n" +
            "    \"parent\": \"Country\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"feature\": \"admin_2\",\n" +
            "    \"type_code\": \"LGA\",\n" +
            "    \"level\": 2,\n" +
            "    \"parent\": \"State\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"feature\": \"admin_3\",\n" +
            "    \"type_code\": \"Ward\",\n" +
            "    \"level\": 3,\n" +
            "    \"parent\": \"LGA\"\n" +
            "  }\n" +
            "]";


}
