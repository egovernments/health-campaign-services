package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.transformer.models.upstream.AttributeValue;


import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SideEffectsIndexV1 {

    @JsonProperty("id")
    private String id;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("taskClientReferenceId")
    private String taskClientReferenceId;

    @JsonProperty("projectBeneficiaryId")
    private String projectBeneficiaryId;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    private String projectBeneficiaryClientReferenceId;

    @JsonProperty("symptoms")
    private List<String> symptoms;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("auditDetails")
    private ObjectNode auditDetails;

    @JsonProperty("clientAuditDetails")
    private ObjectNode clientAuditDetails;

    @JsonProperty("additionalFields")
    private ObjectNode additionalFields;

}