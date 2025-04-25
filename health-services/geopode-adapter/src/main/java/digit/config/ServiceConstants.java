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

    public static final String HIERARCHY_TYPE = "admin1";
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
