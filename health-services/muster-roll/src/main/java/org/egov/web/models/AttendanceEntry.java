package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.common.contract.models.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.*;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * This computed data will also be stored as part of the muster roll db.
 */
@ApiModel(description = "This computed data will also be stored as part of the muster roll db.")
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T19:58:09.415+05:30")

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

