package org.egov.transformer.models.expense;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import digit.models.coremodels.ProcessInstance;

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
public class Bill {
    @JsonProperty("id")
    @Valid
    private String id;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId;

    @JsonProperty("localityCode")
    @Size(min = 2, max = 256)
    private String localityCode;

    @JsonProperty("billDate")
    @Valid
    @NotNull
    private Long billDate;

    @JsonProperty("dueDate")
    @Valid
    private Long dueDate;

    @JsonProperty("totalAmount")
    @Valid
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @JsonProperty("totalPaidAmount")
    @Valid
    @Builder.Default
    private BigDecimal totalPaidAmount = BigDecimal.ZERO;

    @JsonProperty("businessService")
    @NotNull
    @Size(min = 2, max = 128)
    private String businessService;

    @JsonProperty("referenceId")
    @Size(min = 2, max = 256)
    @NotNull
    private String referenceId;

    @JsonProperty("fromPeriod")
    @Valid
    private Long fromPeriod;

    @JsonProperty("toPeriod")
    @Valid
    private Long toPeriod;

    @JsonProperty("paymentStatus")
    private PaymentStatus paymentStatus;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("billNumber")
    private String billNumber;

    @JsonProperty("payer")
    @NotNull
    @Valid
    private Party payer;

    @JsonProperty("billDetails")
    @NotNull
    @Valid
    private List<BillDetail> billDetails;

    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;

    @JsonProperty("wfStatus")
    @Size(min = 2, max = 64)
    private String wfStatus;

    @JsonProperty("processInstance")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ProcessInstance processInstance;

    public Bill addBillDetailsItem(BillDetail billDetailsItem) {

        if (null == this.billDetails)
            this.billDetails = new ArrayList<>();

        this.billDetails.add(billDetailsItem);
        return this;
    }
}
