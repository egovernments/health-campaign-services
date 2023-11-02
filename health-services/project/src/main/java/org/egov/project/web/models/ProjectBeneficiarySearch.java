package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Size;
import java.util.List;

/**
* Search model for project beneficiary.
*/
    @ApiModel(description = "Search model for project beneficiary.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
@Table(name="project_beneficiary")
public class ProjectBeneficiarySearch {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("tenantId")
    @Size(min=2,max=1000)
    private String tenantId = null;

    @JsonProperty("projectId")
    @Size(min=2,max=64)
    private String projectId = null;

    @JsonProperty("beneficiaryId")
    @Size(min=2,max=64)
    private String beneficiaryId = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("dateOfRegistration")
    private Long dateOfRegistration = null;

    @JsonProperty("tag")
    private List<String> tag;
}

