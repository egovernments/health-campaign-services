package org.egov.product.summaryreport.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tunables for the daily summary report.
 */
@Getter
@Slf4j
@Component
public class SummaryReportConfiguration {

    /** Only plain IANA zone ids are allowed, since the value is inlined into SQL. */
    private static final String TZ_PATTERN = "[A-Za-z0-9_/+\\-]{1,64}";

    /**
     * App-level default timezone used to bucket epoch-millis {@code createdtime} into
     * calendar days when a tenant has no explicit override. Day boundaries are
     * campaign-local, e.g. Africa/Lagos for Nigeria.
     */
    @Value("${summary.report.timezone:Africa/Lagos}")
    private String reportTimezone;

    /**
     * Optional per-tenant timezone overrides, as a comma-separated {@code tenantId:zone}
     * list, e.g. {@code bi:Africa/Bujumbura,cd:Africa/Kinshasa}. A tenant not listed here
     * falls back to {@link #reportTimezone}. Set this only for tenants that are NOT in the
     * app default zone (single-country deployments need none).
     */
    @Value("${summary.report.timezone.byTenant:}")
    private String tenantTimezoneRaw;

    /**
     * PROJECT_TASK status values that count as "treated".
     */
    @Value("${summary.report.treated.statuses:ADMINISTRATION_SUCCESS,VISITED}")
    private String treatedStatusesRaw;

    public List<String> getTreatedStatuses() {
        if (treatedStatusesRaw == null || treatedStatusesRaw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(treatedStatusesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** Parsed tenantId -> timezone map (invalid/blank entries dropped with a warning). */
    public Map<String, String> getTenantTimezones() {
        Map<String, String> map = new HashMap<>();
        if (tenantTimezoneRaw == null || tenantTimezoneRaw.trim().isEmpty()) {
            return map;
        }
        for (String entry : tenantTimezoneRaw.split(",")) {
            String[] kv = entry.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String tenant = kv[0].trim();
            String zone = kv[1].trim();
            if (tenant.isEmpty() || zone.isEmpty()) {
                continue;
            }
            if (!isValidZone(zone)) {
                log.warn("summary.report.timezone.byTenant: ignoring invalid zone '{}' for tenant '{}'", zone, tenant);
                continue;
            }
            map.put(tenant, zone);
        }
        return map;
    }

    /**
     * Resolves the timezone to bucket days for a given tenant: the per-tenant override if
     * present and valid, otherwise the validated app default. Never returns an unsafe value.
     */
    public String getTimezoneForTenant(String tenantId) {
        String tz = getTenantTimezones().get(tenantId);
        if (tz != null) {
            return tz; // already validated in getTenantTimezones()
        }
        String def = reportTimezone;
        if (def == null || !isValidZone(def)) {
            throw new IllegalStateException(
                    "Configured summary.report.timezone is not a valid zone id: " + def);
        }
        return def;
    }

    private boolean isValidZone(String tz) {
        return tz != null && tz.matches(TZ_PATTERN);
    }
}
