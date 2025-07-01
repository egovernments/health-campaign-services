package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

/**
 * BoundaryHierarchyDefinitionSearchCriteriaDefinition
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-10-16T17:02:11.361704+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryHierarchyDefinitonSearchRequest {
    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;

    @JsonProperty("BoundaryTypeHierarchySearchCriteria")
    private BoundaryHierarchyDefinitionSearchCriteria boundaryTypeHierarchySearchCriteria;
}
