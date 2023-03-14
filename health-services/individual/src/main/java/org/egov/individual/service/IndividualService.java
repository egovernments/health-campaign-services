package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.protocol.types.Field;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.util.EncrptionDecryptionUtil;
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

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
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

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForUpdate = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(IsDeletedValidator.class)
                    || validator.getClass().equals(IsDeletedSubEntityValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class)
                    || validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(RowVersionValidator.class)
                    || validator.getClass().equals(UniqueEntityValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class);

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
                             EnrichmentService enrichmentService) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.enrichmentService = enrichmentService;
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
        try {
            if (!validIndividuals.isEmpty()) {
                log.info("processing {} valid entities", validIndividuals.size());
                enrichmentService.create(validIndividuals, request);
                //encrypts aadhaar number
                List<Individual> encryptedIndividualList = encryptedIndividuals(validIndividuals);
                individualRepository.save(encryptedIndividualList,
                        properties.getSaveIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(isBulk, errorDetailsMap);
        //decrypt aadhaar number
        return decryptedIndividuals(validIndividuals);
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

        try {
            if (!validIndividuals.isEmpty()) {
                log.info("processing {} valid entities", validIndividuals.size());
                enrichmentService.update(validIndividuals, request);
                individualRepository.save(validIndividuals,
                        properties.getUpdateIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(isBulk, errorDetailsMap);
        //decrypt aadhaar number
        return decryptedIndividuals(validIndividuals);
    }

    public List<Individual> search(IndividualSearch individualSearch,
                                   Integer limit,
                                   Integer offset,
                                   String tenantId,
                                   Long lastChangedSince,
                                   Boolean includeDeleted) {
        String idFieldName = getIdFieldName(individualSearch);
        List<Individual> individuals = null;
        if (isSearchByIdOnly(individualSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(individualSearch)),
                    individualSearch);

            individuals =  individualRepository.findById(ids, idFieldName, includeDeleted)
                    .stream().filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
            //decrypt aadhaar number
            return decryptedIndividuals(individuals);
        }
        individuals = individualRepository.find(individualSearch, limit, offset, tenantId,
                        lastChangedSince, includeDeleted).stream()
                .filter(havingBoundaryCode(individualSearch.getBoundaryCode(), individualSearch.getWardCode()))
                .collect(Collectors.toList());
        //decrypt aadhaar number
        return decryptedIndividuals(individuals);

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

    private List<Individual> encryptedIndividuals(List<Individual> individuals) {
         byte[] publicKey = Base64.getDecoder().decode(properties.getPublicKey());
         List<Individual> encryptedIndividualList = new ArrayList<>();
         for (Individual individual : individuals) {
             Individual encryptedIndividual = individual;
                 try {
                    /* if (StringUtils.isNotBlank(individual.getMobileNumber())) {
                         encryptedIndividual.setMobileNumber(EncrptionDecryptionUtil.encrypt(individual.getMobileNumber(),publicKey));
                     }*/
                     if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                         Identifier identifier = individual.getIdentifiers().stream()
                                 .filter(id -> id.getIdentifierType().contains("AADHAAR"))
                                 .findFirst().orElse(null);
                         if (identifier != null) {
                             identifier.setIdentifierId(EncrptionDecryptionUtil.encrypt(identifier.getIdentifierId(),publicKey));
                             encryptedIndividual.setIdentifiers(Collections.singletonList(identifier));
                         }

                     }
                     encryptedIndividualList.add(encryptedIndividual);
                 } catch (Exception exception) {
                     log.error("IndividualService::encryptedIndividuals::Exception in encryption "+exception);
                 }

         }
         return encryptedIndividualList;
    }

    private List<Individual> decryptedIndividuals(List<Individual> individuals) {
        byte[] privateKey = Base64.getDecoder().decode(properties.getPrivateKey());
        List<Individual> decryptedIndividualList = new ArrayList<>();
        for (Individual individual : individuals) {
            Individual decryptedIndividual = individual;
            try {
                /*if (StringUtils.isNotBlank(individual.getMobileNumber())) {
                    decryptedIndividual.setMobileNumber(EncrptionDecryptionUtil.decrypt(individual.getMobileNumber(),privateKey));
                }*/
                if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                    Identifier identifier = individual.getIdentifiers().stream()
                            .filter(id -> id.getIdentifierType().contains("AADHAAR"))
                            .findFirst().orElse(null);
                    if (identifier != null) {
                        String decryptedValue = EncrptionDecryptionUtil.decrypt(identifier.getIdentifierId(),privateKey);
                        //Mask first 8 digits of aadhaar number
                        char[] charArray = decryptedValue.toCharArray();
                        Arrays.fill(charArray, 0, charArray.length - 4, 'X');
                        decryptedValue = new String(charArray);
                        identifier.setIdentifierId(decryptedValue);
                        decryptedIndividual.setIdentifiers(Collections.singletonList(identifier));
                    }

                }
                decryptedIndividualList.add(decryptedIndividual);
            } catch (Exception exception) {
                log.error("IndividualService::encryptedIndividuals::Exception in decryption "+exception);
            }

        }
        return decryptedIndividualList;
    }
}
