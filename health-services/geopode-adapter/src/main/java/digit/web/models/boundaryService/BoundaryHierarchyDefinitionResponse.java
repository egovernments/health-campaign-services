
package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoundaryHierarchyDefinitionResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("totalCount")
    private Integer totalCount;

    @JsonProperty("BoundaryHierarchy")
    private List<BoundaryHierarchy> boundaryHierarchy;
}
