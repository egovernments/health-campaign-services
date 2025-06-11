package digit.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeopdeHierarchyLevel {

    @JsonProperty("feature")
    private String feature;

    @JsonProperty("type_code")
    private String typeCode;

    @JsonProperty("level")
    private Integer level;

    @JsonProperty("parent")
    private String parent;
}
