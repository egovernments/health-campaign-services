package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.project.Address;

@Validated
@JsonIgnoreProperties(
        ignoreUnknown = true
)
public class Task {
    @JsonProperty("id")
    private String id = null;
    @JsonProperty("tenantId")
    private @NotNull String tenantId = null;
    @JsonProperty("clientReferenceId")
    private @Size(
            min = 2,
            max = 64
    ) String clientReferenceId = null;
    @JsonProperty("projectId")
    private @NotNull @Size(
            min = 2,
            max = 64
    ) String projectId = null;
    @JsonProperty("projectBeneficiaryId")
    private @Size(
            min = 2,
            max = 64
    ) String projectBeneficiaryId = null;
    @JsonProperty("projectBeneficiaryClientReferenceId")
    private @Size(
            min = 2,
            max = 64
    ) String projectBeneficiaryClientReferenceId = null;
    @JsonProperty("resources")
    private @Valid List<TaskResource> resources;
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
    private @Valid Address address = null;
    @JsonProperty("additionalFields")
    private @Valid AdditionalFields additionalFields = null;
    @JsonProperty("isDeleted")
    private Boolean isDeleted;
    @JsonProperty("rowVersion")
    private Integer rowVersion;
    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;
    @JsonProperty("clientAuditDetails")
    private @Valid AuditDetails clientAuditDetails;
    @JsonProperty("status")
    private String status;
    @JsonIgnore
    private Boolean hasErrors;

    public Task addResourcesItem(TaskResource resourcesItem) {
        this.resources.add(resourcesItem);
        return this;
    }

    private static List<TaskResource> $default$resources() {
        return new ArrayList();
    }

    public static TaskBuilder builder() {
        return new TaskBuilder();
    }

