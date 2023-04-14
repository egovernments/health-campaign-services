package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualRequest;
import org.egov.common.models.project.ApiOperation;
import org.egov.common.models.user.UserRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.validators.AadharNumberValidator;
import org.egov.individual.validators.AddressTypeValidator;
import org.egov.individual.validators.IsDeletedSubEntityValidator;
import org.egov.individual.validators.IsDeletedValidator;
import org.egov.individual.validators.MobileNumberValidator;
import org.egov.individual.validators.NonExistentEntityValidator;
import org.egov.individual.validators.NullIdValidator;
import org.egov.individual.validators.RowVersionValidator;
import org.egov.individual.validators.UniqueEntityValidator;
import org.egov.individual.validators.UniqueSubEntityValidator;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.SET_INDIVIDUALS;
import static org.egov.individual.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class IndividualService {

    private final IndividualRepository individualRepository;

    private final List<Validator<IndividualBulkRequest, Individual>> validators;

    private final IndividualProperties properties;

    private final EnrichmentService enrichmentService;

    private final IndividualEncryptionService individualEncryptionService;

    private final UserIntegrationService userIntegrationService;

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForUpdate = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(IsDeletedValidator.class)
                    || validator.getClass().equals(IsDeletedSubEntityValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class)
                    || validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(RowVersionValidator.class)
                    || validator.getClass().equals(UniqueEntityValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class)
                    || validator.getClass().equals(MobileNumberValidator.class)
                    || validator.getClass().equals(AadharNumberValidator.class);

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForCreate = validator ->
            validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class)
                    || validator.getClass().equals(MobileNumberValidator.class)
                    || validator.getClass().equals(AadharNumberValidator.class);

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForDelete = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class);

    @Autowired
    public IndividualService(IndividualRepository individualRepository,
                             List<Validator<IndividualBulkRequest, Individual>> validators,
                             IndividualProperties properties,
                             EnrichmentService enrichmentService,
                             IndividualEncryptionService individualEncryptionService,
                             UserIntegrationService userIntegrationService) {
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.properties = properties;
        this.enrichmentService = enrichmentService;
        this.individualEncryptionService = individualEncryptionService;
        this.userIntegrationService = userIntegrationService;
    }

    public List<Individual> create(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return create(bulkRequest, false);
    }

    public List<Individual> create(IndividualBulkRequest request, boolean isBulk) {

        Tuple<List<Individual>, Map<Individual, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
                isBulk);
        Map<Individual, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Individual> validIndividuals = tuple.getX();
        List<Individual> encryptedIndividualList = Collections.emptyList();
        try {
            if (!validIndividuals.isEmpty()) {
                log.info("processing {} valid entities", validIndividuals.size());
                enrichmentService.create(validIndividuals, request);
                //encrypt PII data
                encryptedIndividualList = individualEncryptionService
                        .encrypt(request, validIndividuals, "IndividualEncrypt", isBulk);
                individualRepository.save(encryptedIndividualList,
                        properties.getSaveIndividualTopic());
                // integrate with user service create call
                integrateWithUserService(request, encryptedIndividualList, ApiOperation.CREATE);
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        //decrypt
        return individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualDecrypt", request.getRequestInfo());
    }

    private Tuple<List<Individual>, Map<Individual, ErrorDetails>> validate(List<Validator<IndividualBulkRequest, Individual>> validators,
                                                                            Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForCreate,
                                                                            IndividualBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<Individual, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicableForCreate, request,
                SET_INDIVIDUALS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<Individual> validIndividuals = request.getIndividuals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validIndividuals, errorDetailsMap);
    }

    public List<Individual> update(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return update(bulkRequest, false);
    }

    public List<Individual> update(IndividualBulkRequest request, boolean isBulk) {
        Tuple<List<Individual>, Map<Individual, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);
        Map<Individual, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Individual> validIndividuals = tuple.getX();
        List<Individual> encryptedIndividualList = Collections.emptyList();

        try {
            if (!validIndividuals.isEmpty()) {
                log.info("processing {} valid entities", validIndividuals.size());
                enrichmentService.update(validIndividuals, request);

                // get masked identifiers
                List<Identifier> maskedIdentifiers = filterMaskedIdentifiers(validIndividuals);
                // remove masked identifiers because we cannot encrypt them again
                List<Individual> individualsToEncrypt = validIndividuals.stream().map(individual -> {
                    if (!maskedIdentifiers.isEmpty()) {
                        individual.getIdentifiers().removeAll(maskedIdentifiers
                                .stream().filter(identifier ->
                                        identifier.getIndividualId().equals(individual.getId()))
                                .collect(Collectors.toList()));
                    }
                    return individual;
                }).collect(Collectors.toList());


                // encrypt new data
                encryptedIndividualList = individualEncryptionService
                        .encrypt(request, individualsToEncrypt, "IndividualEncrypt", isBulk);


                Map<String, Individual> idToObjMap = getIdToObjMap(encryptedIndividualList);
                // find existing individuals from db
                List<Individual> existingIndividuals = individualRepository.findById(new ArrayList<>(idToObjMap.keySet()),
                        false, "id");
                // extract existing identifiers (encrypted) from existing individuals
                Map<String, List<Identifier>> existingIdentifiers = existingIndividuals.stream()
                        .map(Individual::getIdentifiers)
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream).collect(Collectors.groupingBy(Identifier::getIndividualId));
                // merge existing identifiers with new identifiers such that they all are encrypted alike
                // this is because we cannot merge masked identifiers with new identifiers which are now encrypted
                encryptedIndividualList.forEach(encryptedIndividual -> {
                    List<Identifier> newIdentifiers = encryptedIndividual.getIdentifiers();
                    List<Identifier> identifierList = existingIdentifiers.get(encryptedIndividual.getId());
                    if (identifierList != null) {
                        newIdentifiers.addAll(identifierList);
                    }
                });

                // save
                individualRepository.save(encryptedIndividualList,
                        properties.getUpdateIndividualTopic());
                // integrate with user service create call
                integrateWithUserService(request, encryptedIndividualList, ApiOperation.UPDATE);
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        //decrypt
        return individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualDecrypt", request.getRequestInfo());
    }

    private List<Identifier> filterMaskedIdentifiers(List<Individual> validIndividuals) {
        return validIndividuals.stream().map(Individual::getIdentifiers).filter(Objects::nonNull).flatMap(Collection::stream)
                .filter(identifier -> identifier.getIdentifierId().contains("*"))
                .collect(Collectors.toList());
    }

    public List<Individual> search(IndividualSearch individualSearch,
                                   Integer limit,
                                   Integer offset,
                                   String tenantId,
                                   Long lastChangedSince,
                                   Boolean includeDeleted,
                                   RequestInfo requestInfo) {
        String idFieldName = getIdFieldName(individualSearch);
        List<Individual> encryptedIndividualList = null;
        if (isSearchByIdOnly(individualSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(individualSearch)),
                    individualSearch);

            encryptedIndividualList =  individualRepository.findById(ids, idFieldName, includeDeleted)
                    .stream().filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
            //decrypt
            return (!encryptedIndividualList.isEmpty())
                    ? individualEncryptionService.decrypt(encryptedIndividualList,
                    "IndividualDecrypt",  requestInfo)
                    : encryptedIndividualList;
        }
        //encrypt search criteria
        IndividualSearch encryptedIndividualSearch = individualEncryptionService
                .encrypt(individualSearch, "IndividualSearchEncrypt");
        encryptedIndividualList = individualRepository.find(encryptedIndividualSearch, limit, offset, tenantId,
                        lastChangedSince, includeDeleted).stream()
                .filter(havingBoundaryCode(individualSearch.getBoundaryCode(), individualSearch.getWardCode()))
                .collect(Collectors.toList());
        //decrypt
        return (!encryptedIndividualList.isEmpty())
                ? individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualDecrypt", requestInfo)
                : encryptedIndividualList;

    }

    private Predicate<Individual> havingBoundaryCode(String boundaryCode, String wardCode) {
        if (boundaryCode == null && wardCode == null) {
            return individual -> true;
        }

        if (StringUtils.isNotBlank(wardCode)) {
            return individual -> individual.getAddress()
                    .stream()
                    .anyMatch(address -> (StringUtils.compare(wardCode,address.getWard().getCode()) == 0));
        }
        return individual -> individual.getAddress()
                .stream()
                .anyMatch(address -> address.getLocality().getCode().equalsIgnoreCase(boundaryCode));

    }

    public List<Individual> delete(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return delete(bulkRequest, false);
    }

    public List<Individual> delete(IndividualBulkRequest request, boolean isBulk) {
        Tuple<List<Individual>, Map<Individual, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
                isBulk);
        Map<Individual, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Individual> validIndividuals = tuple.getX();
        try {
            if (!validIndividuals.isEmpty()) {
                log.info("processing {} valid entities", validIndividuals.size());
                enrichmentService.delete(validIndividuals, request);
                individualRepository.save(validIndividuals,
                        properties.getDeleteIndividualTopic());
                // integrate with user service create call
                integrateWithUserService(request, validIndividuals, ApiOperation.DELETE);
            }
        } catch(Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validIndividuals;
    }

    public void putInCache(List<Individual> individuals) {
        log.info("putting {} individuals in cache", individuals.size());
        individualRepository.putInCache(individuals);
        log.info("successfully put individuals in cache");
    }

    private void integrateWithUserService(IndividualBulkRequest request,
                                          List<Individual> encryptedIndividualList, ApiOperation apiOperation) {
        if (properties.isUserSyncEnabled()) {
            try {
                if (apiOperation.equals(ApiOperation.UPDATE)) {
                    userIntegrationService.updateUser(encryptedIndividualList,
                            request.getRequestInfo());
                    log.info("successfully updated user for {} individuals",
                            encryptedIndividualList.size());
                } else if (apiOperation.equals(ApiOperation.CREATE)) {
                    Long userId = userIntegrationService.createUser(encryptedIndividualList,
                            request.getRequestInfo()).map(UserRequest::getId).orElse(null);
                    encryptedIndividualList.forEach(individual ->
                            individual.setUserId(userId != null ?
                                    userId.toString() : null));
                    individualRepository.save(encryptedIndividualList,
                            properties.getUpdateIndividualTopic());
                    log.info("successfully created user for {} individuals",
                            encryptedIndividualList.size());
                } else {
                    userIntegrationService.deleteUser(encryptedIndividualList,
                            request.getRequestInfo());
                    log.info("successfully soft deleted user for {} individuals",
                            encryptedIndividualList.size());
                }
            } catch (Exception exception) {
                log.error("error occurred while creating user", exception);
            }
        }
    }
}
