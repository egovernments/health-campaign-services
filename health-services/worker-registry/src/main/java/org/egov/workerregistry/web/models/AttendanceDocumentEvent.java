package org.egov.workerregistry.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttendanceDocumentEvent {

    @JsonProperty("individualId")
    private String individualId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("fileStore")
    private String fileStore;

    @JsonProperty("type")
    private String type; // "SIGNATURE" or "PHOTO"

    @JsonProperty("clientAuditDetails")
    private AuditDetails clientAuditDetails;
}
