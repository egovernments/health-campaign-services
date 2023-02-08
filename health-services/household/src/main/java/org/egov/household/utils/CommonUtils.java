package org.egov.household.utils;

import java.lang.reflect.Method;

public class CommonUtils {

    public static String getColumnName(Method idMethod) {
        String columnName = "id";
        if ("getHouseholdClientReferenceId".equals(idMethod.getName())) {
            columnName = "clientReferenceId";
        }
        return columnName;
    }
}
