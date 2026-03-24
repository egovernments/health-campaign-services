package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.tracer.model.AuditDetails;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FailedEventsIndex extends ProjectInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("url")
    private String url;

    @JsonProperty("exceptionMessage")
    private String exceptionMessage;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("role")
    private List<String> role;

    @JsonProperty("nameOfUser")
    private String nameOfUser;

    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;

    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("errorCategory")
    private String errorCategory;

}
