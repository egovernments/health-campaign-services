package org.egov.common.models.project;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.springframework.validation.annotation.Validated;

/**
* Search model for project beneficiary.
*/
@ApiModel(description = "Search model for project beneficiary.")
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectBeneficiarySearch extends EgovOfflineSearchModel {

    @JsonProperty("projectId")
    private List<String> projectId = null;

    @JsonProperty("beneficiaryId")
    private List<String> beneficiaryId = null;

    @JsonProperty("dateOfRegistration")
    private Long dateOfRegistration = null;

    @JsonProperty("tag")
    private List<String> tag = null;
}

