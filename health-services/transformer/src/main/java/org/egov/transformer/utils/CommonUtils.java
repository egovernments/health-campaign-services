package org.egov.transformer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.service.ProjectService;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@Slf4j
@Component
public class CommonUtils {

    private final TransformerProperties properties;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public CommonUtils(TransformerProperties properties, ObjectMapper objectMapper, ProjectService projectService) {
        this.properties = properties;
        this.projectService = projectService;
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
            Method getLongitudeMethod = addressClass.getMethod("getLongitude");
            Method getLatitudeMethod = addressClass.getMethod("getLatitude");

            Double longitude = (Double) getLongitudeMethod.invoke(address);
            Double latitude = (Double) getLatitudeMethod.invoke(address);

            if (longitude == null || latitude == null) {
                return null;
            }
            List<Double> geoPoint = new ArrayList<>();
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

    public JsonNode getBoundaryHierarchy(String tenantId, String projectTypeId, Map<String, String> boundaryLabelToNameMap) {
        JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, null,projectTypeId);
        List<JsonNode> boundaryLevelVsLabel = StreamSupport
                .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
        ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
        boundaryLevelVsLabel.forEach(node -> {
            if (node.get(Constants.LEVEL).asInt() > 1) {
                boundaryHierarchy.put(node.get(Constants.INDEX_LABEL).asText(),boundaryLabelToNameMap.get(node.get(Constants.LABEL).asText()) == null ? null :  boundaryLabelToNameMap.get(node.get(Constants.LABEL).asText()));
            }
        });
        return boundaryHierarchy;
    }

}
