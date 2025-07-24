package org.egov.individual.util;

import java.lang.reflect.Method;

import org.egov.individual.Constants;

public class CommonUtils {

    public static String getHouseholdMemberIdFieldForHouseholdIdMethod(Method idMethod) {
        String columnName = Constants.ID_FIELD;
        if (Constants.GET_HOUSEHOLD_CLIENT_REFERENCE_ID.equals(idMethod.getName())) {
            columnName = Constants.CLIENT_REFERENCE_ID_FIELD;
        }
        return columnName;
    }

    public static String getHouseholdIdFieldForHouseholdIdMethod(Method idMethod) {
        String columnName = Constants.HOUSEHOLD_ID_FIELD;
        if (Constants.GET_HOUSEHOLD_CLIENT_REFERENCE_ID.equals(idMethod.getName())) {
            columnName = Constants.HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD;
        }
        return columnName;
    }
}
