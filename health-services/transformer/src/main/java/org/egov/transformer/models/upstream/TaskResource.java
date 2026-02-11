package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.egov.common.models.project.AdditionalFields;
import org.springframework.validation.annotation.Validated;

@Validated
@JsonIgnoreProperties(
        ignoreUnknown = true
)
public class TaskResource {
    @JsonProperty("id")
    private @Size(
            min = 2,
            max = 64
    ) String id = null;
    @JsonProperty("tenantId")
    private @NotNull String tenantId = null;
    @JsonProperty("clientReferenceId")
    private @Size(
            min = 2,
            max = 64
    ) String clientReferenceId = null;
    @JsonProperty("taskId")
    private @Size(
            min = 2,
            max = 64
    ) String taskId = null;
    @JsonProperty("productVariantId")
    private @NotNull @Size(
            min = 2,
            max = 64
    ) String productVariantId = null;
    @JsonProperty("additionalFields")
    private @Valid AdditionalFields additionalFields = null;
    @JsonProperty("quantity")
    private @NotNull Long quantity = null;
    @JsonProperty("isDelivered")
    private @NotNull Boolean isDelivered = null;
    @JsonProperty("deliveryComment")
    private @Size(
            min = 0,
            max = 1000
    ) String deliveryComment = null;
    @JsonProperty("isDeleted")
    private Boolean isDeleted;
    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;

