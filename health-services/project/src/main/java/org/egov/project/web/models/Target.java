package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

/**
* Target
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Target {
    @JsonProperty("id")
    private String id = null;

    @JsonIgnore
    private String projectid = null;

    @JsonProperty("beneficiaryType")
    private String beneficiaryType = null;

    @JsonProperty("totalNo")
    private Integer totalNo = null;

    @JsonProperty("targetNo")
    private Integer targetNo = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;


}

