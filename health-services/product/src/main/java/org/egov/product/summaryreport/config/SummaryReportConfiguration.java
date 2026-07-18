package org.egov.product.summaryreport.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tunables for the daily summary report.
 */
@Getter
@Component
public class SummaryReportConfiguration {

    /**
     * Timezone used to bucket epoch-millis {@code createdtime} into calendar days.
     * Day boundaries are campaign-local, e.g. Africa/Lagos for Nigeria.
     */
    @Value("${summary.report.timezone:Africa/Lagos}")
    private String reportTimezone;

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
}
