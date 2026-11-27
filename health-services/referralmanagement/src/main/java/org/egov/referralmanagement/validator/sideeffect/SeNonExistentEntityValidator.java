package org.egov.referralmanagement.validator.sideeffect;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.SideEffectRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.referralmanagement.Constants.GET_ID;

@Component
@Order(value = 4)
@Slf4j
public class SeNonExistentEntityValidator implements Validator<SideEffectBulkRequest, SideEffect> {

    private final SideEffectRepository sideEffectRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    public SeNonExistentEntityValidator(SideEffectRepository sideEffectRepository, ObjectMapper objectMapper) {
        this.sideEffectRepository = sideEffectRepository;
        this.objectMapper = objectMapper;
    }


    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        log.info("validating for existence of entity");
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        List<SideEffect> sideEffects = request.getSideEffects();
        Class<?> objClass = getObjClass(sideEffects);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, SideEffect> iMap = getIdToObjMap(sideEffects
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from Side Effect entities
        sideEffects.forEach(sideEffect -> {
            idList.add(sideEffect.getId());
            clientReferenceIdList.add(sideEffect.getClientReferenceId());
        });
        if (!iMap.isEmpty()) {
            SideEffectSearch sideEffectSearch = SideEffectSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<SideEffect> existingSideEffects;
            try {
                // Query the repository to find existing entities
                existingSideEffects = sideEffectRepository.find(sideEffectSearch, sideEffects.size(), 0,
                        sideEffects.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for SideEffect with error: {}", e.getMessage(), e);
                throw new CustomException("SIDE_EFFECT_SEARCH_FAILED", "Search Failed for SideEffect, " + e.getMessage()); 
            }

            List<SideEffect> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingSideEffects, idMethod);
            nonExistentIndividuals.forEach(sideEffect -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(sideEffect, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}

