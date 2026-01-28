package org.egov.excelingestion.util;

import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.UserInfo;
import org.springframework.stereotype.Component;

@Component
public class RequestInfoConverter {

    private final ExcelIngestionConfig config;

    public RequestInfoConverter(ExcelIngestionConfig config) {
        this.config = config;
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