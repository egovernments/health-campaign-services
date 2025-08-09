package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

import org.egov.common.contract.models.AuditDetails;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GenerateResource {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("refernceId")
    private String refernceId;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("status")
    private String status;

    @JsonProperty("additionalDetails")
    private Map<String, Object> additionalDetails;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("fileStoreId")
    private String fileStoreId;
}
