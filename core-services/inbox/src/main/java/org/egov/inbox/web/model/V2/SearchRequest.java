package org.egov.inbox.web.model.V2;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.egov.common.contract.request.RequestInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SearchRequest {

    @JsonProperty("RequestInfo")
    private RequestInfo RequestInfo;

    @JsonProperty("SearchCriteria")
    private IndexSearchCriteria indexSearchCriteria;
}
