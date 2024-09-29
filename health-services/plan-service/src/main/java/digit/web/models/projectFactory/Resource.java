package digit.web.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resource
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Resource {

    @JsonProperty("type")
    private String type;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("resourceId")
    private String resourceId;

    @JsonProperty("filestoreId")
    private String filestoreId;

    @JsonProperty("createResourceId")
    private String createResourceId;
}
