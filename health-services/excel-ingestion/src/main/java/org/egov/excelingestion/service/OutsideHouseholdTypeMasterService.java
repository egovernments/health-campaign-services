package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads which facility usages are sites (e.g. Outside Household Places, Transit Posts) from the MDMS master
 * {@link ProcessingConstants#OUTSIDE_HOUSEHOLD_TYPES_SCHEMA_CODE} (HCM.OUTSIDE_HOUSEHOLD_TYPES),
 * read at the state tenant, and decides whether the Outside Household sheet applies to a given campaign.
 *
 * <p>An MDMS reply with no active rows is read as NO site types (the feature is simply off for that
 * tenant); only an actual failed MDMS call is an error ({@link ErrorConstants#MDMS_SERVICE_ERROR}).
 */
@Service
@Slf4j
public class OutsideHouseholdTypeMasterService {

    // Fetch limit for the master: a small, bounded list (OUTSIDE_HOUSEHOLD_PLACES/TRANSIT_POST today), not paginated in practice.
    private static final int FETCH_LIMIT = 200;

    private static final String CYCLE_DATA_DELIVERY_METHODS_KEY = "deliveryMethods";
    private static final String CODE_KEY = "code";

    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final CustomExceptionHandler exceptionHandler;
    private final ExcelIngestionConfig config;

    public OutsideHouseholdTypeMasterService(MDMSService mdmsService, CampaignService campaignService,
                                 CustomExceptionHandler exceptionHandler, ExcelIngestionConfig config) {
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.exceptionHandler = exceptionHandler;
        this.config = config;
    }

    /**
     * Active site types of the master (normalized code -> isPermanentByDefault), read at the state tenant.
     * Empty (never null) when the master has no active rows, including when the schema code is not
     * configured at all. Fails with {@link ErrorConstants#MDMS_SERVICE_ERROR} only when the MDMS call
     * itself fails.
     */
    public Map<String, Boolean> getOutsideHouseholdTypes(RequestInfo requestInfo, String tenantId) {
        return extractActiveOutsideHouseholdTypes(fetchMasterRows(requestInfo, tenantId));
    }

    /**
     * Category codes allowed per active site type (normalized type code -> its "categories" list, in master
     * order). A type without a categories list maps to an empty list, which means "any category".
     */
    public Map<String, List<String>> getCategoriesByOutsideHouseholdType(RequestInfo requestInfo, String tenantId) {
        return extractCategoriesByOutsideHouseholdType(fetchMasterRows(requestInfo, tenantId));
    }

    private List<Map<String, Object>> fetchMasterRows(RequestInfo requestInfo, String tenantId) {
        String stateTenantId = stateTenantId(tenantId);
        try {
            return mdmsService.searchMDMS(requestInfo, stateTenantId,
                    ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPES_SCHEMA_CODE, null, FETCH_LIMIT, 0);
        } catch (Exception e) {
            log.error("Error reading {} for tenant {}: {}",
                    ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPES_SCHEMA_CODE, stateTenantId, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR,
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
            return List.of(); // never reached
        }
    }

    /**
     * Whether a configured sheet applies: every sheet except the Outside Household sheet always does; the Outside Household sheet
     * only when the campaign type is one of excel.ingestion.outside.household.campaign.types (POLIO by default), the
     * campaign uses the outside-household delivery method AND the site-types master has at least one
     * active code. False when there is no campaign to check (blank referenceId or an
     * attendance-register reference).
     */
    public boolean isSheetApplicable(String sheetNameKey, String referenceId, String referenceType,
                                     String tenantId, RequestInfo requestInfo) {
        return isSheetApplicable(sheetNameKey, referenceId, referenceType, tenantId, requestInfo, null);
    }

    /**
     * Same rule, for a generate request: when its additionalDetails carry the campaign's deliveryMethods / projectType
     * (project-factory sends those of the update that triggered the generation), they win over the stored campaign, which
     * may not have that update yet. Without them the stored campaign decides, as before.
     */
    public boolean isSheetApplicable(String sheetNameKey, String referenceId, String referenceType,
                                     String tenantId, RequestInfo requestInfo, Map<String, Object> requestDetails) {
        if (!ProcessingConstants.OUTSIDE_HOUSEHOLD_SHEET_NAME_KEY.equals(sheetNameKey)) {
            return true;
        }
        if (referenceId == null || referenceId.trim().isEmpty()
                || ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER.equals(referenceType)) {
            return false;
        }
        Object requestedMethods = requestDetails == null ? null : requestDetails.get(ProcessingConstants.GENERATE_DETAIL_DELIVERY_METHODS_KEY);
        Object requestedType = requestDetails == null ? null : requestDetails.get(ProcessingConstants.GENERATE_DETAIL_PROJECT_TYPE_KEY);
        String projectType = requestedType instanceof String ? normalize((String) requestedType) : null;
        CampaignSearchResponse.CampaignDetail campaign = requestedMethods instanceof List && projectType != null
                ? null : campaignService.searchCampaignById(referenceId, tenantId, requestInfo);
        if (projectType == null && campaign != null) {
            projectType = normalize(campaign.getProjectType());
        }
        Set<String> methods = requestedMethods instanceof List
                ? deliveryMethodCodes((List<?>) requestedMethods) : extractDeliveryMethods(campaign);
        return isOutsideHouseholdSitesCampaignType(projectType)
                && methods.contains(ProcessingConstants.OUTSIDE_HOUSEHOLD_DELIVERY_METHOD)
                && !getOutsideHouseholdTypes(requestInfo, tenantId).isEmpty();
    }

    private boolean isOutsideHouseholdSitesCampaignType(String projectType) {
        List<String> types = config.getOutsideHouseholdCampaignTypes();
        return projectType != null && types != null
                && types.stream().map(OutsideHouseholdTypeMasterService::normalize).anyMatch(projectType::equals);
    }

    /** Upper-cases and trims; null for a null or blank code. Shared lookup key for every caller. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Boolean> extractActiveOutsideHouseholdTypes(List<Map<String, Object>> rows) {
        Map<String, Boolean> outsideHouseholdTypes = new LinkedHashMap<>();
        if (rows == null) {
            return outsideHouseholdTypes;
        }
        for (Map<String, Object> row : rows) {
            Object data = row.get("data");
            if (!(data instanceof Map)) {
                continue;
            }
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object active = dataMap.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_ACTIVE_FIELD);
            Object code = dataMap.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_CODE_FIELD);
            String normalizedCode = code instanceof String ? normalize((String) code) : null;
            if (Boolean.TRUE.equals(active) && normalizedCode != null) {
                Object isPermanentByDefault = dataMap.get(
                        ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_IS_PERMANENT_BY_DEFAULT_FIELD);
                outsideHouseholdTypes.put(normalizedCode, Boolean.TRUE.equals(isPermanentByDefault));
            }
        }
        return outsideHouseholdTypes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> extractCategoriesByOutsideHouseholdType(List<Map<String, Object>> rows) {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        if (rows == null) {
            return categories;
        }
        for (Map<String, Object> row : rows) {
            Object data = row.get("data");
            if (!(data instanceof Map)) {
                continue;
            }
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object code = dataMap.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_CODE_FIELD);
            String normalizedCode = code instanceof String ? normalize((String) code) : null;
            if (!Boolean.TRUE.equals(dataMap.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_ACTIVE_FIELD))
                    || normalizedCode == null) {
                continue;
            }
            List<String> codes = new ArrayList<>();
            Object list = dataMap.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_CATEGORIES_FIELD);
            if (list instanceof List) {
                for (Object category : (List<Object>) list) {
                    String normalizedCategory = category instanceof String ? normalize((String) category) : null;
                    if (normalizedCategory != null) {
                        codes.add(normalizedCategory);
                    }
                }
            }
            categories.put(normalizedCode, codes);
        }
        return categories;
    }

    /** Root/state tenant of a dotted tenantId (e.g. "mz.citya" -> "mz"); unchanged if there is no dot. */
    private static String stateTenantId(String tenantId) {
        return (tenantId != null && tenantId.contains(".")) ? tenantId.split("\\.")[0] : tenantId;
    }

    /** Normalized codes of additionalDetails.cycleData.deliveryMethods (codes or {code} objects), as the console saves them. */
    private static Set<String> extractDeliveryMethods(CampaignSearchResponse.CampaignDetail campaign) {
        Object cycleData = campaign == null || campaign.getAdditionalDetails() == null
                ? null : campaign.getAdditionalDetails().getCycleData();
        Object methods = cycleData instanceof Map ? ((Map<?, ?>) cycleData).get(CYCLE_DATA_DELIVERY_METHODS_KEY) : null;
        return methods instanceof List ? deliveryMethodCodes((List<?>) methods) : new HashSet<>();
    }

    /** Normalized codes of a deliveryMethods list (codes or {code} objects). */
    private static Set<String> deliveryMethodCodes(List<?> methods) {
        Set<String> codes = new HashSet<>();
        for (Object method : methods) {
            Object code = method instanceof Map ? ((Map<?, ?>) method).get(CODE_KEY) : method;
            String normalized = code instanceof String ? normalizeDeliveryMethod((String) code) : null;
            if (normalized != null) {
                codes.add(normalized);
            }
        }
        return codes;
    }

    /** Delivery-method codes are compared upper-cased with non-alphanumerics removed (unlike site-type codes). */
    private static String normalizeDeliveryMethod(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
