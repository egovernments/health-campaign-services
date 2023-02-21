package org.egov.facility.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.facility.web.models.Facility;
import org.egov.facility.web.models.FacilityBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Slf4j
@Order(3)
public class FUniqueEntityValidator implements Validator<FacilityBulkRequest, Facility> {
    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        Map<Facility, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating unique entity for facility");
        List<Facility> validEntities = request.getFacility()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            log.info("valid entity not empty");
            Map<String, Facility> eMap = getIdToObjMap(validEntities);
            if (eMap.keySet().size() != validEntities.size()) {
                List<String> duplicates = eMap.keySet().stream().filter(id ->
                        validEntities.stream()
                                .filter(entity -> entity.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());
                for (String key : duplicates) {
                    Error error = getErrorForUniqueEntity();
                    populateErrorDetails(eMap.get(key), error, errorDetailsMap);
                }
            }
        }
        log.info("facility unique entity validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
