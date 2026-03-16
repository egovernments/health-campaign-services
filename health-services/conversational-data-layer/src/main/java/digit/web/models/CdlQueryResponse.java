package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CdlQueryResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("data")
    private Map<String, Object> data;

    @JsonProperty("generatedQuery")
    private Map<String, Object> generatedQuery;

    @JsonProperty("totalHits")
    private Long totalHits;

    @JsonProperty("queryTimeMs")
    private Long queryTimeMs;

    @JsonProperty("sanitizedQueryText")
    private String sanitizedQueryText;

    @JsonProperty("selectedIndex")
    private String selectedIndex;
}
