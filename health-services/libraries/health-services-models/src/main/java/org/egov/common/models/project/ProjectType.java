package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.tracer.model.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
* This is the master data to capture the metadata of Project
*/
    @ApiModel(description = "This is the master data to capture the metadata of Project")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectType   {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("name")
    @Size(min=2,max=64)
    private String name = null;

    @JsonProperty("code")
    @Size(min=2,max=64)
    private String code = null;

    @JsonProperty("group")
    @Size(min=2,max=64)
    private String group = null;

    @JsonProperty("beneficiaryType")
    private String beneficiaryType = null;

    @JsonProperty("eligibilityCriteria")
    @Size(max=10)
    private List<String> eligibilityCriteria = null;

    @JsonProperty("taskProcedure")
    @Size(max=10)
    private List<String> taskProcedure = null;

    @JsonProperty("resources")
    @Valid
    private List<ProjectProductVariant> resources = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    public ProjectType addEligibilityCriteriaItem(String eligibilityCriteriaItem) {
        if (this.eligibilityCriteria == null) {
            this.eligibilityCriteria = new ArrayList<>();
        }
        this.eligibilityCriteria.add(eligibilityCriteriaItem);
        return this;
    }

    public ProjectType addTaskProcedureItem(String taskProcedureItem) {
        if (this.taskProcedure == null) {
            this.taskProcedure = new ArrayList<>();
        }
        this.taskProcedure.add(taskProcedureItem);
        return this;
    }

    public ProjectType addResourcesItem(ProjectProductVariant resourcesItem) {
        if (this.resources == null) {
            this.resources = new ArrayList<>();
        }
        this.resources.add(resourcesItem);
        return this;
    }

}

