package org.egov.transformer.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Duration;
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
            log.error("Exception while transforming epochTime to timestamp: {}", ExceptionUtils.getStackTrace(e));
        }
        return timeStamp;
    }

    public List<Double> getGeoPoint(Object address) {
        if (address == null) {
            return null;
        }
        try {
            Class<?> addressClass = address.getClass();
            // get the methods required to read values from address object
            Method getLongitudeMethod = addressClass.getMethod("getLongitude");
            Method getLatitudeMethod = addressClass.getMethod("getLatitude");

            //invoke methods to read values
            Double longitude = (Double) getLongitudeMethod.invoke(address);
            Double latitude = (Double) getLatitudeMethod.invoke(address);

            // Check if either longitude or latitude is null
            if (longitude == null || latitude == null) {
                return null;
            }
            List<Double> geoPoint = new ArrayList<>();
            // set the values and return the same
            geoPoint.add(longitude);
            geoPoint.add(latitude);
            return geoPoint;

        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("ERROR_IN_GEO_POINT_EXTRACTION : " + e);
            return null;
        }
    }

    public Integer calculateAgeInMonthsFromDOB(Date dob) {
        Duration difference = Duration.between(dob.toInstant(), new Date().toInstant());
        long totalDays = difference.toDays();
        return (int) (totalDays / 30.42);
    }

}
