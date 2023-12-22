package org.egov.transformer.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Address;
import org.egov.transformer.config.TransformerProperties;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