    public String getId() {
        return this.id;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public String getClientReferenceId() {
        return this.clientReferenceId;
    }

    public String getProjectId() {
        return this.projectId;
    }

    public String getProjectBeneficiaryId() {
        return this.projectBeneficiaryId;
    }

    public String getProjectBeneficiaryClientReferenceId() {
        return this.projectBeneficiaryClientReferenceId;
    }

    public List<TaskResource> getResources() {
        return this.resources;
    }

    public Long getPlannedStartDate() {
        return this.plannedStartDate;
    }

    public Long getPlannedEndDate() {
        return this.plannedEndDate;
    }

    public Long getActualStartDate() {
        return this.actualStartDate;
    }

    public Long getActualEndDate() {
        return this.actualEndDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Long getCreatedDate() {
        return this.createdDate;
    }

    public Address getAddress() {
        return this.address;
    }

    public AdditionalFields getAdditionalFields() {
        return this.additionalFields;
    }

    public Boolean getIsDeleted() {
        return this.isDeleted;
    }

    public Integer getRowVersion() {
        return this.rowVersion;
    }

    public AuditDetails getAuditDetails() {
        return this.auditDetails;
    }

    public AuditDetails getClientAuditDetails() {
        return this.clientAuditDetails;
    }

    public String getStatus() {
        return this.status;
    }

    public Boolean getHasErrors() {
        return this.hasErrors;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("tenantId")
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @JsonProperty("clientReferenceId")
    public void setClientReferenceId(String clientReferenceId) {
        this.clientReferenceId = clientReferenceId;
    }

    @JsonProperty("projectId")
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @JsonProperty("projectBeneficiaryId")
    public void setProjectBeneficiaryId(String projectBeneficiaryId) {
        this.projectBeneficiaryId = projectBeneficiaryId;
    }

    @JsonProperty("projectBeneficiaryClientReferenceId")
    public void setProjectBeneficiaryClientReferenceId(String projectBeneficiaryClientReferenceId) {
        this.projectBeneficiaryClientReferenceId = projectBeneficiaryClientReferenceId;
    }

    @JsonProperty("resources")
    public void setResources(List<TaskResource> resources) {
        this.resources = resources;
    }

    @JsonProperty("plannedStartDate")
    public void setPlannedStartDate(Long plannedStartDate) {
        this.plannedStartDate = plannedStartDate;
    }

    @JsonProperty("plannedEndDate")
    public void setPlannedEndDate(Long plannedEndDate) {
        this.plannedEndDate = plannedEndDate;
    }

    @JsonProperty("actualStartDate")
    public void setActualStartDate(Long actualStartDate) {
        this.actualStartDate = actualStartDate;
    }

    @JsonProperty("actualEndDate")
    public void setActualEndDate(Long actualEndDate) {
        this.actualEndDate = actualEndDate;
    }

    @JsonProperty("createdBy")
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @JsonProperty("createdDate")
    public void setCreatedDate(Long createdDate) {
        this.createdDate = createdDate;
    }

    @JsonProperty("address")
    public void setAddress(Address address) {
        this.address = address;
    }

    @JsonProperty("additionalFields")
    public void setAdditionalFields(AdditionalFields additionalFields) {
        this.additionalFields = additionalFields;
    }

    @JsonProperty("isDeleted")
    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    @JsonProperty("rowVersion")
    public void setRowVersion(Integer rowVersion) {
        this.rowVersion = rowVersion;
    }

    @JsonProperty("auditDetails")
    public void setAuditDetails(AuditDetails auditDetails) {
        this.auditDetails = auditDetails;
    }

    @JsonProperty("clientAuditDetails")
    public void setClientAuditDetails(AuditDetails clientAuditDetails) {
        this.clientAuditDetails = clientAuditDetails;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @JsonIgnore
    public void setHasErrors(Boolean hasErrors) {
        this.hasErrors = hasErrors;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Task)) {
            return false;
        } else {
            Task other = (Task)o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$plannedStartDate = this.getPlannedStartDate();
                Object other$plannedStartDate = other.getPlannedStartDate();
                if (this$plannedStartDate == null) {
                    if (other$plannedStartDate != null) {
                        return false;
                    }
                } else if (!this$plannedStartDate.equals(other$plannedStartDate)) {
                    return false;
                }

                Object this$plannedEndDate = this.getPlannedEndDate();
                Object other$plannedEndDate = other.getPlannedEndDate();
                if (this$plannedEndDate == null) {
                    if (other$plannedEndDate != null) {
                        return false;
                    }
                } else if (!this$plannedEndDate.equals(other$plannedEndDate)) {
                    return false;
                }

                Object this$actualStartDate = this.getActualStartDate();
                Object other$actualStartDate = other.getActualStartDate();
                if (this$actualStartDate == null) {
                    if (other$actualStartDate != null) {
                        return false;
                    }
                } else if (!this$actualStartDate.equals(other$actualStartDate)) {
                    return false;
                }

                Object this$actualEndDate = this.getActualEndDate();
                Object other$actualEndDate = other.getActualEndDate();
                if (this$actualEndDate == null) {
                    if (other$actualEndDate != null) {
                        return false;
                    }
                } else if (!this$actualEndDate.equals(other$actualEndDate)) {
                    return false;
                }

                Object this$createdDate = this.getCreatedDate();
                Object other$createdDate = other.getCreatedDate();
                if (this$createdDate == null) {
                    if (other$createdDate != null) {
                        return false;
                    }
                } else if (!this$createdDate.equals(other$createdDate)) {
                    return false;
                }

                Object this$isDeleted = this.getIsDeleted();
                Object other$isDeleted = other.getIsDeleted();
                if (this$isDeleted == null) {
                    if (other$isDeleted != null) {
                        return false;
                    }
                } else if (!this$isDeleted.equals(other$isDeleted)) {
                    return false;
                }

                Object this$rowVersion = this.getRowVersion();
                Object other$rowVersion = other.getRowVersion();
                if (this$rowVersion == null) {
                    if (other$rowVersion != null) {
                        return false;
                    }
                } else if (!this$rowVersion.equals(other$rowVersion)) {
                    return false;
                }

                Object this$hasErrors = this.getHasErrors();
                Object other$hasErrors = other.getHasErrors();
                if (this$hasErrors == null) {
                    if (other$hasErrors != null) {
                        return false;
                    }
                } else if (!this$hasErrors.equals(other$hasErrors)) {
                    return false;
                }

                Object this$id = this.getId();
                Object other$id = other.getId();
                if (this$id == null) {
                    if (other$id != null) {
                        return false;
                    }
                } else if (!this$id.equals(other$id)) {
                    return false;
                }

                Object this$tenantId = this.getTenantId();
                Object other$tenantId = other.getTenantId();
                if (this$tenantId == null) {
                    if (other$tenantId != null) {
                        return false;
                    }
                } else if (!this$tenantId.equals(other$tenantId)) {
                    return false;
                }

                Object this$clientReferenceId = this.getClientReferenceId();
                Object other$clientReferenceId = other.getClientReferenceId();
                if (this$clientReferenceId == null) {
                    if (other$clientReferenceId != null) {
                        return false;
                    }
                } else if (!this$clientReferenceId.equals(other$clientReferenceId)) {
                    return false;
                }

                Object this$projectId = this.getProjectId();
                Object other$projectId = other.getProjectId();
                if (this$projectId == null) {
                    if (other$projectId != null) {
                        return false;
                    }
                } else if (!this$projectId.equals(other$projectId)) {
                    return false;
                }

                Object this$projectBeneficiaryId = this.getProjectBeneficiaryId();
                Object other$projectBeneficiaryId = other.getProjectBeneficiaryId();
                if (this$projectBeneficiaryId == null) {
                    if (other$projectBeneficiaryId != null) {
                        return false;
                    }
                } else if (!this$projectBeneficiaryId.equals(other$projectBeneficiaryId)) {
                    return false;
                }

                Object this$projectBeneficiaryClientReferenceId = this.getProjectBeneficiaryClientReferenceId();
                Object other$projectBeneficiaryClientReferenceId = other.getProjectBeneficiaryClientReferenceId();
                if (this$projectBeneficiaryClientReferenceId == null) {
                    if (other$projectBeneficiaryClientReferenceId != null) {
                        return false;
                    }
                } else if (!this$projectBeneficiaryClientReferenceId.equals(other$projectBeneficiaryClientReferenceId)) {
                    return false;
                }

                Object this$resources = this.getResources();
                Object other$resources = other.getResources();
                if (this$resources == null) {
                    if (other$resources != null) {
                        return false;
                    }
                } else if (!this$resources.equals(other$resources)) {
                    return false;
                }

                Object this$createdBy = this.getCreatedBy();
                Object other$createdBy = other.getCreatedBy();
                if (this$createdBy == null) {
                    if (other$createdBy != null) {
                        return false;
                    }
                } else if (!this$createdBy.equals(other$createdBy)) {
                    return false;
                }

                Object this$address = this.getAddress();
                Object other$address = other.getAddress();
                if (this$address == null) {
                    if (other$address != null) {
                        return false;
                    }
                } else if (!this$address.equals(other$address)) {
                    return false;
                }

                Object this$additionalFields = this.getAdditionalFields();
                Object other$additionalFields = other.getAdditionalFields();
                if (this$additionalFields == null) {
                    if (other$additionalFields != null) {
                        return false;
                    }
                } else if (!this$additionalFields.equals(other$additionalFields)) {
                    return false;
                }

                Object this$auditDetails = this.getAuditDetails();
                Object other$auditDetails = other.getAuditDetails();
                if (this$auditDetails == null) {
                    if (other$auditDetails != null) {
                        return false;
                    }
                } else if (!this$auditDetails.equals(other$auditDetails)) {
                    return false;
                }

                Object this$clientAuditDetails = this.getClientAuditDetails();
                Object other$clientAuditDetails = other.getClientAuditDetails();
                if (this$clientAuditDetails == null) {
                    if (other$clientAuditDetails != null) {
                        return false;
                    }
                } else if (!this$clientAuditDetails.equals(other$clientAuditDetails)) {
                    return false;
                }

                Object this$status = this.getStatus();
                Object other$status = other.getStatus();
                if (this$status == null) {
                    if (other$status != null) {
                        return false;
                    }
                } else if (!this$status.equals(other$status)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof Task;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $plannedStartDate = this.getPlannedStartDate();
        result = result * 59 + ($plannedStartDate == null ? 43 : $plannedStartDate.hashCode());
        Object $plannedEndDate = this.getPlannedEndDate();
        result = result * 59 + ($plannedEndDate == null ? 43 : $plannedEndDate.hashCode());
        Object $actualStartDate = this.getActualStartDate();
        result = result * 59 + ($actualStartDate == null ? 43 : $actualStartDate.hashCode());
        Object $actualEndDate = this.getActualEndDate();
        result = result * 59 + ($actualEndDate == null ? 43 : $actualEndDate.hashCode());
        Object $createdDate = this.getCreatedDate();
        result = result * 59 + ($createdDate == null ? 43 : $createdDate.hashCode());
        Object $isDeleted = this.getIsDeleted();
        result = result * 59 + ($isDeleted == null ? 43 : $isDeleted.hashCode());
        Object $rowVersion = this.getRowVersion();
        result = result * 59 + ($rowVersion == null ? 43 : $rowVersion.hashCode());
        Object $hasErrors = this.getHasErrors();
        result = result * 59 + ($hasErrors == null ? 43 : $hasErrors.hashCode());
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $tenantId = this.getTenantId();
        result = result * 59 + ($tenantId == null ? 43 : $tenantId.hashCode());
        Object $clientReferenceId = this.getClientReferenceId();
        result = result * 59 + ($clientReferenceId == null ? 43 : $clientReferenceId.hashCode());
        Object $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : $projectId.hashCode());
        Object $projectBeneficiaryId = this.getProjectBeneficiaryId();
        result = result * 59 + ($projectBeneficiaryId == null ? 43 : $projectBeneficiaryId.hashCode());
        Object $projectBeneficiaryClientReferenceId = this.getProjectBeneficiaryClientReferenceId();
        result = result * 59 + ($projectBeneficiaryClientReferenceId == null ? 43 : $projectBeneficiaryClientReferenceId.hashCode());
        Object $resources = this.getResources();
        result = result * 59 + ($resources == null ? 43 : $resources.hashCode());
        Object $createdBy = this.getCreatedBy();
        result = result * 59 + ($createdBy == null ? 43 : $createdBy.hashCode());
        Object $address = this.getAddress();
        result = result * 59 + ($address == null ? 43 : $address.hashCode());
        Object $additionalFields = this.getAdditionalFields();
        result = result * 59 + ($additionalFields == null ? 43 : $additionalFields.hashCode());
        Object $auditDetails = this.getAuditDetails();
        result = result * 59 + ($auditDetails == null ? 43 : $auditDetails.hashCode());
        Object $clientAuditDetails = this.getClientAuditDetails();
        result = result * 59 + ($clientAuditDetails == null ? 43 : $clientAuditDetails.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        return result;
    }

    public String toString() {
        return "Task(id=" + this.getId() + ", tenantId=" + this.getTenantId() + ", clientReferenceId=" + this.getClientReferenceId() + ", projectId=" + this.getProjectId() + ", projectBeneficiaryId=" + this.getProjectBeneficiaryId() + ", projectBeneficiaryClientReferenceId=" + this.getProjectBeneficiaryClientReferenceId() + ", resources=" + this.getResources() + ", plannedStartDate=" + this.getPlannedStartDate() + ", plannedEndDate=" + this.getPlannedEndDate() + ", actualStartDate=" + this.getActualStartDate() + ", actualEndDate=" + this.getActualEndDate() + ", createdBy=" + this.getCreatedBy() + ", createdDate=" + this.getCreatedDate() + ", address=" + this.getAddress() + ", additionalFields=" + this.getAdditionalFields() + ", isDeleted=" + this.getIsDeleted() + ", rowVersion=" + this.getRowVersion() + ", auditDetails=" + this.getAuditDetails() + ", clientAuditDetails=" + this.getClientAuditDetails() + ", status=" + this.getStatus() + ", hasErrors=" + this.getHasErrors() + ")";
    }

    public Task() {
        this.isDeleted = Boolean.FALSE;
        this.rowVersion = null;
        this.auditDetails = null;
        this.clientAuditDetails = null;
        this.status = null;
        this.hasErrors = Boolean.FALSE;
        this.resources = $default$resources();
    }

    public Task(String id, String tenantId, String clientReferenceId, String projectId, String projectBeneficiaryId, String projectBeneficiaryClientReferenceId, List<TaskResource> resources, Long plannedStartDate, Long plannedEndDate, Long actualStartDate, Long actualEndDate, String createdBy, Long createdDate, Address address, AdditionalFields additionalFields, Boolean isDeleted, Integer rowVersion, AuditDetails auditDetails, AuditDetails clientAuditDetails, String status, Boolean hasErrors) {
        this.isDeleted = Boolean.FALSE;
        this.rowVersion = null;
        this.auditDetails = null;
        this.clientAuditDetails = null;
        this.status = null;
        this.hasErrors = Boolean.FALSE;
        this.id = id;
        this.tenantId = tenantId;
        this.clientReferenceId = clientReferenceId;
        this.projectId = projectId;
        this.projectBeneficiaryId = projectBeneficiaryId;
        this.projectBeneficiaryClientReferenceId = projectBeneficiaryClientReferenceId;
        this.resources = resources;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.actualStartDate = actualStartDate;
        this.actualEndDate = actualEndDate;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.address = address;
        this.additionalFields = additionalFields;
        this.isDeleted = isDeleted;
        this.rowVersion = rowVersion;
        this.auditDetails = auditDetails;
        this.clientAuditDetails = clientAuditDetails;
        this.status = status;
        this.hasErrors = hasErrors;
    }

    public static class TaskBuilder {
        private String id;
        private String tenantId;
        private String clientReferenceId;
        private String projectId;
        private String projectBeneficiaryId;
        private String projectBeneficiaryClientReferenceId;
        private boolean resources$set;
        private List<TaskResource> resources$value;
        private Long plannedStartDate;
        private Long plannedEndDate;
        private Long actualStartDate;
        private Long actualEndDate;
        private String createdBy;
        private Long createdDate;
        private Address address;
        private AdditionalFields additionalFields;
        private Boolean isDeleted;
        private Integer rowVersion;
        private AuditDetails auditDetails;
        private AuditDetails clientAuditDetails;
        private String status;
        private Boolean hasErrors;

        TaskBuilder() {
        }

        @JsonProperty("id")
        public TaskBuilder id(String id) {
            this.id = id;
            return this;
        }

        @JsonProperty("tenantId")
        public TaskBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        @JsonProperty("clientReferenceId")
        public TaskBuilder clientReferenceId(String clientReferenceId) {
            this.clientReferenceId = clientReferenceId;
            return this;
        }

        @JsonProperty("projectId")
        public TaskBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        @JsonProperty("projectBeneficiaryId")
        public TaskBuilder projectBeneficiaryId(String projectBeneficiaryId) {
            this.projectBeneficiaryId = projectBeneficiaryId;
            return this;
        }

        @JsonProperty("projectBeneficiaryClientReferenceId")
        public TaskBuilder projectBeneficiaryClientReferenceId(String projectBeneficiaryClientReferenceId) {
            this.projectBeneficiaryClientReferenceId = projectBeneficiaryClientReferenceId;
            return this;
        }

        @JsonProperty("resources")
        public TaskBuilder resources(List<TaskResource> resources) {
            this.resources$value = resources;
            this.resources$set = true;
            return this;
        }

        @JsonProperty("plannedStartDate")
        public TaskBuilder plannedStartDate(Long plannedStartDate) {
            this.plannedStartDate = plannedStartDate;
            return this;
        }

        @JsonProperty("plannedEndDate")
        public TaskBuilder plannedEndDate(Long plannedEndDate) {
            this.plannedEndDate = plannedEndDate;
            return this;
        }

        @JsonProperty("actualStartDate")
        public TaskBuilder actualStartDate(Long actualStartDate) {
            this.actualStartDate = actualStartDate;
            return this;
        }

        @JsonProperty("actualEndDate")
        public TaskBuilder actualEndDate(Long actualEndDate) {
            this.actualEndDate = actualEndDate;
            return this;
        }

        @JsonProperty("createdBy")
        public TaskBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        @JsonProperty("createdDate")
        public TaskBuilder createdDate(Long createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        @JsonProperty("address")
        public TaskBuilder address(Address address) {
            this.address = address;
            return this;
        }

        @JsonProperty("additionalFields")
        public TaskBuilder additionalFields(AdditionalFields additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }

        @JsonProperty("isDeleted")
        public TaskBuilder isDeleted(Boolean isDeleted) {
            this.isDeleted = isDeleted;
            return this;
        }

        @JsonProperty("rowVersion")
        public TaskBuilder rowVersion(Integer rowVersion) {
            this.rowVersion = rowVersion;
            return this;
        }

        @JsonProperty("auditDetails")
        public TaskBuilder auditDetails(AuditDetails auditDetails) {
            this.auditDetails = auditDetails;
            return this;
        }

        @JsonProperty("clientAuditDetails")
        public TaskBuilder clientAuditDetails(AuditDetails clientAuditDetails) {
            this.clientAuditDetails = clientAuditDetails;
            return this;
        }

        @JsonProperty("status")
        public TaskBuilder status(String status) {
            this.status = status;
            return this;
        }

        @JsonIgnore
        public TaskBuilder hasErrors(Boolean hasErrors) {
            this.hasErrors = hasErrors;
            return this;
        }

        public Task build() {
            List<TaskResource> resources$value = this.resources$value;
            if (!this.resources$set) {
                resources$value = Task.$default$resources();
            }

            return new Task(this.id, this.tenantId, this.clientReferenceId, this.projectId, this.projectBeneficiaryId, this.projectBeneficiaryClientReferenceId, resources$value, this.plannedStartDate, this.plannedEndDate, this.actualStartDate, this.actualEndDate, this.createdBy, this.createdDate, this.address, this.additionalFields, this.isDeleted, this.rowVersion, this.auditDetails, this.clientAuditDetails, this.status, this.hasErrors);
        }

        public String toString() {
            return "Task.TaskBuilder(id=" + this.id + ", tenantId=" + this.tenantId + ", clientReferenceId=" + this.clientReferenceId + ", projectId=" + this.projectId + ", projectBeneficiaryId=" + this.projectBeneficiaryId + ", projectBeneficiaryClientReferenceId=" + this.projectBeneficiaryClientReferenceId + ", resources$value=" + this.resources$value + ", plannedStartDate=" + this.plannedStartDate + ", plannedEndDate=" + this.plannedEndDate + ", actualStartDate=" + this.actualStartDate + ", actualEndDate=" + this.actualEndDate + ", createdBy=" + this.createdBy + ", createdDate=" + this.createdDate + ", address=" + this.address + ", additionalFields=" + this.additionalFields + ", isDeleted=" + this.isDeleted + ", rowVersion=" + this.rowVersion + ", auditDetails=" + this.auditDetails + ", clientAuditDetails=" + this.clientAuditDetails + ", status=" + this.status + ", hasErrors=" + this.hasErrors + ")";
        }
    }
}
