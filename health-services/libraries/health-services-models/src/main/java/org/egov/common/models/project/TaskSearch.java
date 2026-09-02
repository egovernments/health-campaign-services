package org.egov.common.models.project;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.springframework.validation.annotation.Validated;

/**
* TaskSearch
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskSearch extends EgovOfflineSearchModel {

    @JsonProperty("projectId")
    private List<String> projectId = null;

    @JsonProperty("projectBeneficiaryId")
    private List<String> projectBeneficiaryId = null;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    private List<String> projectBeneficiaryClientReferenceId = null;

    @JsonProperty("plannedStartDate")
    private Long plannedStartDate = null;

    @JsonProperty("plannedEndDate")
    private Long plannedEndDate = null;

    @JsonProperty("actualStartDate")
    private Long actualStartDate = null;

    @JsonProperty("actualEndDate")
    private Long actualEndDate = null;

    @JsonProperty("createdBy")
    private String createdBy = null;

    @JsonProperty("status")
    private String status = null;

    @JsonProperty("boundaryCode")
    private String boundaryCode = null;

}

