package org.egov.common.models.project;

import java.util.List;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
* Search model for project beneficiary.
*/
    @ApiModel(description = "Search model for project beneficiary.")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectBeneficiarySearch {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("tenantId")
    @Size(min=2,max=1000)
    private String tenantId = null;

    @JsonProperty("projectId")
    private List<String> projectId = null;

    @JsonProperty("beneficiaryId")
    private List<String> beneficiaryId = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("dateOfRegistration")
    private Long dateOfRegistration = null;

    @JsonProperty("tag")
    private List<String> tag = null;
}

