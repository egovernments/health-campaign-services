
package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.*;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoundaryHierarchyDefinitionResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("totalCount")
    private Integer totalCount;

    @JsonProperty("BoundaryHierarchy")
    @Valid
    private List<BoundaryHierarchy> boundaryHierarchy;
}
