package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.adrm.adverseevent.AdverseEvent;
import org.egov.common.models.adrm.adverseevent.AdverseEventBulkRequest;
import org.egov.common.models.adrm.adverseevent.AdverseEventRequest;
import org.egov.common.models.adrm.adverseevent.AdverseEventSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.AdverseEventRepository;
import org.egov.project.service.enrichment.AdverseEventEnrichmentService;
import org.egov.project.validator.adverseevent.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.project.Constants.SET_ADVERSE_EVENTS;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class AdverseEventService {
    private final IdGenService idGenService;

    private final AdverseEventRepository adverseEventRepository;

    private final ProjectService projectService;

    private final ProjectConfiguration projectConfiguration;

    private final AdverseEventEnrichmentService adverseEventEnrichmentService;

    private final List<Validator<AdverseEventBulkRequest, AdverseEvent>> validators;

    private final Predicate<Validator<AdverseEventBulkRequest, AdverseEvent>> isApplicableForCreate = validator ->
            validator.getClass().equals(AdProjectTaskIdValidator.class);

    private final Predicate<Validator<AdverseEventBulkRequest, AdverseEvent>> isApplicableForUpdate = validator ->
            validator.getClass().equals(AdProjectTaskIdValidator.class)
                || validator.getClass().equals(AdNullIdValidator.class)
                || validator.getClass().equals(AdIsDeletedValidator.class)
                || validator.getClass().equals(AdUniqueEntityValidator.class)
                || validator.getClass().equals(AdNonExistentEntityValidator.class);

    private final Predicate<Validator<AdverseEventBulkRequest, AdverseEvent>> isApplicableForDelete = validator ->
            validator.getClass().equals(AdNullIdValidator.class)
                || validator.getClass().equals(AdNonExistentEntityValidator.class);
    
    @Autowired
    public AdverseEventService(
            IdGenService idGenService,
            AdverseEventRepository adverseEventRepository,
            ProjectService projectService,
            ProjectConfiguration projectConfiguration,
            AdverseEventEnrichmentService adverseEventEnrichmentService,
            List<Validator<AdverseEventBulkRequest, AdverseEvent>> validators
    ) {
        this.idGenService = idGenService;
        this.adverseEventRepository = adverseEventRepository;
        this.projectService = projectService;
        this.projectConfiguration = projectConfiguration;
        this.adverseEventEnrichmentService = adverseEventEnrichmentService;
        this.validators = validators;
    }

    public AdverseEvent create(AdverseEventRequest request) {
        log.info("received request to create adverse events");
        AdverseEventBulkRequest bulkRequest = AdverseEventBulkRequest.builder().requestInfo(request.getRequestInfo())
                .adverseEvents(Collections.singletonList(request.getAdverseEvent())).build();
        log.info("creating bulk request");
        return create(bulkRequest, false).get(0);
    }

    public List<AdverseEvent> create(AdverseEventBulkRequest adverseEventRequest, boolean isBulk) {
        log.info("received request to create bulk adverse events");
        Tuple<List<AdverseEvent>, Map<AdverseEvent, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, adverseEventRequest, isBulk);
        Map<AdverseEvent, ErrorDetails> errorDetailsMap = tuple.getY();
        List<AdverseEvent> validAdverseEvents = tuple.getX();

        try {
            if (!validAdverseEvents.isEmpty()) {
                log.info("processing {} valid entities", validAdverseEvents.size());
                adverseEventEnrichmentService.create(validAdverseEvents, adverseEventRequest);
                adverseEventRepository.save(validAdverseEvents,
                        projectConfiguration.getCreateAdverseEventTopic());
                log.info("successfully created adverse events");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating adverse events: {}", exception.getMessage());
            populateErrorDetails(adverseEventRequest, errorDetailsMap, validAdverseEvents,
                    exception, SET_ADVERSE_EVENTS);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validAdverseEvents;
    }

    public AdverseEvent update(AdverseEventRequest request) {
        log.info("received request to update adverse event");
        AdverseEventBulkRequest bulkRequest = AdverseEventBulkRequest.builder().requestInfo(request.getRequestInfo())
                .adverseEvents(Collections.singletonList(request.getAdverseEvent())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false).get(0);
    }

    public List<AdverseEvent> update(AdverseEventBulkRequest adverseEventRequest, boolean isBulk) {
        log.info("received request to update bulk adverse event");
        Tuple<List<AdverseEvent>, Map<AdverseEvent, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, adverseEventRequest, isBulk);
        Map<AdverseEvent, ErrorDetails> errorDetailsMap = tuple.getY();
        List<AdverseEvent> validAdverseEvents = tuple.getX();

        try {
            if (!validAdverseEvents.isEmpty()) {
                log.info("processing {} valid entities", validAdverseEvents.size());
                adverseEventEnrichmentService.update(validAdverseEvents, adverseEventRequest);
                adverseEventRepository.save(validAdverseEvents,
                        projectConfiguration.getUpdateAdverseEventTopic());
                log.info("successfully updated bulk adverse events");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating adverse events", exception);
            populateErrorDetails(adverseEventRequest, errorDetailsMap, validAdverseEvents,
                    exception, SET_ADVERSE_EVENTS);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validAdverseEvents;
    }

    public List<AdverseEvent> search(AdverseEventSearchRequest adverseEventSearchRequest,
                                     Integer limit,
                                     Integer offset,
                                     String tenantId,
                                     Long lastChangedSince,
                                     Boolean includeDeleted) throws Exception {
        log.info("received request to search adverse events");
        String idFieldName = getIdFieldName(adverseEventSearchRequest.getAdverseEvent());
        if (isSearchByIdOnly(adverseEventSearchRequest.getAdverseEvent(), idFieldName)) {
            log.info("searching adverse events by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(adverseEventSearchRequest.getAdverseEvent())),
                    adverseEventSearchRequest.getAdverseEvent());
            log.info("fetching adverse events with ids: {}", ids);
            return adverseEventRepository.findById(ids, includeDeleted, idFieldName).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        log.info("searching adverse events using criteria");
        return adverseEventRepository.find(adverseEventSearchRequest.getAdverseEvent(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    public AdverseEvent delete(AdverseEventRequest adverseEventRequest) {
        log.info("received request to delete a adverse event");
        AdverseEventBulkRequest bulkRequest = AdverseEventBulkRequest.builder().requestInfo(adverseEventRequest.getRequestInfo())
                .adverseEvents(Collections.singletonList(adverseEventRequest.getAdverseEvent())).build();
        log.info("creating bulk request");
        return delete(bulkRequest, false).get(0);
    }

    public List<AdverseEvent> delete(AdverseEventBulkRequest adverseEventRequest, boolean isBulk) {
        Tuple<List<AdverseEvent>, Map<AdverseEvent, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, adverseEventRequest, isBulk);
        Map<AdverseEvent, ErrorDetails> errorDetailsMap = tuple.getY();
        List<AdverseEvent> validAdverseEvents = tuple.getX();

        try {
            if (!validAdverseEvents.isEmpty()) {
                log.info("processing {} valid entities", validAdverseEvents.size());
                List<String> adverseEventIds = validAdverseEvents.stream().map(entity -> entity.getId()).collect(Collectors.toSet()).stream().collect(Collectors.toList());
                List<AdverseEvent> existingAdverseEvents = adverseEventRepository
                        .findById(adverseEventIds, false);
                adverseEventEnrichmentService.delete(existingAdverseEvents, adverseEventRequest);
                adverseEventRepository.save(existingAdverseEvents,
                        projectConfiguration.getDeleteAdverseEventTopic());
                log.info("successfully deleted entities");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", exception);
            populateErrorDetails(adverseEventRequest, errorDetailsMap, validAdverseEvents,
                    exception, SET_ADVERSE_EVENTS);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validAdverseEvents;
    }

    public void putInCache(List<AdverseEvent> adverseEvents) {
        log.info("putting {} adverse events in cache", adverseEvents.size());
        adverseEventRepository.putInCache(adverseEvents);
        log.info("successfully put adverse events in cache");
    }

    private Tuple<List<AdverseEvent>, Map<AdverseEvent, ErrorDetails>> validate(
            List<Validator<AdverseEventBulkRequest, AdverseEvent>> validators, 
            Predicate<Validator<AdverseEventBulkRequest, AdverseEvent>> isApplicable, 
            AdverseEventBulkRequest request, 
            boolean isBulk
    ) {
        log.info("validating request");
        Map<AdverseEvent, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicable, request,
                SET_ADVERSE_EVENTS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.error("validation error occurred. error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<AdverseEvent> validAdverseEvents = request.getAdverseEvents().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validation successful, found valid adverse events");
        return new Tuple<>(validAdverseEvents, errorDetailsMap);
    }

}
