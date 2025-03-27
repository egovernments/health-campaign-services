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
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillDetail {
    @JsonProperty("id")
    @Valid
    private String id;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId;

    @JsonProperty("billId")
    @Valid
    private String billId;

    @JsonProperty("totalAmount")
    @Valid
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @JsonProperty("totalPaidAmount")
    @Valid
    @Builder.Default
    private BigDecimal totalPaidAmount = BigDecimal.ZERO;

    @JsonProperty("referenceId")
    @Size(min = 2, max = 64)
    private String referenceId;

    @JsonProperty("paymentStatus")
    private PaymentStatus paymentStatus;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("fromPeriod")
    @Valid
    private Long fromPeriod;

    @JsonProperty("toPeriod")
    @Valid
    private Long toPeriod;

    @JsonProperty("payee")
    @NotNull
    @Valid
    private Party payee;

    @JsonProperty("lineItems")
    @Valid
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();

    @JsonProperty("payableLineItems")
    @NotNull
    @Valid
    @Builder.Default
    private List<LineItem> payableLineItems = new ArrayList<>();

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;

    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    public BillDetail addLineItems(LineItem lineItem) {

        if (this.lineItems == null) {
            this.lineItems = new ArrayList<>();
        }
        this.lineItems.add(lineItem);
        return this;
    }

    public BillDetail addPayableLineItems(LineItem payableLineItem) {

        if (this.payableLineItems == null) {
            this.payableLineItems = new ArrayList<>();
        }
        this.payableLineItems.add(payableLineItem);
        return this;
    }
}
