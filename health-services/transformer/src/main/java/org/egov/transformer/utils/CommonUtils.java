package org.egov.transformer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.service.ProjectService;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
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
    private static Map<String, ProjectInfo> userIdVsProjectInfoCache = new ConcurrentHashMap<>();

    public CommonUtils(TransformerProperties properties, ObjectMapper objectMapper, ProjectService projectService) {
        this.properties = properties;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    public List<String> getProjectDatesList(Long startDateEpoch, Long endDateEpoch) {
        List<String> dates = new ArrayList<>();
        for (long timestamp = startDateEpoch; timestamp <= DAY_MILLIS + endDateEpoch; timestamp += DAY_MILLIS) {
            dates.add(getDateFromEpoch(timestamp));
        }
        return dates;
    }

    public String getDateFromEpoch(long epochTime) {
        String dateFromEpoch = "";
        String timeZone = properties.getTimeZone();
        try {
            Date date = new Date(epochTime);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            dateFormat.setTimeZone(java.util.TimeZone.getTimeZone(timeZone));
            dateFromEpoch = dateFormat.format(date);
        } catch (Exception e) {
            log.error("EpochTime to be transformed :" + epochTime);
            log.error("Exception while transforming epochTime to date: {}", ExceptionUtils.getStackTrace(e));
        }
        return dateFromEpoch;
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

    public List<Double> getGeoPointFromAdditionalDetails(JsonNode additionalDetails) {
        List<Double> geoPoint = null;
        if (additionalDetails != null && JsonNodeType.OBJECT.equals(additionalDetails.getNodeType())
                && additionalDetails.hasNonNull(LAT) && additionalDetails.hasNonNull(LNG)) {
            geoPoint = Arrays.asList(
                    additionalDetails.get(LNG).asDouble(),
                    additionalDetails.get(LAT).asDouble()
            );
        }
        return geoPoint;
    }

    public String getLocalityCodeFromAdditionalDetails(JsonNode additionalDetails) {
        String localityCode = null;
        if (additionalDetails != null && JsonNodeType.OBJECT.equals(additionalDetails.getNodeType()) && additionalDetails.hasNonNull(BOUNDARY_CODE_KEY)) {
            localityCode = additionalDetails.get(BOUNDARY_CODE_KEY).asText();
        } else if (additionalDetails != null && JsonNodeType.STRING.equals(additionalDetails.getNodeType())){
            localityCode = additionalDetails.asText();
        }
        return localityCode;
    }

    public Integer calculateAgeInMonthsFromDOB(Date birthDate) {
        Calendar currentDate = Calendar.getInstance();

        Calendar birthCalendar = Calendar.getInstance();
        birthCalendar.setTime(birthDate);

        // Calculate the difference in years, months, and days
        int years = currentDate.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR);
        int months = currentDate.get(Calendar.MONTH) - birthCalendar.get(Calendar.MONTH);

        // If the birth date hasn't occurred this year yet,
        // reduce the years
        if (months < 0) {
            years--;
            months += 12;
        }
        // Calculate the age in months
        return years * 12 + months;
    }

    public JsonNode getBoundaryHierarchy(String tenantId, String projectTypeId, Map<String, String> boundaryLabelToNameMap) {
        if (boundaryLabelToNameMap == null || boundaryLabelToNameMap.isEmpty()) {
            return null;
        }
        List<JsonNode> boundaryLevelVsLabel = null;
        ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
        try {
            String cacheKey = (projectTypeId != null) ? tenantId + HYPHEN + projectTypeId : tenantId;
            if (boundaryLevelVsLabelCache.containsKey(cacheKey)) {
                boundaryLevelVsLabel = boundaryLevelVsLabelCache.get(cacheKey);
                log.info("Fetching boundaryLevelVsLabel from cache for projectTypeId: {}", projectTypeId);
            } else {
                JsonNode mdmsBoundaryData = (projectTypeId != null) ? projectService.fetchBoundaryData(tenantId, null, projectTypeId) :
                        projectService.fetchBoundaryDataByTenant(tenantId, null);
                boundaryLevelVsLabel = StreamSupport
                        .stream(mdmsBoundaryData.get(BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
                boundaryLevelVsLabelCache.put(cacheKey, boundaryLevelVsLabel);
            }
        } catch (Exception e) {
            log.error("Error while fetching boundaryHierarchy for projectTypeId: {}, Error: {}", projectTypeId, ExceptionUtils.getStackTrace(e));
            log.info("RETURNING BOUNDARY_LABEL_TO_NAME_MAP as BOUNDARY_HIERARCHY: {}", boundaryLabelToNameMap);
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryDataByTenant(tenantId, null);
            if (mdmsBoundaryData != null && mdmsBoundaryData.has(BOUNDARY_HIERARCHY)) {
                boundaryLevelVsLabel = StreamSupport
                        .stream(mdmsBoundaryData.get(BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            }
        }
        if (boundaryLevelVsLabel == null) {
            return null;
        }
        boundaryLevelVsLabel.forEach(node -> {
            if (node.get(LEVEL).asInt() > 1) {
                boundaryHierarchy.put(node.get(INDEX_LABEL).asText(), boundaryLabelToNameMap.get(node.get(LABEL).asText()) == null ? null : boundaryLabelToNameMap.get(node.get(LABEL).asText()));
            }
        });
        return boundaryHierarchy;
    }

    //TODO move below cycle fetching logic to mdmsService
    public Integer fetchCycleIndex(String tenantId, String projectTypeId, AuditDetails auditDetails) {
        Long createdTime = auditDetails.getCreatedTime();
        JsonNode projectType = projectService.fetchProjectTypes(tenantId, null, projectTypeId);
        if (projectType.has(CYCLES)) {
            ArrayNode cycles = (ArrayNode) projectType.get(CYCLES);

            for (int i = 0; i < cycles.size(); i++) {
                JsonNode currentCycle = cycles.get(i);
                if (currentCycle.has(START_DATE) && currentCycle.has(END_DATE)) {
                    Long startDate = currentCycle.get(START_DATE).asLong();
                    Long endDate = currentCycle.get(END_DATE).asLong();
                    if (isWithinCycle(createdTime, startDate, endDate) || isBetweenCycles(createdTime, cycles, i)) {
                        return currentCycle.get(ID).asInt();
                    }
                }
            }
            return null;
        }
        return null;
    }

    private boolean isWithinCycle(Long createdTime, Long startDate, Long endDate) {
        log.info("createdTime is {}", createdTime);
        log.info("startDate is {} and endDate is {}", startDate, endDate);
        return createdTime >= startDate && createdTime <= endDate;
    }

    private boolean isBetweenCycles(Long createdTime, ArrayNode cycles, int currentIndex) {
        if (currentIndex < cycles.size() - 1) {
            JsonNode nextCycle = cycles.get(currentIndex + 1);
            if (nextCycle.has(START_DATE)) {
                Long nextStartDate = nextCycle.get(START_DATE).asLong();
                Long currentEndDate = cycles.get(currentIndex).get(END_DATE).asLong();
                log.info("nextStartDate is {} and currentEndDate is {}", nextStartDate, currentEndDate);
                return createdTime > currentEndDate && createdTime < nextStartDate;
            }
        }
        return false;
    }

    public ProjectInfo projectDetailsFromUserId(String userId, String tenantId){
        if (userIdVsProjectInfoCache.containsKey(userId)) {
            return userIdVsProjectInfoCache.get(userId);
        }

        List<String> userIds = new ArrayList<>(Arrays.asList(userId));
        ProjectInfo projectInfo = new ProjectInfo();
        List<ProjectStaff> projectStaffList = projectService.searchProjectStaff(userIds, tenantId);
        ProjectStaff projectStaff = !CollectionUtils.isEmpty(projectStaffList) ? projectStaffList.get(0) : null;

        if (ObjectUtils.isNotEmpty(projectStaff)) {
            Project project = projectService.getProject(projectStaff.getProjectId(), tenantId);
            if (ObjectUtils.isNotEmpty(project)) {
                projectInfo.setProjectTypeId(project.getProjectTypeId());
                projectInfo.setProjectId(projectStaff.getProjectId());
                projectInfo.setProjectType(project.getProjectType());
                projectInfo.setProjectName(project.getName());
                userIdVsProjectInfoCache.put(userId, projectInfo);
            }
        }

        return projectInfo;
    }

    public void addProjectDetailsForUserIdAndTenantId(ProjectInfo projectInfo, String userId, String tenantId) {
        ProjectInfo projectDetails = projectDetailsFromUserId(userId, tenantId);
        if(ObjectUtils.isNotEmpty(projectDetails)) {
            projectInfo.setProjectId(projectDetails.getProjectId());
            projectInfo.setProjectTypeId(projectDetails.getProjectTypeId());
            projectInfo.setProjectType(projectDetails.getProjectType());
            projectInfo.setProjectName(projectDetails.getProjectName());
        }
    }

//    public ObjectNode additionalFieldsToDetails(List<Object> fields) {
//        ObjectNode additionalDetails = objectMapper.createObjectNode();
//
//        try {
//            for (Object field : fields) {
//                Method getKey = field.getClass().getMethod("getKey");
//                Method getValue = field.getClass().getMethod("getValue");
//                String key = (String) getKey.invoke(field);
//                String value = (String) getValue.invoke(field);
//                additionalDetails.put(key, value);
//            }
//        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
//            log.info("Error in additionalDetails fetch from additionalFields : " + ExceptionUtils.getStackTrace(e));
//            return null;
//        }
//        return additionalDetails;
//    }
}
