package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Boundary
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Boundary {

    @JsonProperty("code")
    private String code;

    @JsonProperty("type")
    private String type;

    @JsonProperty("isRoot")
    private Boolean isRoot;

    @JsonProperty("includeAllChildren")
    private Boolean includeAllChildren;

    @JsonProperty("parent")
    private String parent;
}