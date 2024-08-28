package digit.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * PlanConfiguration
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanConfiguration {
    @JsonProperty("id")
    @Valid
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("name")
    @NotNull
    @Size(min = 3, max = 128)
    private String name = null;

    @JsonProperty("executionPlanId")
    @NotNull
    @Size(min = 2, max = 64)
    @Pattern(regexp = "^(?!\\p{Punct}+$).*$", message = "Execution Plan Id must not contain only special characters")
    private String executionPlanId = null;

    @JsonProperty("status")
    @NotNull
    private StatusEnum status = null;

    @JsonProperty("files")
    @NotNull
    @NotEmpty
    @Valid
    private List<File> files = new ArrayList<>();

    @JsonProperty("assumptions")
    @NotNull
    @NotEmpty
    @Valid
    private List<Assumption> assumptions = new ArrayList<>();

    @JsonProperty("operations")
    @NotNull
    @NotEmpty
    @Valid
    private List<Operation> operations = new ArrayList<>();

    @JsonProperty("resourceMapping")
    @NotNull
    @NotEmpty
    @Valid
    private List<ResourceMapping> resourceMapping = new ArrayList<>();

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;

    /**
     * The status used in the Plan Configuration
     */
    public enum StatusEnum {
            DRAFT ,
            GENERATED,
            INVALID_DATA
    }

}
