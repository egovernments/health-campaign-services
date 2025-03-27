package org.egov.transformer.models.expense;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LineItem {
    @JsonProperty("id")
    @Valid
    private String id;

    @JsonProperty("billDetailId")
    @Valid
    private String billDetailId;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId;

    @JsonProperty("headCode")
    @NotNull
    @Size(min = 2, max = 64)
    private String headCode;

    @JsonProperty("amount")
    @NotNull
    @Valid
    private BigDecimal amount;

    @JsonProperty("type")
    @NotNull
    private LineItemType type;

    @JsonProperty("paidAmount")
    @Valid
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("paymentStatus")
    private PaymentStatus paymentStatus;

    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;
}
