package org.egov.transformer.models.musterRoll;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttendanceEntry {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("time")
    private BigDecimal time = null;

    @JsonProperty("attendance")
    private BigDecimal attendance = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;
}
