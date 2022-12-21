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
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * The purpose of this object to define the Project for a geography and period
 */
@ApiModel(description = "The purpose of this object to define the Project for a geography and period")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-14T20:57:07.075+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("projectTypeId")
    @NotNull
    private String projectTypeId = null;

    @JsonProperty("subProjectTypeId")
    private String subProjectTypeId = null;

    @JsonProperty("address")
    @Valid
    private Address address = null;

    @JsonProperty("startDate")
    private Long startDate = null;

    @JsonProperty("endDate")
    private Long endDate = null;

    @JsonProperty("isTaskEnabled")
    private Boolean isTaskEnabled = false;

    @JsonProperty("parent")
    @Size(min = 2, max = 64)
    private String parent = null;

    @JsonProperty("targets")
    @Valid
    private List < Target > targets = null;

    @JsonProperty("department")
    @Size(min = 2, max = 64)
    private String department = null;

    @JsonProperty("description")
    @Size(min = 2)
    private String description = null;

    @JsonProperty("referenceId")
    @Size(min = 2, max = 100)
    private String referenceId = null;

    @JsonProperty("documents")
    @Valid
    private List < Document > documents = null;

    @JsonProperty("projectHierarchy")
    private String projectHierarchy = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    public Project addTargetsItem(Target targetsItem) {
        if (this.targets == null) {
            this.targets = new ArrayList<>();
        }
        this.targets.add(targetsItem);
        return this;
    }

    public Project addDocumentsItem(Document documentsItem) {
        if (this.documents == null) {
            this.documents = new ArrayList<>();
        }
        this.documents.add(documentsItem);
        return this;
    }
}