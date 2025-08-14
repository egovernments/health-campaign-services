package org.egov.transformer.models.attendance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceLog
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T14:44:21.051+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttendanceLog {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("registerId")
    private String registerId = null;

    @JsonProperty("individualId")
    private String individualId = null;

    @JsonProperty("userName")
    private String userName = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("time")
    private BigDecimal time = null;

    @JsonProperty("type")
    private String type = null;

    @JsonProperty("status")
    private Status status = null;

    @JsonProperty("documentIds")
    @Valid
    private List<Document> documentIds = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalDetails")
    private JsonNode additionalDetails = null;


    public AttendanceLog addDocumentIdsItem(Document documentIdsItem) {
        if (this.documentIds == null) {
            this.documentIds = new ArrayList<>();
        }
        this.documentIds.add(documentIdsItem);
        return this;
    }

}

