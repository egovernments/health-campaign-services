package org.egov.codegen;

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
}
