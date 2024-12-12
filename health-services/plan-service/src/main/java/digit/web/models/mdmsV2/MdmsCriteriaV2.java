package digit.web.models.mdmsV2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Validated
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdmsCriteriaV2 {

    @JsonProperty("tenantId")
    @Size(min = 1, max = 100)
    @NotNull
    private String tenantId;

    @JsonProperty("ids")
    private Set<String> ids;

    @JsonProperty("uniqueIdentifier")
    @Size(min = 1, max = 64)
    private String uniqueIdentifier;

    @JsonProperty("uniqueIdentifiers")
    private List<String> uniqueIdentifiers;

    @JsonProperty("schemaCode")
    private String schemaCode;

    @JsonProperty("filters")
    private Map<String, String> filterMap;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonIgnore
    private Map<String, String> schemaCodeFilterMap;

    @JsonIgnore
    private Set<String> uniqueIdentifiersForRefVerification;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    private Integer limit;

}