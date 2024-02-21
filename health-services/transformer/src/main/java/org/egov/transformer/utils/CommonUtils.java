package org.egov.transformer.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import digit.models.coremodels.mdms.*;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.service.MdmsService;
import org.egov.transformer.service.ProjectService;
import org.springframework.stereotype.Component;

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
    private final MdmsService mdmsService;
    private static Map<String, List<JsonNode>> boundaryLevelVsLabelCache = new ConcurrentHashMap<>();

    private static Map<String, String> transformerLocalizations = new HashMap<>();

    private static Map<String, JSONArray> projectStaffRolesCache = new ConcurrentHashMap<>();

    public CommonUtils(TransformerProperties properties, ObjectMapper objectMapper, ProjectService projectService, MdmsService mdmsService) {
        this.properties = properties;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.mdmsService = mdmsService;
    }

    public  String getMDMSTransformerLocalizations (String text, String tenantId) {
        if (transformerLocalizations.containsKey(text)) {
            log.info("Fetching localization from transformerLocalization: {}", text);
            return transformerLocalizations.get(text);
        }
        return fetchLocalizationsFromMdms(text, tenantId);
    }
    public List<String> getProjectDatesList (Long startDateEpoch, Long endDateEpoch) {
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
                        .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
                boundaryLevelVsLabelCache.put(cacheKey, boundaryLevelVsLabel);
            }
        } catch (Exception e) {
            log.error("Error while fetching boundaryHierarchy for projectTypeId: {}, Error: {}", projectTypeId, ExceptionUtils.getStackTrace(e));
            log.info("RETURNING BOUNDARY_LABEL_TO_NAME_MAP as BOUNDARY_HIERARCHY: {}", boundaryLabelToNameMap.toString());
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryDataByTenant(tenantId, null);
            if (mdmsBoundaryData != null && mdmsBoundaryData.has(Constants.BOUNDARY_HIERARCHY)) {
                boundaryLevelVsLabel = StreamSupport
                        .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            }
        }
        if (boundaryLevelVsLabel == null) {
            return null;
        }
        boundaryLevelVsLabel.forEach(node -> {
            if (node.get(LEVEL).asInt() > 1) {
                boundaryHierarchy.put(node.get(Constants.INDEX_LABEL).asText(), boundaryLabelToNameMap.get(node.get(LABEL).asText()) == null ? null : boundaryLabelToNameMap.get(node.get(LABEL).asText()));
            }
        });
        return boundaryHierarchy;
    }


    private String fetchLocalizationsFromMdms(String text, String tenantId) {
        JSONArray transformerLocalizationsArray = new JSONArray();

        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, TRANSFORMER_LOCALIZATIONS, properties.getTransformerLocalizationsMdmsModule(), "");
        try {
            MdmsResponse mdmsResponse = mdmsService.fetchConfig(mdmsCriteriaReq, MdmsResponse.class);
            transformerLocalizationsArray = mdmsResponse.getMdmsRes().get(properties.getTransformerLocalizationsMdmsModule())
                    .get(TRANSFORMER_LOCALIZATIONS);
            ObjectMapper objectMapper = new ObjectMapper();
            transformerLocalizationsArray.forEach(item -> {
                Map map = objectMapper.convertValue(item, new TypeReference<Map>() {
                });
                transformerLocalizations.put((String) map.get("text"), (String) map.get("translatedText"));
            });
        } catch (Exception e) {
            log.error("error while fetching TRANFORMER_LOCALIZATIONS from MDMS: {}", ExceptionUtils.getStackTrace(e));
        }
        return transformerLocalizations.getOrDefault(text, text);
    }
    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, String masterName,
                                           String moduleName, String filter) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        if (filter != null && !filter.isEmpty()) {
            masterDetail.setFilter(filter);
        }
        List<MasterDetail> masterDetailList = new ArrayList<>();
        masterDetailList.add(masterDetail);
        ModuleDetail moduleDetail = new ModuleDetail();
        moduleDetail.setMasterDetails(masterDetailList);
        moduleDetail.setModuleName(moduleName);
        List<ModuleDetail> moduleDetailList = new ArrayList<>();
        moduleDetailList.add(moduleDetail);
        MdmsCriteria mdmsCriteria = new MdmsCriteria();
        mdmsCriteria.setTenantId(tenantId.split("\\.")[0]);
        mdmsCriteria.setModuleDetails(moduleDetailList);
        MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
        mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
        mdmsCriteriaReq.setRequestInfo(requestInfo);
        return mdmsCriteriaReq;
    }

    public HashMap<String, Integer> getProjectStaffRoles(String tenantId) {
        String moduleName = properties.getProjectStaffRolesMdmsModule();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, PROJECT_STAFF_ROLES, moduleName, null);
        JSONArray projectStaffRoles = new JSONArray();
        try {
            String cacheKey = tenantId;
            if (projectStaffRolesCache.containsKey(cacheKey)) {
                projectStaffRoles = projectStaffRolesCache.get(cacheKey);
                log.info("Fetching projectStaffRoles from cache for tenantId: {}", tenantId);
            }
            else {
                MdmsResponse mdmsResponse = mdmsService.fetchConfig(mdmsCriteriaReq, MdmsResponse.class);
                projectStaffRoles = mdmsResponse.getMdmsRes().get(moduleName).get(PROJECT_STAFF_ROLES);
                projectStaffRolesCache.put(cacheKey, projectStaffRoles);
            }
        } catch (Exception e) {
            log.error("Exception while fetching mdms roles: {}", ExceptionUtils.getStackTrace(e));
        }
        ObjectMapper objectMapper = new ObjectMapper();
        HashMap<String, Integer> projectStaffRolesMap = new HashMap<>();
        projectStaffRoles.forEach(role -> {
            LinkedHashMap<String, Object> map = objectMapper.convertValue(role, new TypeReference<LinkedHashMap>() {
            });
            projectStaffRolesMap.put((String) map.get("code"), (Integer) map.get("rank"));
        });
        return projectStaffRolesMap;
    }

    public Integer fetchCycleIndex(String tenantId, String projectTypeId, AuditDetails auditDetails) {
        Long createdTime = auditDetails.getCreatedTime();
        JsonNode projectType = projectService.fetchProjectTypes(tenantId, null, projectTypeId);
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

    private boolean isWithinCycle(Long createdTime, Long startDate, Long endDate) {
        return createdTime >= startDate && createdTime <= endDate;
    }

    private boolean isBetweenCycles(Long createdTime, ArrayNode cycles, int currentIndex) {
        if (currentIndex < cycles.size() - 1) {
            JsonNode nextCycle = cycles.get(currentIndex + 1);
            if (nextCycle.has(START_DATE)) {
                Long nextStartDate = nextCycle.get(START_DATE).asLong();
                Long currentEndDate = cycles.get(currentIndex).get(END_DATE).asLong();
                return createdTime > currentEndDate && createdTime < nextStartDate;
            }
        }
        return false;
    }

}
