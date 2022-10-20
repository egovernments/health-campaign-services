package org.digit.health.delivery.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.delivery.utils.ModelMapper;
import org.digit.health.delivery.web.models.request.ResourceDeliveryRequest;
import org.digit.health.delivery.web.models.response.DeliveryResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;
import java.util.List;

@Controller
@Slf4j
@RequestMapping("/delivery/v1")
public class DeliveryController {

    @PostMapping("/_create")
    public ResponseEntity<DeliveryResponse> create(@RequestBody @Valid List<ResourceDeliveryRequest>
                                                               deliveryRequests) {
        log.info("Delivery request {}", deliveryRequests);
        ResourceDeliveryRequest deliveryRequest = deliveryRequests.get(0);
        if (deliveryRequest.getDelivery().getDeliveryId().equals("error")) {
            throw new CustomException("ERROR_IN_DELIVERY", "Dummy error");
        }
        return ResponseEntity.ok().body(DeliveryResponse.builder()
                .responseInfo(ModelMapper
                        .createResponseInfoFromRequestInfo(deliveryRequest
                                .getRequestInfo(), true))
                .deliveryId(deliveryRequest.getDelivery().getDeliveryId()).build());
    }
}
