package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
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
* Task
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("projectId")
    @NotNull
    @Size(min=2,max=64)
    private String projectId = null;

    @JsonProperty("projectBeneficiaryId")
    @NotNull
    private String projectBeneficiaryId = null;

    @JsonProperty("resources")
    @NotNull
    @Valid
    @Size(min = 1)
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

    @JsonProperty("status")
    private String status = null;

    public Task addResourcesItem(TaskResource resourcesItem) {
        this.resources.add(resourcesItem);
        return this;
    }
}

