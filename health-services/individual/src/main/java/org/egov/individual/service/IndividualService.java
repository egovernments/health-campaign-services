package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.util.EncryptionDecryptionUtil;
import org.egov.individual.validators.*;
import org.egov.individual.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
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

    private final EncryptionDecryptionUtil encryptionDecryptionUtil;

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
                             EncryptionDecryptionUtil encryptionDecryptionUtil) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.enrichmentService = enrichmentService;
        this.encryptionDecryptionUtil = encryptionDecryptionUtil;
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
                encryptedIndividualList = (List<Individual>) encryptIndividuals(validIndividuals,null,"IndividualEncrypt");
                individualRepository.save(encryptedIndividualList,
                        properties.getSaveIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(isBulk, errorDetailsMap);
        //decrypt
        return decryptIndividuals(encryptedIndividualList, request.getRequestInfo());
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
                encryptedIndividualList = (List<Individual>) encryptIndividuals(validIndividuals,null,"IndividualEncrypt");
                individualRepository.save(encryptedIndividualList,
                        properties.getUpdateIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(isBulk, errorDetailsMap);
        //decrypt
        return decryptIndividuals(encryptedIndividualList, request.getRequestInfo());
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
            return (!encryptedIndividualList.isEmpty()) ? decryptIndividuals(encryptedIndividualList, requestInfo): encryptedIndividualList;
        }
        //encrypt search criteria
        IndividualSearch encryptedIndividualSearch = (IndividualSearch) encryptIndividuals(null,individualSearch,"IndividualSearchEncrypt");
        encryptedIndividualList = individualRepository.find(encryptedIndividualSearch, limit, offset, tenantId,
                        lastChangedSince, includeDeleted).stream()
                .filter(havingBoundaryCode(individualSearch.getBoundaryCode(), individualSearch.getWardCode()))
                .collect(Collectors.toList());
        //decrypt
        return (!encryptedIndividualList.isEmpty()) ? decryptIndividuals(encryptedIndividualList, requestInfo): encryptedIndividualList;

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

        handleErrors(isBulk, errorDetailsMap);

        return validIndividuals;
    }

    private static void handleErrors(boolean isBulk, Map<Individual, ErrorDetails> errorDetailsMap) {
        if (!errorDetailsMap.isEmpty()) {
            log.error("{} errors collected", errorDetailsMap.size());
            if (isBulk) {
                log.info("call tracer.handleErrors(), {}", errorDetailsMap.values());
            } else {
                throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
            }
        }
    }

    /**
     * Encrypts the PII data of the individual
     * @param validIndividuals
     * @return
     */
    private Object encryptIndividuals(List<Individual> validIndividuals, IndividualSearch individualSearch, String key) {
        //encrypt
        Class classType;
        Object objectToEncrypt;
        List<Individual> encryptedIndividualList = new ArrayList<>();

        if (individualSearch != null) {
            classType = IndividualSearch.class;
            objectToEncrypt = individualSearch;
        } else if (validIndividuals.size() > 1) {
            classType = Individual.class;
            objectToEncrypt = validIndividuals;
        } else {
            classType = Individual.class;
            objectToEncrypt = validIndividuals.get(0);
        }
        objectToEncrypt = encryptionDecryptionUtil.encryptObject(objectToEncrypt, key, classType);

        if (objectToEncrypt instanceof List) {
            encryptedIndividualList.addAll((List<Individual>)objectToEncrypt);
        } else if (objectToEncrypt instanceof Individual) {
            encryptedIndividualList.add((Individual)objectToEncrypt);
        } else if (objectToEncrypt instanceof  IndividualSearch) {
           return (IndividualSearch) objectToEncrypt;
        }

        // check if the aadhaar already exists
        validateAadhaarUniqueness(encryptedIndividualList);

        return encryptedIndividualList;
    }

    /**
     * Decrypts the data for
     * @param encryptedIndividuals
     * @return
     */
    private List<Individual> decryptIndividuals(List<Individual> encryptedIndividuals, RequestInfo requestInfo) {
        //decrypt
        Class classType;
        Object objectToDecrypt;
        List<Individual> decryptedIndividualList = new ArrayList<>();

        if (encryptedIndividuals.size() > 1) {
            classType = Individual.class;
            objectToDecrypt = encryptedIndividuals;
        } else {
            classType = Individual.class;
            objectToDecrypt = encryptedIndividuals.get(0);
        }
        objectToDecrypt = encryptionDecryptionUtil.decryptObject(objectToDecrypt, "IndividualDecrypt", classType, requestInfo);

        if (objectToDecrypt instanceof List) {
            decryptedIndividualList.addAll((List<Individual>)objectToDecrypt);
        } else if (objectToDecrypt instanceof Individual) {
            decryptedIndividualList.add((Individual)objectToDecrypt);
        }

        return decryptedIndividualList;
    }

    /**
     * validate if aadhar already exists
     * @param individuals
     */
    private void validateAadhaarUniqueness (List<Individual> individuals) {

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
                            Error error = Error.builder().errorMessage("Aadhaar already exists for Individual - "+individualsList.get(0).getIndividualId()).errorCode("DUPLICATE_AADHAAR").type(Error.ErrorType.NON_RECOVERABLE).exception(new CustomException("DUPLICATE_AADHAAR", "Aadhaar already exists for Individual - "+individualsList.get(0).getIndividualId())).build();
                            populateErrorDetails(individual, error, errorDetailsMap);
                        }
                    }

                }
            }
        }

        if (!errorDetailsMap.isEmpty()) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }

    }

}
