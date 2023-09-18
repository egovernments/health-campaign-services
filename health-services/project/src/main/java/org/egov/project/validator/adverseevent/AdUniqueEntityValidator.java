package org.egov.project.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.adrm.adverseevent.AdverseEvent;
import org.egov.common.models.adrm.adverseevent.AdverseEventBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Order(value = 2)
@Slf4j
public class AdUniqueEntityValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {

    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating unique entity");
        Map<AdverseEvent, List<Error>> errorDetailsMap = new HashMap<>();
        List<AdverseEvent> validEntities = request.getAdverseEvents()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            Map<String, AdverseEvent> eMap = getIdToObjMap(validEntities);
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
        return errorDetailsMap;
    }
}
