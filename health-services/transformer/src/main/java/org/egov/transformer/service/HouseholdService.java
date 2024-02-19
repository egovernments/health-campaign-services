package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.*;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.downstream.HouseholdIndexV1;
import org.egov.transformer.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HouseholdService {
    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private final Producer producer;
    private final ObjectMapper objectMapper;

    public HouseholdService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, Producer producer, ObjectMapper objectMapper) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    public void transform(List<Household> payloadList) {
        String topic = transformerProperties.getTransformerProducerBulkHouseholdIndexV1Topic();
        log.info("transforming for ids {}", payloadList.stream()
                .map(Household::getId).collect(Collectors.toList()));
        List<HouseholdIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        if (!transformedPayloadList.isEmpty()) {
            producer.push(topic, transformedPayloadList);
            log.info("transformation successful");
        }
    }

    public HouseholdIndexV1 transform(Household household) {
        return HouseholdIndexV1.builder()
                .household(household)
                .geoPoint(getGeoPoint(household.getAddress()))
                .build();
    }

    public List<Double> getGeoPoint(Address address) {
        if (address == null || (address.getLongitude() == null && address.getLatitude() == null)) {
            return null;
        }
        List<Double> geoPoints = new ArrayList<>();
        geoPoints.add(address.getLongitude());
        geoPoints.add(address.getLatitude());
        return geoPoints;
    }

}
