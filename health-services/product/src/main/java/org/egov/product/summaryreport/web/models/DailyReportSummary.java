package org.egov.product.summaryreport.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * One day's activity summary for a single employee (createdBy).
 * The API returns an array of these, one entry per day in the requested range that
 * has at least one record.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyReportSummary {

    /** Calendar day in campaign timezone, formatted yyyy-MM-dd. */
    @JsonProperty("date")
    private String date;

    /** Employee uuid (RequestInfo.userInfo.uuid). */
    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("householdsRegistered")
    @Builder.Default
    private Long householdsRegistered = 0L;

    /**
     * Total occupants recorded across the households registered that day, i.e. the sum of
     * {@code household.numberOfMembers}. This is the household size the worker entered at
     * registration, which may exceed the number of members actually enrolled individually.
     */
    @JsonProperty("peopleInHouseholds")
    @Builder.Default
    private Long peopleInHouseholds = 0L;

    @JsonProperty("individualRegistered")
    @Builder.Default
    private Long individualRegistered = 0L;

    @JsonProperty("beneficiariesRegistered")
    @Builder.Default
    private Long beneficiariesRegistered = 0L;

    @JsonProperty("childrenTreated")
    @Builder.Default
    private Long childrenTreated = 0L;

    /**
     * Total ITNs handed out that day, i.e. the sum of every quantity in
     * {@link #stockConsumedMap}. The campaign distributes nets only, so the flat total and
     * the per-variant breakdown describe the same stock from two angles.
     */
    @JsonProperty("itnsDistributed")
    @Builder.Default
    private Long itnsDistributed = 0L;

    /** productVariantId -> quantity consumed (delivered) that day. */
    @JsonProperty("stockConsumedMap")
    @Builder.Default
    private Map<String, Long> stockConsumedMap = new HashMap<>();
}
