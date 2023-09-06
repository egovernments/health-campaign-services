package org.egov.common.models.project.adverseevent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdverseEventBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("AdverseEvents")
    @NotNull
    @Valid
    @Size(min=1)
    private List<AdverseEvent> adverseEvents = new ArrayList<>();

    public AdverseEventBulkRequest addAdverseEventItem(AdverseEvent adverseEventItem) {
        this.adverseEvents.add(adverseEventItem);
        return this;
    }

}
