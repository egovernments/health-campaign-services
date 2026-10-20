package org.egov.healthnotification.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;

/**
 * Utility class for creating RequestInfo objects for service-to-service communication.
 * Since this is a consumer service without user context, we use a system user UUID
 * for making calls to external services like MDMS, Household, Individual, etc.
 */
public class RequestInfoUtil {

    /**
     * System user UUID used for service-to-service API calls.
     * This is used when the consumer doesn't have access to a real user's token.
     */
    private static final String SYSTEM_USER_UUID = "health-notification-uuid";

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private RequestInfoUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Creates a RequestInfo object with a system user for service-to-service calls.
     * This should be used when making API calls to external services from Kafka consumers
     * where no user context is available.
     *
     * @return RequestInfo with system user UUID
     */
    public static RequestInfo buildSystemRequestInfo() {
        return RequestInfo.builder()
                .userInfo(User.builder()
                        .uuid(SYSTEM_USER_UUID)
                        .build())
                .build();
    }

    /**
     * Gets the system user UUID.
     *
     * @return The system user UUID string
     */
    public static String getSystemUserUuid() {
        return SYSTEM_USER_UUID;
    }
}
