package digit.web.models;

import digit.models.coremodels.AuditDetails;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import digit.web.models.Assumption;
import digit.web.models.Operation;
import digit.web.models.ResourceMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * PlanConfiguration
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2024-03-04T09:55:29.782094600+05:30[Asia/Calcutta]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanConfiguration {
    @JsonProperty("id")

    @Valid
    private UUID id = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("name")
    @NotNull
    @Size(min = 2)
    private String name = null;

    @JsonProperty("executionPlanId")
    private String executionPlanId = null;

    @JsonProperty("files")
    @NotNull
    @Valid
    private List<File> files = new ArrayList<>();

    @JsonProperty("assumptions")
    @NotNull
    @Valid
    private List<Assumption> assumptions = new ArrayList<>();

    @JsonProperty("operations")
    @NotNull
    @Valid
    private List<Operation> operations = new ArrayList<>();

    @JsonProperty("resourceMapping")
    @NotNull
    @Valid
    private List<ResourceMapping> resourceMapping = new ArrayList<>();

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;

    public PlanConfiguration addFilesItem(File filesItem) {
        this.files.add(filesItem);
        return this;
    }

    public PlanConfiguration addAssumptionsItem(Assumption assumptionsItem) {
        this.assumptions.add(assumptionsItem);
        return this;
    }

    public PlanConfiguration addOperationsItem(Operation operationsItem) {
        this.operations.add(operationsItem);
        return this;
    }

    public PlanConfiguration addResourceMappingItem(ResourceMapping resourceMappingItem) {
        this.resourceMapping.add(resourceMappingItem);
        return this;
    }

}
