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
    public static final String HIERARCHY_TYPE = "MICROPLAN_NEW1";
    public static final String ERROR_FETCHING_FROM_MDMS="Error Fetching Data from mdms";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ISO_CODE="For given tenantId and ISO code no country exists";
    public static final String COUNTRY_OUTFIELDS="ADM1_NAME";
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
