package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * Assumption
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Assumption {
    @JsonProperty("id")
    @Valid
    private UUID id = null;

    @JsonProperty("key")
    @NotNull
    @Size(min = 1, max = 32)
    private String key = null;

    @JsonProperty("value")
    @NotNull
    @Valid
    private BigDecimal value = null;

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;


}
