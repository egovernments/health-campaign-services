package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads which facility usages are posts (e.g. CLF, transit post) from the MDMS master
 * {@link ProcessingConstants#OUTSIDE_HOUSEHOLD_POST_TYPES_SCHEMA_CODE} (HCM.OUTSIDE_HOUSEHOLD_POST_TYPES),
 * read at the state tenant, and decides whether the posts sheet applies to a given campaign.
 *
 * <p>An MDMS reply with no active rows is read as NO post types (the feature is simply off for that
 * tenant); only an actual failed MDMS call is an error ({@link ErrorConstants#MDMS_SERVICE_ERROR}).
 */
@Service
@Slf4j
public class PostTypeMasterService {

    // Fetch limit for the master: a small, bounded list (CLF/TRANSIT_POST today), not paginated in practice.
    private static final int FETCH_LIMIT = 200;

    private static final String CYCLE_DATA_DELIVERY_METHODS_KEY = "deliveryMethods";
    private static final String CODE_KEY = "code";

    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final CustomExceptionHandler exceptionHandler;
    private final ExcelIngestionConfig config;

    public PostTypeMasterService(MDMSService mdmsService, CampaignService campaignService,
                                 CustomExceptionHandler exceptionHandler, ExcelIngestionConfig config) {
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.exceptionHandler = exceptionHandler;
        this.config = config;
    }

    /**
     * Active post types of the master (normalized code -> isPermanentByDefault), read at the state tenant.
     * Empty (never null) when the master has no active rows, including when the schema code is not
     * configured at all. Fails with {@link ErrorConstants#MDMS_SERVICE_ERROR} only when the MDMS call
     * itself fails.
     */
    public Map<String, Boolean> getPostTypes(RequestInfo requestInfo, String tenantId) {
        String stateTenantId = stateTenantId(tenantId);
        List<Map<String, Object>> rows;
        try {
            rows = mdmsService.searchMDMS(requestInfo, stateTenantId,
                    ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPES_SCHEMA_CODE, null, FETCH_LIMIT, 0);
        } catch (Exception e) {
            log.error("Error reading {} for tenant {}: {}",
                    ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPES_SCHEMA_CODE, stateTenantId, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR,
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
            return Map.of(); // never reached
        }

        return extractActivePostTypes(rows);
    }

    /**
     * Whether a configured sheet applies: every sheet except the posts sheet always does; the posts sheet
     * only when the campaign type is one of excel.ingestion.posts.campaign.types (POLIO by default), the
     * campaign uses the outside-household delivery method AND the post-types master has at least one
     * active code. False when there is no campaign to check (blank referenceId or an
     * attendance-register reference).
     */
    public boolean isSheetApplicable(String sheetNameKey, String referenceId, String referenceType,
                                     String tenantId, RequestInfo requestInfo) {
        if (!ProcessingConstants.POSTS_SHEET_NAME_KEY.equals(sheetNameKey)) {
            return true;
        }
        if (referenceId == null || referenceId.trim().isEmpty()
                || ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER.equals(referenceType)) {
            return false;
        }
        CampaignSearchResponse.CampaignDetail campaign =
                campaignService.searchCampaignById(referenceId, tenantId, requestInfo);
        return isPostsCampaignType(campaign)
                && extractDeliveryMethods(campaign).contains(ProcessingConstants.OUTSIDE_HOUSEHOLD_DELIVERY_METHOD)
                && !getPostTypes(requestInfo, tenantId).isEmpty();
    }

    private boolean isPostsCampaignType(CampaignSearchResponse.CampaignDetail campaign) {
        String projectType = campaign == null ? null : normalize(campaign.getProjectType());
        List<String> types = config.getPostsCampaignTypes();
        return projectType != null && types != null
                && types.stream().map(PostTypeMasterService::normalize).anyMatch(projectType::equals);
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
    private static Map<String, Boolean> extractActivePostTypes(List<Map<String, Object>> rows) {
        Map<String, Boolean> postTypes = new LinkedHashMap<>();
        if (rows == null) {
            return postTypes;
        }
        for (Map<String, Object> row : rows) {
            Object data = row.get("data");
            if (!(data instanceof Map)) {
                continue;
            }
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object active = dataMap.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_ACTIVE_FIELD);
            Object code = dataMap.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_CODE_FIELD);
            String normalizedCode = code instanceof String ? normalize((String) code) : null;
            if (Boolean.TRUE.equals(active) && normalizedCode != null) {
                Object isPermanentByDefault = dataMap.get(
                        ProcessingConstants.OUTSIDE_HOUSEHOLD_POST_TYPE_IS_PERMANENT_BY_DEFAULT_FIELD);
                postTypes.put(normalizedCode, Boolean.TRUE.equals(isPermanentByDefault));
            }
        }
        return postTypes;
    }

    /** Root/state tenant of a dotted tenantId (e.g. "mz.citya" -> "mz"); unchanged if there is no dot. */
    private static String stateTenantId(String tenantId) {
        return (tenantId != null && tenantId.contains(".")) ? tenantId.split("\\.")[0] : tenantId;
    }

    /** Normalized codes of additionalDetails.cycleData.deliveryMethods (codes or {code} objects), as the console saves them. */
    private static Set<String> extractDeliveryMethods(CampaignSearchResponse.CampaignDetail campaign) {
        Set<String> codes = new HashSet<>();
        Object cycleData = campaign == null || campaign.getAdditionalDetails() == null
                ? null : campaign.getAdditionalDetails().getCycleData();
        Object methods = cycleData instanceof Map ? ((Map<?, ?>) cycleData).get(CYCLE_DATA_DELIVERY_METHODS_KEY) : null;
        if (methods instanceof List) {
            for (Object method : (List<?>) methods) {
                Object code = method instanceof Map ? ((Map<?, ?>) method).get(CODE_KEY) : method;
                String normalized = code instanceof String ? normalizeDeliveryMethod((String) code) : null;
                if (normalized != null) {
                    codes.add(normalized);
                }
            }
        }
        return codes;
    }

    /** Delivery-method codes are compared upper-cased with non-alphanumerics removed (unlike post-type codes). */
    private static String normalizeDeliveryMethod(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
