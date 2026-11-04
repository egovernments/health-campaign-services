package org.egov.facility.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.service.OutsideHouseholdTypeService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Checks a site (a facility whose usage is a site type of HCM.OUTSIDE_HOUSEHOLD_TYPES) the way the Excel upload does:
 * the name length and characters, and a category that is one of its type's categories. Without this, a site made through the API
 * with such values is carried into later templates with locked cells the planner cannot correct.
 */
@Component
@Order(value = 6)
@Slf4j
public class FOutsideHouseholdSiteValidator implements Validator<FacilityBulkRequest, Facility> {

    static final String CATEGORY_KEY = "outsideHouseholdCategory";
    static final String INVALID_NAME = "INVALID_OUTSIDE_HOUSEHOLD_SITE_NAME";
    static final String INVALID_CATEGORY = "INVALID_OUTSIDE_HOUSEHOLD_SITE_CATEGORY";
    /**
     * As in the Excel upload, a site name may use any character except ! @ # $ % ^ * ; < > ? | \ ~ and control characters
     * (line break, tab), and may not start with = or +
     */
    static final Pattern ALLOWED_NAME = Pattern.compile("(?![=+])[^!@#$%^*;<>?|\\\\~\\p{Cntrl}]+");

    private final OutsideHouseholdTypeService outsideHouseholdTypeService;
    private final FacilityConfiguration configuration;

    public FOutsideHouseholdSiteValidator(OutsideHouseholdTypeService outsideHouseholdTypeService, FacilityConfiguration configuration) {
        this.outsideHouseholdTypeService = outsideHouseholdTypeService;
        this.configuration = configuration;
    }

    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        HashMap<Facility, List<Error>> errorDetailsMap = new HashMap<>();
        if (!Boolean.TRUE.equals(configuration.getValidateOutsideHouseholdSites()) || request.getFacilities() == null) {
            return errorDetailsMap;
        }
        for (Facility facility : request.getFacilities()) {
            String type = facility.getUsage() == null ? null : facility.getUsage().trim().toUpperCase(Locale.ROOT);
            Map<String, List<String>> categoriesByType = outsideHouseholdTypeService.getCategoriesByType(facility.getTenantId());
            if (type == null || !categoriesByType.containsKey(type)) {
                continue;   // not a site, or the site types could not be read
            }
            String name = facility.getName() == null ? "" : facility.getName().trim();
            int min = configuration.getOutsideHouseholdSiteNameMinLength();
            int max = configuration.getOutsideHouseholdSiteNameMaxLength();
            if (name.length() < min || name.length() > max) {
                populateErrorDetails(facility, error(INVALID_NAME,
                        "Site name must be between " + min + " and " + max + " characters"), errorDetailsMap);
            } else if (!ALLOWED_NAME.matcher(name).matches()) {
                populateErrorDetails(facility, error(INVALID_NAME,
                        "Site name cannot have ! @ # $ % ^ * ; < > ? | \\ ~, a line break or a tab, and cannot start with = or +"), errorDetailsMap);
            }
            String category = category(facility);
            List<String> allowed = categoriesByType.get(type);
            if (category != null && !allowed.isEmpty() && !allowed.contains(category)) {
                populateErrorDetails(facility, error(INVALID_CATEGORY,
                        "Category " + category + " is not allowed for site type " + type), errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }

    private static String category(Facility facility) {
        if (facility.getAdditionalFields() == null || facility.getAdditionalFields().getFields() == null) {
            return null;
        }
        for (var field : facility.getAdditionalFields().getFields()) {
            if (field != null && CATEGORY_KEY.equals(field.getKey()) && field.getValue() != null && !field.getValue().isBlank()) {
                return field.getValue().trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static Error error(String code, String message) {
        return Error.builder().errorMessage(message).errorCode(code).type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(code, message)).build();
    }
}
