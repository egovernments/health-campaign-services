package org.egov.common.models.referralmanagement.adverseevent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdverseEventBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("AdverseEvents")
    @NotNull
    @Valid
    private List<AdverseEvent> adverseEvents = new ArrayList<>();

    public AdverseEventBulkResponse addAdverseEventItem(AdverseEvent adverseEventItem) {
        this.adverseEvents.add(adverseEventItem);
        return this;
    }
}
