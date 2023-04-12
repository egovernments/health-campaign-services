package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.validators.AadharMobileNumberValidator;
import org.egov.individual.validators.AddressTypeValidator;
import org.egov.individual.validators.IsDeletedSubEntityValidator;
import org.egov.individual.validators.IsDeletedValidator;
import org.egov.individual.validators.NonExistentEntityValidator;
import org.egov.individual.validators.NullIdValidator;
import org.egov.individual.validators.RowVersionValidator;
import org.egov.individual.validators.UniqueEntityValidator;
import org.egov.individual.validators.UniqueSubEntityValidator;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
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

    private final IdGenService idGenService;

    private final IndividualRepository individualRepository;

    private final List<Validator<IndividualBulkRequest, Individual>> validators;

    private final ObjectMapper objectMapper;

    private final IndividualProperties properties;

    private final EnrichmentService enrichmentService;

    private final IndividualEncryptionService individualEncryptionService;

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForUpdate = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(IsDeletedValidator.class)
                    || validator.getClass().equals(IsDeletedSubEntityValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class)
                    || validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(RowVersionValidator.class)
                    || validator.getClass().equals(UniqueEntityValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class)
                    || validator.getClass().equals(AadharMobileNumberValidator.class);

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForCreate = validator ->
            validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class)
                    || validator.getClass().equals(AadharMobileNumberValidator.class);

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForDelete = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class);

    @Autowired
    public IndividualService(IdGenService idGenService,
                             IndividualRepository individualRepository,
                             List<Validator<IndividualBulkRequest, Individual>> validators,
                             @Qualifier("objectMapper") ObjectMapper objectMapper,
                             IndividualProperties properties,
                             EnrichmentService enrichmentService,
                             IndividualEncryptionService individualEncryptionService) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.enrichmentService = enrichmentService;
        this.individualEncryptionService = individualEncryptionService;
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
        List<Individual> encryptedIndividualList = null;
        try {
            if (!validIndividuals.isEmpty()) {
                log.info("processing {} valid entities", validIndividuals.size());
                enrichmentService.create(validIndividuals, request);
                //encrypt PII data
                encryptedIndividualList = individualEncryptionService
                        .encrypt(validIndividuals, "IndividualEncrypt");
                individualRepository.save(encryptedIndividualList,
                        properties.getSaveIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        //decrypt
        return individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualEncrypt", request.getRequestInfo());
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
        List<Individual> encryptedIndividualList = null;

        try {
            if (!validIndividuals.isEmpty()) {
                log.info("processing {} valid entities", validIndividuals.size());
                enrichmentService.update(validIndividuals, request);
                //encrypt PII data
                Map<String, List<Identifier>> identifierGroups = filterMaskedIdentifiers(validIndividuals);
                List<Individual> individualsToEncrypt = validIndividuals.stream().map(individual -> {
                    individual.getIdentifiers().removeAll(identifierGroups.get("MASKED")
                            .stream().filter(identifier ->
                                    identifier.getIndividualId().equals(individual.getId()))
                            .collect(Collectors.toList()));
                    return individual;
                }).collect(Collectors.toList());
                encryptedIndividualList = individualEncryptionService
                        .encrypt(individualsToEncrypt, "IndividualEncrypt");
                Map<String, Individual> idToObjMap = getIdToObjMap(encryptedIndividualList);
                List<Individual> existingIndividuals = individualRepository.findById(new ArrayList<>(idToObjMap.keySet()),
                        false, "id");
                Map<String, List<Identifier>> existingIdentifiers = existingIndividuals.stream().map(Individual::getIdentifiers)
                        .flatMap(Collection::stream).collect(Collectors.groupingBy(Identifier::getIndividualId));
                encryptedIndividualList.forEach(individual -> {
                    List<Identifier> identifiers = individual.getIdentifiers();
                    List<Identifier> identifierList = existingIdentifiers.get(individual.getId());
                    if (identifierList != null) {
                        identifiers.addAll(identifierList);
                    }
                });
                individualRepository.save(encryptedIndividualList,
                        properties.getUpdateIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        //decrypt
        return individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualEncrypt", request.getRequestInfo());
    }

    private Map<String, List<Identifier>> filterMaskedIdentifiers(List<Individual> validIndividuals) {
        return validIndividuals.stream().map(Individual::getIdentifiers).flatMap(Collection::stream)
                .collect(Collectors.groupingBy(identifier -> identifier.getIdentifierId().contains("*") ? "MASKED" : "UNMASKED"));
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
                    "IndividualEncrypt",  requestInfo)
                    : encryptedIndividualList;
        }
        //encrypt search criteria
        IndividualSearch encryptedIndividualSearch = individualEncryptionService
                .encrypt(individualSearch, "IndividualEncrypt");
        encryptedIndividualList = individualRepository.find(encryptedIndividualSearch, limit, offset, tenantId,
                        lastChangedSince, includeDeleted).stream()
                .filter(havingBoundaryCode(individualSearch.getBoundaryCode(), individualSearch.getWardCode()))
                .collect(Collectors.toList());
        //decrypt
        return (!encryptedIndividualList.isEmpty())
                ? individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualEncrypt", requestInfo)
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
            }
        } catch(Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validIndividuals;
    }

    /**
     * validate if aadhar already exists
     * @param individuals
     */
    private void validateAadhaarUniqueness (List<Individual> individuals, boolean isBulk) {

        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        String tenantId = getTenantId(individuals);

        if (!individuals.isEmpty()) {
            for (Individual individual : individuals) {
                if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                    Identifier identifier = individual.getIdentifiers().stream()
                            .filter(id -> id.getIdentifierType().contains("AADHAAR"))
                            .findFirst().orElse(null);
                    if (identifier != null && StringUtils.isNotBlank(identifier.getIdentifierId())) {
                        Identifier identifierSearch = Identifier.builder().identifierType(identifier.getIdentifierType()).identifierId(identifier.getIdentifierId()).build();
                        IndividualSearch individualSearch = IndividualSearch.builder().identifier(identifierSearch).build();
                        List<Individual> individualsList = individualRepository.find(individualSearch,null,null,tenantId,null,false);
                        if (!CollectionUtils.isEmpty(individualsList)) {
                            boolean isSelfIdentifier = individualsList.stream()
                                    .anyMatch(ind -> ind.getId().equalsIgnoreCase(individual.getId()));
                            if (!isSelfIdentifier) {
                                Error error = Error.builder().errorMessage("Aadhaar already exists for Individual - "+individualsList.get(0).getIndividualId()).errorCode("DUPLICATE_AADHAAR").type(Error.ErrorType.NON_RECOVERABLE).exception(new CustomException("DUPLICATE_AADHAAR", "Aadhaar already exists for Individual - "+individualsList.get(0).getIndividualId())).build();
                                populateErrorDetails(individual, error, errorDetailsMap);
                            }
                        }
                    }

                }
            }
        }

        if (!errorDetailsMap.isEmpty()) {
            log.info("call tracer.handleErrors(), {}", errorDetailsMap.values());
            if (!isBulk) {
                throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
            }
        }
    }

    /**
     * Fetch identifier for update
     * @param individuals
     */
    private void fetchIdentifier(List<Individual> individuals) {
        String tenantId = getTenantId(individuals);
        if (!individuals.isEmpty()) {
            for (Individual individual : individuals) {
                if (individual.getIdentifiers() == null || individual.getIdentifiers().isEmpty()) {
                    Identifier identifierSearch = Identifier.builder().individualId(individual.getId()).build();
                    IndividualSearch individualSearch = IndividualSearch.builder().identifier(identifierSearch).build();
                    List<Individual> individualsList = individualRepository.find(individualSearch, null, null, tenantId, null, false);
                    if (!individualsList.isEmpty()) {
                        individual.setIdentifiers(individualsList.get(0).getIdentifiers());
                    }
                }
            }
        }
    }

    public void putInCache(List<Individual> individuals) {
        log.info("putting {} individuals in cache", individuals.size());
        individualRepository.putInCache(individuals);
        log.info("successfully put individuals in cache");
    }
}
