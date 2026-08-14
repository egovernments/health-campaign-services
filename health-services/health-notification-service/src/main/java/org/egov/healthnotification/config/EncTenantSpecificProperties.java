package org.egov.healthnotification.config;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "egov.enc.tenant")
public class EncTenantSpecificProperties {

    private boolean useDefaultValues;

    private Map<String, String> host;

    private Map<String, String> encryptEndpoint;

    private Map<String, String> decryptEndpoint;

    public String getHost(String tenantId, String defaultValue) {
        return getValue(host, tenantId, defaultValue);
    }

    public String getEncryptEndpoint(String tenantId, String defaultValue) {
        return getValue(encryptEndpoint, tenantId, defaultValue);
    }

    public String getDecryptEndpoint(String tenantId, String defaultValue) {
        return getValue(decryptEndpoint, tenantId, defaultValue);
    }

    private String getValue(Map<String, String> mappings, String tenantId, String defaultValue) {
        if (mappings == null || tenantId == null) {
            return defaultValue;
        }
        if (useDefaultValues) {
            return mappings.getOrDefault(tenantId, defaultValue);
        }
        return mappings.get(tenantId);
    }
}
