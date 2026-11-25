package org.egov.household.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.pii.CipherText;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Rejects a household whose address text fields hold a value shaped like cipher text.
 *
 * <p>The address text values are stored as cipher text, so an input value must be plain text. The error names the
 * fields, never the value.</p>
 */
@Component
@Order(value = 6)
@Slf4j
public class HAddressValueValidator implements Validator<HouseholdBulkRequest, Household> {

    static final String ERROR_CODE = "HOUSEHOLD_ADDRESS_VALUE_NOT_ALLOWED";

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        log.debug("Validating that household address values are not cipher text.");
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        for (Household household : request.getHouseholds()) {
            List<String> fields = cipherShapedFields(household);
            if (!fields.isEmpty()) {
                String message = "Address value is not allowed in field(s): " + String.join(", ", fields);
                Error error = Error.builder()
                        .errorMessage(message)
                        .errorCode(ERROR_CODE)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                populateErrorDetails(household, error, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }

    /** True when one of the eight address text fields is cipher-shaped; such a household fails this validator. */
    public static boolean hasCipherShapedValue(Household household) {
        return !cipherShapedFields(household).isEmpty();
    }

    private static List<String> cipherShapedFields(Household household) {
        List<String> fields = new ArrayList<>();
        Address a = household.getAddress();
        if (a == null) {
            return fields;
        }
        addIfCipher("doorNo", a.getDoorNo(), fields);
        addIfCipher("addressLine1", a.getAddressLine1(), fields);
        addIfCipher("addressLine2", a.getAddressLine2(), fields);
        addIfCipher("landmark", a.getLandmark(), fields);
        addIfCipher("city", a.getCity(), fields);
        addIfCipher("pincode", a.getPincode(), fields);
        addIfCipher("buildingName", a.getBuildingName(), fields);
        addIfCipher("street", a.getStreet(), fields);
        return fields;
    }

    private static void addIfCipher(String field, String value, List<String> fields) {
        if (CipherText.isCipher(value)) {
            fields.add(field);
        }
    }
}
