package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryRelationshipSearchCriteria {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("includeChildren")
    private Boolean includeChildren;

    @JsonProperty("includeParents")
    private Boolean includeParents;

    @JsonProperty("codes")
    private List<String> codes;
}
