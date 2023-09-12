package org.egov.adrm.validator.adverseevent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.adrm.repository.AdverseEventRepository;
import org.egov.common.models.Error;
import org.egov.common.models.project.adverseevent.AdverseEvent;
import org.egov.common.models.project.adverseevent.AdverseEventBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.adrm.Constants.GET_ID;
import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

@Component
@Order(value = 4)
@Slf4j
public class AdNonExistentEntityValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {

    private final AdverseEventRepository adverseEventRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    public AdNonExistentEntityValidator(AdverseEventRepository adverseEventRepository, ObjectMapper objectMapper) {
        this.adverseEventRepository = adverseEventRepository;
        this.objectMapper = objectMapper;
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
            List<String> adverseEventIds = new ArrayList<>(iMap.keySet());
            List<AdverseEvent> existingAdverseEvents = adverseEventRepository
                    .findById(adverseEventIds, false, getIdFieldName(idMethod));
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

