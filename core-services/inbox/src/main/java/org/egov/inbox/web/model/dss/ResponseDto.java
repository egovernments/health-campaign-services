package org.egov.inbox.web.model.dss;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ResponseDto {

    @Valid
    @JsonProperty("totalAmountPaid")
    private BigDecimal totalAmountPaid;

    @Valid
    @JsonProperty("activeConnections")
    private BigDecimal activeConnections;
}
