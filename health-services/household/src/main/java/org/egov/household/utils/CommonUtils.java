package org.egov.household.utils;

import java.lang.reflect.Method;

import static org.egov.household.Constants.*;

public class CommonUtils {

    public static String getColumnName(Method idMethod) {
        String columnName = ID_FIELD;
        if (GET_HOUSEHOLD_CLIENT_REFERENCE_ID.equals(idMethod.getName())) {
            columnName = CLIENT_REFERENCE_ID_FIELD;
        }
        return columnName;
    }

    public static String getHouseholdColumnName(Method idMethod) {
        String columnName = HOUSEHOLD_ID_FIELD;
        if (GET_HOUSEHOLD_CLIENT_REFERENCE_ID.equals(idMethod.getName())) {
            columnName = HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD;
        }
        return columnName;
    }
}
