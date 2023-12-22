package org.egov.transformer.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Address;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CommonUtils {

    private final TransformerProperties properties;
    private final ObjectMapper objectMapper;

    public CommonUtils(TransformerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String getTimeStampFromEpoch(long epochTime) {
        String timeStamp = "";
        String timeZone = properties.getTimeZone();
        try {
            Date date = new Date(epochTime);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(java.util.TimeZone.getTimeZone(timeZone));
            timeStamp = dateFormat.format(date);
        } catch (Exception e) {
            log.error("EpochTime to be transformed :" + epochTime);
            log.error("Exception while transforming epochTime to timestamp: ", e);
        }
        return timeStamp;
    }

    public List<Double> getGeoPoints(Address address) {
        if (address == null) {
            log.error("Address is null");
            throw new CustomException("GEO_POINT_FETCH_ERROR", "error in getting geo-points");
        }
        List<Double> geoPoints = new ArrayList<>();
        geoPoints.add(address.getLongitude());
        geoPoints.add(address.getLatitude());
        return geoPoints;
    }

}
