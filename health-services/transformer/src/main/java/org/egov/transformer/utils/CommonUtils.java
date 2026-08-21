package org.egov.transformer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.service.MdmsService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class CommonUtils {

    private final TransformerProperties properties;
    private final ObjectMapper objectMapper;
    private final MdmsService mdmsService;

    // DateTimeFormatter is immutable and thread safe, unlike SimpleDateFormat, so these are built once
    // and shared across every consumer thread instead of being allocated per call.
    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private DateTimeFormatter dateFormatter;
    private DateTimeFormatter timeStampFormatter;

    public CommonUtils(TransformerProperties properties, ObjectMapper objectMapper, MdmsService mdmsService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mdmsService = mdmsService;
    }

    /**
     * Binds the configured time zone into the formatters once, at startup, rather than resolving it on
     * every call. Surrounding quotes are tolerated because the property is quoted in some environments.
     */
    @PostConstruct
    void initDateFormatters() {
        String configuredTimeZone = properties.getTimeZone();
        String sanitizedTimeZone = configuredTimeZone == null ? "" : configuredTimeZone.replace("\"", "").trim();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(sanitizedTimeZone);
        } catch (Exception e) {
            // Matches the previous behaviour: TimeZone.getTimeZone silently fell back to GMT here.
            log.error("Invalid configured timeZone: {}. Falling back to UTC.", configuredTimeZone);
            zoneId = ZoneOffset.UTC;
        }
        if (!sanitizedTimeZone.equals(configuredTimeZone)) {
            log.warn("Configured timeZone {} contained quotes or whitespace. Resolved to zone: {}", configuredTimeZone, zoneId);
        }
        dateFormatter = DATE_PATTERN.withZone(zoneId);
        timeStampFormatter = TIMESTAMP_PATTERN.withZone(zoneId);
        log.info("Date formatters initialised with zone: {}", zoneId);
    }

    public List<String> getProjectDatesList(Long startDateEpoch, Long endDateEpoch) {
        List<String> dates = new ArrayList<>();
        for (long timestamp = startDateEpoch; timestamp <= DAY_MILLIS + endDateEpoch; timestamp += DAY_MILLIS) {
            dates.add(getDateFromEpoch(timestamp));
        }
        return dates;
    }

    public String getDateFromEpoch(long epochTime) {
        try {
            return dateFormatter.format(Instant.ofEpochMilli(epochTime));
        } catch (Exception e) {
            log.error("EpochTime to be transformed :" + epochTime);
            log.error("Exception while transforming epochTime to date: {}", ExceptionUtils.getStackTrace(e));
            return "";
        }
    }

    public String getTimeStampFromEpoch(long epochTime) {
        try {
            return timeStampFormatter.format(Instant.ofEpochMilli(epochTime));
        } catch (Exception e) {
            log.error("EpochTime to be transformed :" + epochTime);
            log.error("Exception while transforming epochTime to timestamp: {}", ExceptionUtils.getStackTrace(e));
            return "";
        }
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

    public List<Double> getGeoPointFromAdditionalFields(JsonNode additionalFields, JsonNode additionalDetails) {
        if (additionalFields != null && JsonNodeType.OBJECT.equals(additionalFields.getNodeType()) && additionalFields.has(ADDITIONAL_FIELDS_FIELDS_KEY)) {
            JsonNode additionalFieldsMap = convertAdditionalFieldsToMap(additionalFields);
            if(additionalFieldsMap != null) additionalDetails = additionalFieldsMap;
        }
        if (additionalDetails != null && JsonNodeType.OBJECT.equals(additionalDetails.getNodeType())
                && additionalDetails.hasNonNull(LAT) && additionalDetails.hasNonNull(LNG)) {
            return Arrays.asList(
                    additionalDetails.get(LNG).asDouble(),
                    additionalDetails.get(LAT).asDouble()
            );
        }
        return null;
    }

    public String getLocalityCodeFromAdditionalFields(JsonNode additionalFields, JsonNode additionalDetails) {
        if (additionalFields != null && JsonNodeType.OBJECT.equals(additionalFields.getNodeType()) && additionalFields.hasNonNull(ADDITIONAL_FIELDS_FIELDS_KEY)) {
            JsonNode additionalFieldsMap = convertAdditionalFieldsToMap(additionalFields);
            if(additionalFieldsMap != null) additionalDetails = additionalFieldsMap;
        }
        if (additionalDetails != null && JsonNodeType.OBJECT.equals(additionalDetails.getNodeType()) && additionalDetails.hasNonNull(BOUNDARY_CODE_KEY)) {
            return additionalDetails.get(BOUNDARY_CODE_KEY).asText();
        }
        if (additionalDetails != null && JsonNodeType.STRING.equals(additionalDetails.getNodeType())){
            return additionalDetails.asText();
        }
        return null;
    }

    public String getLocalityCodeFromAdditionalFields(Object additionalFields) {
        if(additionalFields == null) return null;
        JsonNode node = objectMapper.valueToTree(additionalFields);
        return getLocalityCodeFromAdditionalFields(node, null);
    }

    public JsonNode convertAdditionalFieldsToMap(Object additionalFields) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        JsonNode node = objectMapper.valueToTree(additionalFields);
        ArrayNode fields = (ArrayNode) node.get(ADDITIONAL_FIELDS_FIELDS_KEY);
        fields.spliterator().forEachRemaining(field -> {
            data.set(
                    field.get(ADDITIONAL_FIELDS_FIELDS_KEY_KEY).asText(),
                    field.get(ADDITIONAL_FIELDS_FIELDS_VALUE_KEY)
            );
        });
        return data;
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

    public String fetchCycleIndexFromTime(String tenantId, String projectTypeId, Long createdTime) {
        JsonNode projectType = mdmsService.fetchProjectTypes(tenantId, null, projectTypeId);
        if (projectType.has(CYCLES)) {
            ArrayNode cycles = (ArrayNode) projectType.get(CYCLES);
            return findCycleIndex(cycles, createdTime);
        }
        return null;
    }

    public String findCycleIndex(ArrayNode cycles, Long createdTime) {
        if (cycles == null || cycles.isEmpty()) {
            return null;
        }

        for (int i = 0; i < cycles.size(); i++) {
            JsonNode cycle = cycles.get(i);
            long start = cycle.get(START_DATE).asLong(0);
            long end = cycle.get(END_DATE).asLong(0);

            if (isWithinCycle(createdTime, start, end) || isBetweenCycles(createdTime, cycles, i)) {
                return String.format("%02d", cycle.path(ID).asInt(0));
            }
        }

        JsonNode earliestCycle = cycles.get(0);
        long earliestStart = earliestCycle.get(START_DATE).asLong(0);
        if (createdTime < earliestStart) {
            log.info("createdTime {} is before first cycle startDate {}, assigning earliest cycleIndex", createdTime, earliestStart);
            return String.format("%02d", earliestCycle.path(ID).asInt(0));
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
                log.info("nextStartDate is {} and currentEndDate is {}", nextStartDate, currentEndDate);
                return createdTime > currentEndDate && createdTime < nextStartDate;
            }
        }
        return false;
    }

}
