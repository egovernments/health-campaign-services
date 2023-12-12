package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttendeeSearchCriteria {

    @JsonProperty("ids")
    private List<String> ids;

    @JsonProperty("individualIds")
    private List<String> individualIds;

    @JsonProperty("registerIds")
    private List<String> registerIds;

    @JsonProperty("enrollmentDate")
    private BigDecimal enrollmentDate = null;

    @JsonProperty("denrollmentDate")
    private BigDecimal denrollmentDate = null;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;
}
