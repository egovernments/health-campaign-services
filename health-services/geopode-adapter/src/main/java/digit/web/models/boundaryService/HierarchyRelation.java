package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * HierarchyRelation
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-10-16T17:02:11.361704+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HierarchyRelation {

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("hierarchyType")
    private String hierarchyType = null;

    @JsonProperty("boundary")
    @Valid
    private List<EnrichedBoundary> boundary = null;

}
