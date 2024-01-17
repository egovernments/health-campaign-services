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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class CommonUtils {

    private final TransformerProperties properties;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private static Map<String, List<JsonNode>> boundaryLevelVsLabelCache = new ConcurrentHashMap<>();

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
        List<JsonNode> boundaryLevelVsLabel = null;
        ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
        try {
            if (projectTypeId != null) {
                String cacheKey = tenantId + HYPHEN + projectTypeId;
                if (boundaryLevelVsLabelCache.containsKey(cacheKey)) {
                    boundaryLevelVsLabel = boundaryLevelVsLabelCache.get(tenantId + "-" + projectTypeId);
                    log.info("Fetching boundaryLevelVsLabel from cache for projectTypeId: {}", projectTypeId);
                } else {
                    JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, null, projectTypeId);
                    boundaryLevelVsLabel = StreamSupport
                            .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
                    boundaryLevelVsLabelCache.put(cacheKey, boundaryLevelVsLabel);
                }
            } else {
                boundaryLevelVsLabel = getDefaultBoundaryVsLabel();
            }
        } catch (Exception e) {
            log.error("Error while fetching boundaryHierarchy for projectTypeId: {}", projectTypeId);
            log.info("RETURNING BOUNDARY_LABEL_TO_NAME_MAP as BOUNDARY_HIERARCHY: {}", boundaryLabelToNameMap.toString());
            boundaryLevelVsLabel = getDefaultBoundaryVsLabel();
        }
        boundaryLevelVsLabel.forEach(node -> {
            if (node.get(LEVEL).asInt() > 1) {
                boundaryHierarchy.put(node.get(Constants.INDEX_LABEL).asText(), boundaryLabelToNameMap.get(node.get(LABEL).asText()) == null ? null : boundaryLabelToNameMap.get(node.get(LABEL).asText()));
            }
        });
        return boundaryHierarchy;
    }

    private static final List<JsonNode> DEFAULT_BOUNDARY_VS_LABEL;

    static {
        ObjectMapper tempObjectMapper = new ObjectMapper();
        List<JsonNode> tempBoundaryVsLabel = new ArrayList<>();
        tempBoundaryVsLabel.add(createBoundaryLevel(tempObjectMapper, 0, null, null));
        tempBoundaryVsLabel.add(createBoundaryLevel(tempObjectMapper, 1, "Country", "country"));
        tempBoundaryVsLabel.add(createBoundaryLevel(tempObjectMapper, 2, "Provincia", "province"));
        tempBoundaryVsLabel.add(createBoundaryLevel(tempObjectMapper, 3, "Distrito", "district"));
        tempBoundaryVsLabel.add(createBoundaryLevel(tempObjectMapper, 4, "Posto Administrativo", "administrativeProvince"));
        tempBoundaryVsLabel.add(createBoundaryLevel(tempObjectMapper, 5, "Localidade", "locality"));
        tempBoundaryVsLabel.add(createBoundaryLevel(tempObjectMapper, 6, "Aldeia", "village"));

        DEFAULT_BOUNDARY_VS_LABEL = Collections.unmodifiableList(tempBoundaryVsLabel);
    }

    private static JsonNode createBoundaryLevel(ObjectMapper mapper, int level, String label, String indexLabel) {
        ObjectNode boundaryLevel = mapper.createObjectNode();
        boundaryLevel.put(LEVEL, level);
        boundaryLevel.put(LABEL, label);
        boundaryLevel.put(INDEX_LABEL, indexLabel);
        return boundaryLevel;
    }

    public static List<JsonNode> getDefaultBoundaryVsLabel() {
        return DEFAULT_BOUNDARY_VS_LABEL;
    }

}
