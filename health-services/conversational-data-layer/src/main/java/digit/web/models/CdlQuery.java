package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CdlQuery {

    @JsonProperty("queryText")
    private String queryText;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("indexName")
    private String indexName;
}
