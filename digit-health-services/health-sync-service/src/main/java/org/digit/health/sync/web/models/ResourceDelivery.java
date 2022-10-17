package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceDelivery extends CampaignData {

    @JsonProperty("requestInfo")
    private RequestInfo requestInfo;

    @JsonProperty("delivery")
    private Delivery delivery;
}
