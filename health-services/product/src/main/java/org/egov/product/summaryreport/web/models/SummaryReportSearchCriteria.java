package org.egov.product.summaryreport.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Search criteria for the daily summary report.
 * <p>
 * The employee whose data is reported is NOT taken from here - it is always resolved
 * from {@code RequestInfo.userInfo.uuid} so a worker can only pull their own summary.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummaryReportSearchCriteria {

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 1000)
    private String tenantId;

    /** Inclusive lower bound, epoch millis. */
    @JsonProperty("startDate")
    @NotNull
    private Long startDate;

    /** Inclusive upper bound, epoch millis. */
    @JsonProperty("endDate")
    @NotNull
    private Long endDate;
}
