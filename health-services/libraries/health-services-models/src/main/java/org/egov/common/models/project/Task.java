package org.egov.common.models.project;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

/**
* Task
*/
@Validated

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Task extends EgovOfflineModel {

    @JsonProperty("projectId")
    @NotNull
    @Size(min=2,max=64)
    private String projectId = null;

    @JsonProperty("projectBeneficiaryId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryId = null;

    @JsonProperty("projectBeneficiaryClientReferenceId")
    @Size(min = 2, max = 64)
    private String projectBeneficiaryClientReferenceId = null;

    @JsonProperty("resources")
    @Valid
    @Builder.Default
    private List<TaskResource> resources = new ArrayList<>();

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

    @JsonProperty("createdDate")
    private Long createdDate = null;

    @JsonProperty("address")
    @Valid
    private Address address = null;

    //TODO remove this
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("taskStatus")
    @NotNull
    TaskStatus taskStatus = null;

    public Task addResourcesItem(TaskResource resourcesItem) {
        this.resources.add(resourcesItem);
        return this;
    }
}

