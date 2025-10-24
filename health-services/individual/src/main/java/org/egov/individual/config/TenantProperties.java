package org.egov.individual.config;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tenant.state.level.tenant.id")
public class TenantProperties {

    private Map<String, String> mappings;

    public String getStateLevelTenant(String tenantId, String defaultTenantId) {
        if (tenantId == null) {
            return defaultTenantId;
        }
        return mappings.getOrDefault(tenantId.toLowerCase(Locale.ROOT), defaultTenantId);
    }
}