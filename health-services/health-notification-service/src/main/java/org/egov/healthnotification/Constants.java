package org.egov.healthnotification;

public class Constants {

    private Constants() {
        throw new IllegalStateException("Constants class");
    }

    // Validation error constants
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INVALID_TENANT_ID = "INVALID_TENANT_ID";
    public static final String INVALID_TENANT_ID_MSG = "Tenant ID is not valid";

    // Task filtering constants
    public static final String DELIVERY_STRATEGY_KEY = "deliveryStrategy";
    public static final String DELIVERY_STRATEGY_DIRECT = "DIRECT";
}
