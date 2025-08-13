package org.egov.excelingestion.util;

import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RequestInfoConverter {

    private final ExcelIngestionConfig config;

    @Autowired
    public RequestInfoConverter(ExcelIngestionConfig config) {
        this.config = config;
    }

    /**
     * Converts a common RequestInfo object to Excel Ingestion specific RequestInfo.
     * This method centralizes the conversion logic that was duplicated across multiple processors.
     *
     * @param commonRequestInfo The common RequestInfo object
     * @return Excel Ingestion specific RequestInfo
     */
    public RequestInfo convertToExcelIngestionRequestInfo(RequestInfo commonRequestInfo) {
        if (commonRequestInfo == null) {
            return null;
        }

        UserInfo userInfo = null;
        if (commonRequestInfo.getUserInfo() != null) {
            userInfo = UserInfo.builder()
                    .id(commonRequestInfo.getUserInfo().getId())
                    .uuid(commonRequestInfo.getUserInfo().getUuid())
                    .userName(commonRequestInfo.getUserInfo().getUserName())
                    .name(commonRequestInfo.getUserInfo().getName())
                    .mobileNumber(commonRequestInfo.getUserInfo().getMobileNumber())
                    .emailId(commonRequestInfo.getUserInfo().getEmailId())
                    .locale(commonRequestInfo.getUserInfo().getLocale())
                    .type(commonRequestInfo.getUserInfo().getType())
                    .tenantId(commonRequestInfo.getUserInfo().getTenantId())
                    .build();
        }

        return RequestInfo.builder()
                .apiId(commonRequestInfo.getApiId())
                .ver(commonRequestInfo.getVer())
                .ts(commonRequestInfo.getTs())
                .action(commonRequestInfo.getAction())
                .did(commonRequestInfo.getDid())
                .key(commonRequestInfo.getKey())
                .msgId(commonRequestInfo.getMsgId())
                .requesterId(commonRequestInfo.getRequesterId())
                .authToken(commonRequestInfo.getAuthToken())
                .userInfo(userInfo)
                .correlationId(commonRequestInfo.getCorrelationId())
                .build();
    }

    /**
     * Extracts locale from RequestInfo msgId field.
     * Default locale is taken from configuration if not found.
     *
     * @param requestInfo The RequestInfo object
     * @return The locale string
     */
    public String extractLocale(RequestInfo requestInfo) {
        if (requestInfo != null && requestInfo.getMsgId() != null) {
            String[] parts = requestInfo.getMsgId().split("\\|");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return config.getDefaultLocale();
    }
}