    public static TaskResourceBuilder builder() {
        return new TaskResourceBuilder();
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

    public String getTaskId() {
        return this.taskId;
    }

    public String getProductVariantId() {
        return this.productVariantId;
    }

    public AdditionalFields getAdditionalFields() {
        return this.additionalFields;
    }

    public Long getQuantity() {
        return this.quantity;
    }

    public Boolean getIsDelivered() {
        return this.isDelivered;
    }

    public String getDeliveryComment() {
        return this.deliveryComment;
    }

    public Boolean getIsDeleted() {
        return this.isDeleted;
    }

    public AuditDetails getAuditDetails() {
        return this.auditDetails;
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

    @JsonProperty("taskId")
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @JsonProperty("productVariantId")
    public void setProductVariantId(String productVariantId) {
        this.productVariantId = productVariantId;
    }

    @JsonProperty("quantity")
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    @JsonProperty("isDelivered")
    public void setIsDelivered(Boolean isDelivered) {
        this.isDelivered = isDelivered;
    }

    @JsonProperty("deliveryComment")
    public void setDeliveryComment(String deliveryComment) {
        this.deliveryComment = deliveryComment;
    }

    @JsonProperty("isDeleted")
    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    @JsonProperty("auditDetails")
    public void setAuditDetails(AuditDetails auditDetails) {
        this.auditDetails = auditDetails;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof TaskResource)) {
            return false;
        } else {
            TaskResource other = (TaskResource)o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$quantity = this.getQuantity();
                Object other$quantity = other.getQuantity();
                if (this$quantity == null) {
                    if (other$quantity != null) {
                        return false;
                    }
                } else if (!this$quantity.equals(other$quantity)) {
                    return false;
                }

                Object this$isDelivered = this.getIsDelivered();
                Object other$isDelivered = other.getIsDelivered();
                if (this$isDelivered == null) {
                    if (other$isDelivered != null) {
                        return false;
                    }
                } else if (!this$isDelivered.equals(other$isDelivered)) {
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

                Object this$taskId = this.getTaskId();
                Object other$taskId = other.getTaskId();
                if (this$taskId == null) {
                    if (other$taskId != null) {
                        return false;
                    }
                } else if (!this$taskId.equals(other$taskId)) {
                    return false;
                }

                Object this$productVariantId = this.getProductVariantId();
                Object other$productVariantId = other.getProductVariantId();
                if (this$productVariantId == null) {
                    if (other$productVariantId != null) {
                        return false;
                    }
                } else if (!this$productVariantId.equals(other$productVariantId)) {
                    return false;
                }

                Object this$deliveryComment = this.getDeliveryComment();
                Object other$deliveryComment = other.getDeliveryComment();
                if (this$deliveryComment == null) {
                    if (other$deliveryComment != null) {
                        return false;
                    }
                } else if (!this$deliveryComment.equals(other$deliveryComment)) {
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

                return true;
            }
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof TaskResource;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $quantity = this.getQuantity();
        result = result * 59 + ($quantity == null ? 43 : $quantity.hashCode());
        Object $isDelivered = this.getIsDelivered();
        result = result * 59 + ($isDelivered == null ? 43 : $isDelivered.hashCode());
        Object $isDeleted = this.getIsDeleted();
        result = result * 59 + ($isDeleted == null ? 43 : $isDeleted.hashCode());
        Object $id = this.getId();
        result = result * 59 + ($id == null ? 43 : $id.hashCode());
        Object $tenantId = this.getTenantId();
        result = result * 59 + ($tenantId == null ? 43 : $tenantId.hashCode());
        Object $clientReferenceId = this.getClientReferenceId();
        result = result * 59 + ($clientReferenceId == null ? 43 : $clientReferenceId.hashCode());
        Object $taskId = this.getTaskId();
        result = result * 59 + ($taskId == null ? 43 : $taskId.hashCode());
        Object $productVariantId = this.getProductVariantId();
        result = result * 59 + ($productVariantId == null ? 43 : $productVariantId.hashCode());
        Object $deliveryComment = this.getDeliveryComment();
        result = result * 59 + ($deliveryComment == null ? 43 : $deliveryComment.hashCode());
        Object $auditDetails = this.getAuditDetails();
        result = result * 59 + ($auditDetails == null ? 43 : $auditDetails.hashCode());
        return result;
    }

    public String toString() {
        return "TaskResource(id=" + this.getId() + ", tenantId=" + this.getTenantId() + ", clientReferenceId=" + this.getClientReferenceId() + ", taskId=" + this.getTaskId() + ", productVariantId=" + this.getProductVariantId() + ", quantity=" + this.getQuantity() + ", isDelivered=" + this.getIsDelivered() + ", deliveryComment=" + this.getDeliveryComment() + ", isDeleted=" + this.getIsDeleted() + ", auditDetails=" + this.getAuditDetails() + ")";
    }

    public TaskResource() {
        this.isDeleted = Boolean.FALSE;
        this.auditDetails = null;
    }

    public TaskResource(String id, String tenantId, String clientReferenceId, String taskId, String productVariantId, AdditionalFields additionalFields, Long quantity, Boolean isDelivered, String deliveryComment, Boolean isDeleted, AuditDetails auditDetails) {
        this.isDeleted = Boolean.FALSE;
        this.auditDetails = null;
        this.id = id;
        this.tenantId = tenantId;
        this.clientReferenceId = clientReferenceId;
        this.taskId = taskId;
        this.productVariantId = productVariantId;
        this.additionalFields = additionalFields;
        this.quantity = quantity;
        this.isDelivered = isDelivered;
        this.deliveryComment = deliveryComment;
        this.isDeleted = isDeleted;
        this.auditDetails = auditDetails;
    }

    public static class TaskResourceBuilder {
        private String id;
        private String tenantId;
        private String clientReferenceId;
        private String taskId;
        private String productVariantId;
        private AdditionalFields additionalFields;
        private Long quantity;
        private Boolean isDelivered;
        private String deliveryComment;
        private Boolean isDeleted;
        private AuditDetails auditDetails;

        TaskResourceBuilder() {
        }

        @JsonProperty("id")
        public TaskResourceBuilder id(String id) {
            this.id = id;
            return this;
        }

        @JsonProperty("tenantId")
        public TaskResourceBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        @JsonProperty("clientReferenceId")
        public TaskResourceBuilder clientReferenceId(String clientReferenceId) {
            this.clientReferenceId = clientReferenceId;
            return this;
        }

        @JsonProperty("taskId")
        public TaskResourceBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        @JsonProperty("productVariantId")
        public TaskResourceBuilder productVariantId(String productVariantId) {
            this.productVariantId = productVariantId;
            return this;
        }

        @JsonProperty("additionalFields")
        public TaskResourceBuilder additionalFields(AdditionalFields additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }

        @JsonProperty("quantity")
        public TaskResourceBuilder quantity(Long quantity) {
            this.quantity = quantity;
            return this;
        }

        @JsonProperty("isDelivered")
        public TaskResourceBuilder isDelivered(Boolean isDelivered) {
            this.isDelivered = isDelivered;
            return this;
        }

        @JsonProperty("deliveryComment")
        public TaskResourceBuilder deliveryComment(String deliveryComment) {
            this.deliveryComment = deliveryComment;
            return this;
        }

        @JsonProperty("isDeleted")
        public TaskResourceBuilder isDeleted(Boolean isDeleted) {
            this.isDeleted = isDeleted;
            return this;
        }

        @JsonProperty("auditDetails")
        public TaskResourceBuilder auditDetails(AuditDetails auditDetails) {
            this.auditDetails = auditDetails;
            return this;
        }

        public TaskResource build() {
            return new TaskResource(this.id, this.tenantId, this.clientReferenceId, this.taskId, this.productVariantId, this.additionalFields, this.quantity, this.isDelivered, this.deliveryComment, this.isDeleted, this.auditDetails);
        }

        public String toString() {
            return "TaskResource.TaskResourceBuilder(id=" + this.id + ", tenantId=" + this.tenantId + ", clientReferenceId=" + this.clientReferenceId + ", taskId=" + this.taskId + ", productVariantId=" + this.productVariantId + ", quantity=" + this.quantity + ", isDelivered=" + this.isDelivered + ", deliveryComment=" + this.deliveryComment + ", isDeleted=" + this.isDeleted + ", auditDetails=" + this.auditDetails + ")";
        }
    }
}
