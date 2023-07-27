package org.egov.project.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.AdverseEvent;
import org.egov.common.models.project.AdverseEventBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.AdverseEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.project.Constants.GET_ID;

@Component
@Order(value = 4)
@Slf4j
public class AdNonExistentEntityValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {

    private final AdverseEventRepository adverseEventRepository;

    @Autowired
    public AdNonExistentEntityValidator(AdverseEventRepository adverseEventRepository) {
        this.adverseEventRepository = adverseEventRepository;
    }


    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating for existence of entity");
        Map<AdverseEvent, List<Error>> errorDetailsMap = new HashMap<>();
        List<AdverseEvent> adverseEvents = request.getAdverseEvents();
        Class<?> objClass = getObjClass(adverseEvents);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, AdverseEvent> iMap = getIdToObjMap(adverseEvents
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> beneficiaryIds = new ArrayList<>(iMap.keySet());
            List<AdverseEvent> existingAdverseEvents = adverseEventRepository
                    .findById(beneficiaryIds, false, getIdFieldName(idMethod));
            List<AdverseEvent> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingAdverseEvents, idMethod);
            nonExistentIndividuals.forEach(adverseEvent -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(adverseEvent, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}

