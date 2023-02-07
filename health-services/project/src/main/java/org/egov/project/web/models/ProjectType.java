package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
* This is the master data to capture the metadata of Project
*/
    @ApiModel(description = "This is the master data to capture the metadata of Project")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

