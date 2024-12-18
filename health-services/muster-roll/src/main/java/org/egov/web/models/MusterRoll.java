package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.workflow.ProcessInstance;
import org.egov.works.services.common.models.musterroll.Status;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * MusterRoll
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T19:58:09.415+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusterRoll {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("musterRollNumber")
    private String musterRollNumber = null;

    @JsonProperty("registerId")
    @NotNull
    @Size(min = 2, max = 256)
    private String registerId = null;

    @JsonProperty("status")
    private Status status = null;

    @JsonProperty("musterRollStatus")
    private String musterRollStatus = null;

    @JsonProperty("startDate")
    @NotNull
    private BigDecimal startDate = null;

    @JsonProperty("endDate")
    private BigDecimal endDate = null;

    @JsonProperty("individualEntries")
    @Valid
    private List<IndividualEntry> individualEntries = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("serviceCode")
    private String serviceCode = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("processInstance")
    private ProcessInstance processInstance = null;


    public MusterRoll addIndividualEntriesItem(IndividualEntry individualEntriesItem) {
        if (this.individualEntries == null) {
            this.individualEntries = new ArrayList<>();
        }
        this.individualEntries.add(individualEntriesItem);
        return this;
    }

}

