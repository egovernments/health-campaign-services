package org.egov.codegen;

import java.util.Map;

public class Utils {
    private Utils() {}

    public static String getMainClassName(String artifactId) {
        try {
            String firstCharacter = String.valueOf(artifactId.charAt(0))
                    .toUpperCase();
            String remainingChars = artifactId.substring(1);
            return String.format("%s%s%s", firstCharacter, remainingChars, "Application");
        } catch (Exception e) {
            return "Main";
        }
    }

    public static void addImportMappingsForCommonObjects(Map<String, String> importMapping) {
        importMapping.put("RequestInfo", "org.egov.common.contract.request.RequestInfo");
        importMapping.put("ResponseInfo", "org.egov.common.contract.response.ResponseInfo");
        importMapping.put("Role", "org.egov.common.contract.request.Role");
    }
}
