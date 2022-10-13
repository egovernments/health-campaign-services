package org.digit.health.delivery.web.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.delivery.web.models.Delivery;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Validated
public class DeliveryRequest {
    @JsonProperty("requestInfo")
    private RequestInfo requestInfo;

    @JsonProperty("delivery")
    private Delivery delivery;
}
