package org.egov.individual.service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.abha.AbhaGatewayOtpVerifyRequest;
import org.egov.common.models.abha.AbhaGatewayOtpVerifyResponse;
import org.egov.common.models.abha.AbhaOtpRequest;
import org.egov.common.models.abha.AbhaOtpResponse;
import org.egov.common.models.individual.AbhaOtpVerifyRequest;
import org.egov.common.models.core.Role;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.*;
import org.egov.common.models.project.ApiOperation;
import org.egov.common.models.user.UserRequest;
import org.egov.common.service.AbhaService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.AbhaRepository;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.util.BeneficiaryIdGenUtil;
import org.egov.individual.validators.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.*;

@Service
@Slf4j
public class IndividualService {

    private final IndividualRepository individualRepository;

    private final AbhaRepository abhaRepository;

    private final AbhaService abhaService;

    private final List<Validator<IndividualBulkRequest, Individual>> validators;

    private final IndividualProperties properties;

    private final EnrichmentService enrichmentService;

    private final IndividualEncryptionService individualEncryptionService;

    private final UserIntegrationService userIntegrationService;

    private final NotificationService notificationService;

    private final BeneficiaryIdGenUtil beneficiaryIdGenUtil;

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForUpdate = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(IBoundaryValidator.class)
                    || validator.getClass().equals(IsDeletedValidator.class)
                    || validator.getClass().equals(IsDeletedSubEntityValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class)
                    || validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(RowVersionValidator.class)
                    || validator.getClass().equals(UniqueEntityValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class)
                    || validator.getClass().equals(MobileNumberValidator.class)
                    || validator.getClass().equals(AadharNumberValidator.class)
                    || validator.getClass().equals(IdPoolValidatorForUpdate.class)
                    || validator.getClass().equals(AbhaNumberValidator.class)
            ;

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForCreate = validator ->
            validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(IExistentEntityValidator.class)
                    || validator.getClass().equals(IBoundaryValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class)
                    || validator.getClass().equals(MobileNumberValidator.class)
                    || validator.getClass().equals(AadharNumberValidatorForCreate.class)
                    || validator.getClass().equals(IdPoolValidatorForCreate.class)
                    || validator.getClass().equals(AbhaNumberValidatorForCreate.class)
            ;

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForDelete = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class);

    @Autowired
    public IndividualService(IndividualRepository individualRepository,
                             List<Validator<IndividualBulkRequest, Individual>> validators,
                             IndividualProperties properties,
                             EnrichmentService enrichmentService,
                             IndividualEncryptionService individualEncryptionService,
                             UserIntegrationService userIntegrationService,
                             NotificationService notificationService,
                             BeneficiaryIdGenUtil beneficiaryIdGenUtil,
                             AbhaRepository abhaRepository, AbhaService abhaService) {
        this.individualRepository = individualRepository;
        this.abhaRepository = abhaRepository;
        this.validators = validators;
        this.properties = properties;
        this.enrichmentService = enrichmentService;
        this.individualEncryptionService = individualEncryptionService;
        this.userIntegrationService = userIntegrationService;
        this.notificationService = notificationService;
        this.beneficiaryIdGenUtil = beneficiaryIdGenUtil;
        this.abhaService = abhaService;
    }

    public List<Individual> create(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        List<Individual> individuals = create(bulkRequest, false);

        // check if sms feature is enable for the environment role
        if(properties.getIsSMSEnabled() && isSmsEnabledForRole(request))
            notificationService.sendNotification(request, true);
        return individuals;
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
                // integrate with user service create call
                validIndividuals = integrateWithUserService(request, validIndividuals, ApiOperation.CREATE, errorDetailsMap);
                //encrypt PII data

                // BenificiaryIds to Update
                List<String> beneficiaryIds = validIndividuals.stream()
                        .flatMap(d -> Optional.ofNullable(d.getIdentifiers())
                                .orElse(Collections.emptyList())
                                .stream()
                                .filter(identifier -> UNIQUE_BENEFICIARY_ID.equals(identifier.getIdentifierType()))
                                .findFirst()
                                .stream())
                        .map(identifier -> String.valueOf(identifier.getIdentifierId()))
                        .toList();

                // trigger Aadhaar OTP requests
                triggerAadhaarOtpRequests(validIndividuals, request.getRequestInfo());

                if (!validIndividuals.isEmpty()) {
                    encryptedIndividualList = individualEncryptionService
                            .encrypt(request, validIndividuals, "IndividualEncrypt", isBulk);
                    individualRepository.save(encryptedIndividualList,
                            properties.getSaveIndividualTopic());
                    // update beneficiary ids in idgen
                    beneficiaryIdGenUtil.updateBeneficiaryIds(beneficiaryIds, validIndividuals.get(0).getTenantId(), request.getRequestInfo());
                }
            }
        } catch (CustomException exception) {
            log.error("error occurred", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        //decrypt
        List<Individual> decryptedIndividualList = individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualDecrypt", request.getRequestInfo());
        return decryptedIndividualList;
    }

    private Tuple<List<Individual>, Map<Individual, ErrorDetails>> validate(List<Validator<IndividualBulkRequest, Individual>> validators,
                                                                            Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForCreate,
                                                                            IndividualBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<Individual, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicableForCreate, request,
                SET_INDIVIDUALS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            Set<String> hashset = new HashSet<>();
            for (Map.Entry<Individual, ErrorDetails> entry : errorDetailsMap.entrySet()) {
                List<Error> errors = entry.getValue().getErrors();
                hashset.addAll(errors.stream().map(error -> error.getErrorCode()).collect(Collectors.toSet()));
            }
            throw new CustomException(String.join(":",  hashset), errorDetailsMap.values().toString());
        }
        List<Individual> validIndividuals = request.getIndividuals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validIndividuals, errorDetailsMap);
    }

    public List<Individual> update(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        List<Individual> individuals = update(bulkRequest, false);

        // check if sms feature is enable for the environment role
        if(properties.getIsSMSEnabled() && isSmsEnabledForRole(request))
            notificationService.sendNotification(request, false);
        return individuals;
    }

    public List<Individual> update(IndividualBulkRequest request, boolean isBulk) {
        String tenantId =  request.getRequestInfo().getUserInfo().getTenantId();
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
                boolean identifiersPresent = validIndividuals.stream()
                        .anyMatch(individual -> individual.getIdentifiers() != null
                                && !individual.getIdentifiers().isEmpty());

                List<Individual> individualsToEncrypt = validIndividuals;
                if (identifiersPresent) {
                    // get masked identifiers
                    List<Identifier> maskedIdentifiers = filterMaskedIdentifiers(validIndividuals);
                    // remove masked identifiers because we cannot encrypt them again
                    individualsToEncrypt = validIndividuals.stream().map(individual -> {
                        if (!maskedIdentifiers.isEmpty()) {
                            individual.getIdentifiers().removeAll(maskedIdentifiers
                                    .stream().filter(identifier ->
                                            identifier.getIndividualId().equals(individual.getId()))
                                    .collect(Collectors.toList()));
                        }
                        return individual;
                    }).collect(Collectors.toList());
                }

                // integrate with user service update call
                individualsToEncrypt = integrateWithUserService(request, individualsToEncrypt, ApiOperation.UPDATE, errorDetailsMap);

                // encrypt new data
                encryptedIndividualList = individualEncryptionService
                        .encrypt(request, individualsToEncrypt, "IndividualEncrypt", isBulk);


                Map<String, Individual> idToObjMap = getIdToObjMap(encryptedIndividualList);
                // find existing individuals from db
                List<Individual> existingIndividuals = individualRepository.findById(tenantId, new ArrayList<>(idToObjMap.keySet()),
                        "id", false).getResponse();

                if (identifiersPresent) {
                    // extract existing identifiers (encrypted) from existing individuals
                    Map<String, List<Identifier>> existingIdentifiers = existingIndividuals.stream()
                            .map(Individual::getIdentifiers)
                            .filter(Objects::nonNull)
                            .flatMap(Collection::stream).collect(Collectors.groupingBy(Identifier::getIndividualId));
                    // merge existing identifiers with new identifiers such that they all are encrypted alike
                    // this is because we cannot merge masked identifiers with new identifiers which are now encrypted
                    encryptedIndividualList.forEach(encryptedIndividual -> {
                        List<Identifier> newIdentifiers = encryptedIndividual.getIdentifiers();
                        List<String> newIdentifiersIds = getIdList(newIdentifiers);
                        List<Identifier> identifierList = existingIdentifiers.get(encryptedIndividual.getId()).stream()
                                .filter(identifier -> !newIdentifiersIds.contains(identifier.getId()))
                                .collect(Collectors.toList());

                        if (identifierList != null) {
                            newIdentifiers.addAll(identifierList);
                        }
                    });
                }
                // save
                individualRepository.save(encryptedIndividualList,
                        properties.getUpdateIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        //decrypt
        List<Individual> decryptedIndividualList = individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualDecrypt", request.getRequestInfo());
        return decryptedIndividualList;
    }

    private List<Identifier> filterMaskedIdentifiers(List<Individual> validIndividuals) {
        return validIndividuals.stream().map(Individual::getIdentifiers).filter(Objects::nonNull).flatMap(Collection::stream)
                .filter(identifier -> identifier.getIdentifierId().contains("*"))
                .collect(Collectors.toList());
    }

    public SearchResponse<Individual> search(IndividualSearch individualSearch,
                                             Integer limit,
                                             Integer offset,
                                             String tenantId,
                                             Long lastChangedSince,
                                             Boolean includeDeleted,
                                             RequestInfo requestInfo) {
        SearchResponse<Individual> searchResponse = null;

        String idFieldName = getIdFieldName(individualSearch);
        List<Individual> encryptedIndividualList = null;
        if (isSearchByIdOnly(individualSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(individualSearch)),
                    individualSearch);

            try {
                searchResponse = individualRepository.findById(tenantId ,ids, idFieldName, includeDeleted);
            } catch (InvalidTenantIdException e) {
                throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
            }

            encryptedIndividualList = searchResponse.getResponse().stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
            //decrypt
            List<Individual> decryptedIndividualList = (!encryptedIndividualList.isEmpty())
                    ? individualEncryptionService.decrypt(encryptedIndividualList,
                    "IndividualDecrypt", requestInfo)
                    : encryptedIndividualList;

            searchResponse.setResponse(decryptedIndividualList);

            return searchResponse;
        }
        //encrypt search criteria

        IndividualSearch encryptedIndividualSearch;
        if (individualSearch.getIdentifier() != null && individualSearch.getMobileNumber() == null) {
            encryptedIndividualSearch = individualEncryptionService
                    .encrypt(individualSearch, "IndividualSearchIdentifierEncrypt");
        } else if (individualSearch.getIdentifier() == null && individualSearch.getMobileNumber() != null) {
            encryptedIndividualSearch = individualEncryptionService
                    .encrypt(individualSearch, "IndividualSearchMobileNumberEncrypt");
        } else {
            encryptedIndividualSearch = individualEncryptionService
                    .encrypt(individualSearch, "IndividualSearchEncrypt");
        }
        try {
            searchResponse = individualRepository.find(encryptedIndividualSearch, limit, offset, tenantId,
                    lastChangedSince, includeDeleted);
            encryptedIndividualList = searchResponse.getResponse().stream()
                    .filter(havingBoundaryCode(individualSearch.getBoundaryCode(), individualSearch.getWardCode()))
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            log.error("database error occurred", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("DATABASE_ERROR", exception.getMessage());
        }
        //decrypt
        List<Individual> decryptedIndividualList =  (!encryptedIndividualList.isEmpty())
                ? individualEncryptionService.decrypt(encryptedIndividualList,
                "IndividualDecrypt", requestInfo)
                : encryptedIndividualList;

        searchResponse.setResponse(decryptedIndividualList);

        return searchResponse;
    }

    private Predicate<Individual> havingBoundaryCode(String boundaryCode, String wardCode) {
        if (boundaryCode == null && wardCode == null) {
            return individual -> true;
        }

        if (StringUtils.isNotBlank(wardCode)) {
            return individual -> individual.getAddress()
                    .stream()
                    .anyMatch(address -> (StringUtils.compare(wardCode, address.getWard().getCode()) == 0));
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
                // integrate with user service delete call
                validIndividuals = integrateWithUserService(request, validIndividuals, ApiOperation.DELETE, errorDetailsMap);
                individualRepository.save(validIndividuals,
                        properties.getDeleteIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", ExceptionUtils.getStackTrace(exception));
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

    private List<Individual> integrateWithUserService(IndividualBulkRequest request,
                                          List<Individual> individualList, ApiOperation apiOperation,
                                          Map<Individual, ErrorDetails> errorDetails) {
        List<Individual> validIndividuals = new ArrayList<>(individualList);
        if (properties.isUserSyncEnabled()) {
            for (Individual individual : individualList) {
                if (!Boolean.TRUE.equals(individual.getIsSystemUser())) continue;
                try {
                    if (apiOperation.equals(ApiOperation.UPDATE)) {
                        userIntegrationService.updateUser(individual, request.getRequestInfo());
                        log.info("successfully updated user for {} ",
                                individual.getName());
                    } else if (apiOperation.equals(ApiOperation.CREATE)) {
                        List<UserRequest> userRequests = userIntegrationService.createUser(individual,
                                request.getRequestInfo());
                            individual.setUserId(Long.toString(userRequests.get(0).getId()));
                            individual.setUserUuid(userRequests.get(0).getUuid());
                        log.info("successfully created user for {} ",
                                individual.getName());
                    } else {
                        userIntegrationService.deleteUser(Collections.singletonList(individual),
                                request.getRequestInfo());
                        log.info("successfully soft deleted user for {} ",
                                individual.getName());
                    }
                } catch (Exception exception) {
                    log.error("error occurred while creating user", ExceptionUtils.getStackTrace(exception));
                    Error error = Error.builder().errorMessage("User service exception")
                            .errorCode("USER_SERVICE_ERROR")
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException("USER_SERVICE_ERROR", "User service exception")).build();
                    Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
                    populateErrorDetails(individual, error, errorDetailsMap);
                    populateErrorDetails(request, errorDetails, errorDetailsMap, SET_INDIVIDUALS);
                    validIndividuals.remove(individual);
                }
            }
        }
        return validIndividuals;
    }
    Boolean isSmsEnabledForRole(IndividualRequest request) {
        if (CollectionUtils.isEmpty(properties.getSmsDisabledRoles()))
            return true;
        List<String> smsDisabledRoles = properties.getSmsDisabledRoles();
        List<String> roleCodes = new ArrayList<>();
        if(request != null && request.getIndividual() != null && request.getIndividual().getUserDetails() != null
                && request.getIndividual().getUserDetails().getRoles() != null) {
            // get the role codes from the list of roles
            roleCodes = request.getIndividual().getUserDetails().getRoles().stream().map(Role::getCode).collect(Collectors.toList());
        }
        for (String smsDisabledRole : smsDisabledRoles) {
            if (roleCodes.contains(smsDisabledRole))
                return false;
        }
        return true;
    }

    /* * This method is used to trigger Aadhaar OTP requests for individuals
     * It checks if the individual has an Aadhaar identifier and if so, sends an OTP request
     */
    public Individual verifyAbhaOtp(AbhaOtpVerifyRequest request) {
        String tenantId = request.getRequestInfo().getUserInfo().getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }
        String individualId = request.getIndividualId();

        log.info("Starting ABHA OTP verification for individualId: {}, tenantId: {}", individualId, tenantId);

        String txnId = fetchTxnId(individualId, tenantId);
        AbhaGatewayOtpVerifyRequest externalRequest = buildGatewayOtpRequest(txnId, request);
        String abhaNumber = fetchVerifiedAbhaNumber(externalRequest, individualId);

        IndividualSearch searchCriteria = IndividualSearch.builder()
                .individualId(Collections.singletonList(individualId))
                .build();

        SearchResponse<Individual> response = search(searchCriteria, 10, 0, tenantId, null, false, request.getRequestInfo());

        if (response.getResponse() == null || response.getResponse().isEmpty()) {
            throw new CustomException("INDIVIDUAL_NOT_FOUND", "Individual not found for ID: " + individualId);
        }

        Individual individual = response.getResponse().get(0);
        updateIndividualWithAbha(individual, abhaNumber);
        List<Individual> updated = persistUpdatedIndividual(individual, request.getRequestInfo());
        cleanUpAbhaTransaction(individualId, tenantId, request.getRequestInfo());

        log.info("Completed ABHA OTP verification for individualId: {}", individualId);
        return updated.get(0);
    }

    private String fetchTxnId(String individualId, String tenantId) {
        SearchResponse<AbhaTransaction> txnResponse = abhaRepository.findByIndividualId(individualId, tenantId);
        if (txnResponse.getResponse() == null || txnResponse.getResponse().isEmpty()) {
            log.error("No ABHA transaction found for individualId: {}", individualId);
            throw new CustomException(TXN_ID_NOT_FOUND, "No ABHA transaction found for individualId: " + individualId);
        }

        String txnId = txnResponse.getResponse().get(0).getTransactionId();
        if (txnId == null || txnId.isEmpty()) {
            log.error("Transaction ID is missing for individualId: {}", individualId);
            throw new CustomException(TXN_ID_MISSING, "Transaction ID is missing in AbhaTransaction");
        }

        log.info("Fetched txnId: {} for individualId: {}", txnId, individualId);
        return txnId;
    }

    private AbhaGatewayOtpVerifyRequest buildGatewayOtpRequest(String txnId, org.egov.common.models.individual.AbhaOtpVerifyRequest request) {
        return AbhaGatewayOtpVerifyRequest.builder()
                .txnId(txnId)
                .otp(request.getOtp())
                .mobile(request.getMobile())
                .build();
    }

    private String fetchVerifiedAbhaNumber(AbhaGatewayOtpVerifyRequest request, String individualId) {
        log.info("Verifying ABHA OTP with txnId: {}", request.getTxnId());
        AbhaGatewayOtpVerifyResponse response = abhaService.verifyAadhaarOtp(request);

        if (response == null || response.getAbhaProfile() == null || response.getAbhaProfile().getAbhaNumber() == null) {
            log.error("Failed ABHA verification for individualId: {}", individualId);
            throw new CustomException(VALIDATION_ERROR, "ABHA verification failed or response was invalid");
        }

        String abhaNumber = response.getAbhaProfile().getAbhaNumber();
        log.info("Received verified ABHA number: {} for individualId: {}", abhaNumber, individualId);
        return abhaNumber;
    }

    private Individual fetchIndividual(String individualId, String tenantId, RequestInfo requestInfo) {
        List<String> ids = Collections.singletonList(individualId);
        try {
            List<Individual> result = individualRepository.findById(tenantId, ids, "individualId", false).getResponse();
            if (result == null || result.isEmpty()) {
                throw new CustomException("INDIVIDUAL_NOT_FOUND", "Individual not found for ID: " + individualId);
            }
            return individualEncryptionService.decrypt(result, "IndividualDecrypt", requestInfo).get(0);
        } catch (Exception e) {
            log.error("Failed to fetch individual | individualId: {}, error: {}", individualId, e.getMessage());
            throw new CustomException("FETCH_FAILED", "Failed to fetch individual for update");
        }
    }

    private void updateIndividualWithAbha(Individual individual, String abhaNumber) {
        List<Identifier> identifiers = Optional.ofNullable(individual.getIdentifiers()).orElse(new ArrayList<>());

        boolean alreadyExists = identifiers.stream()
                .anyMatch(id -> ABHA_IDENTIFIER.equalsIgnoreCase(id.getIdentifierType()) && abhaNumber.equals(id.getIdentifierId()));

        if (!alreadyExists) {
            log.info("Appending ABHA identifier to individual: {}", individual.getId());
            identifiers.add(Identifier.builder()
                    .identifierType(ABHA_IDENTIFIER)
                    .identifierId(abhaNumber)
                    .isDeleted(false)
                    .build());
            individual.setIdentifiers(identifiers);
        } else {
            log.info("ABHA identifier already exists for individual: {}", individual.getId());
        }
    }

    private List<Individual> persistUpdatedIndividual(Individual individual, RequestInfo requestInfo) {
        IndividualRequest updateRequest = IndividualRequest.builder()
                .individual(individual)
                .requestInfo(requestInfo)
                .build();

        log.info("Persisting updated Individual with ABHA number for id: {}", individual.getId());
        return update(updateRequest);
    }

    private void cleanUpAbhaTransaction(String individualId, String tenantId , RequestInfo requestInfo) {
        log.info("Deleting ABHA transaction log for individualId: {}", individualId);
        abhaRepository.deleteByIndividualId(individualId, tenantId, requestInfo.getUserInfo().getUuid());
    }


    public void triggerAadhaarOtpRequests(List<Individual> validIndividuals, RequestInfo requestInfo) {
        List<AbhaTransaction> abhaTransactions = new ArrayList<>();

        for (Individual individual : validIndividuals) {
            extractAadhaarIdentifier(individual, true ).ifPresent(aadhaar -> {
                AbhaOtpRequest otpRequest = AbhaOtpRequest.builder()
                        .aadhaarNumber(aadhaar)
                        .build();

                AbhaOtpResponse response = abhaService.sendAadhaarOtp(otpRequest);
                String txnId = response.getTxnId();

                AbhaTransaction abhaTxn = AbhaTransaction.builder()
                        .individualId(individual.getIndividualId())
                        .tenantId(individual.getTenantId())
                        .transactionId(txnId)
                        .build();

                abhaTransactions.add(abhaTxn);
            });
        }

        if (!abhaTransactions.isEmpty()) {
            enrichmentService.enrichAbhaTransactions(abhaTransactions, requestInfo);
            abhaRepository.save(abhaTransactions, properties.getSaveAbhaTransactionTopic());
        }
    }

    /**
     * Extracts the Aadhaar identifier from the given Individual.
     *
     * @param individual the Individual entity
     * @param requireAbhaCreationFlag if true, will only return Aadhaar where enableAbhaCreation is true;
     *                                if false, will return Aadhaar regardless of enableAbhaCreation
     * @return Optional containing the Aadhaar identifier if found, otherwise empty
     */
    private Optional<String> extractAadhaarIdentifier(Individual individual, boolean requireAbhaCreationFlag) {
        return Optional.ofNullable(individual.getIdentifiers())
                .orElse(Collections.emptyList())
                .stream()
                .filter(id -> AADHAR_IDENTIFIER.equalsIgnoreCase(id.getIdentifierType())
                        && (!requireAbhaCreationFlag || Boolean.TRUE.equals(id.getEnableAbhaCreation())))
                .map(Identifier::getIdentifierId)
                .findFirst();
    }


    /**
     * Re-triggers Aadhaar OTP for the given individual.
     * 1) Validates tenantId
     * 2) Fetches Individual (search logic same style as verifyAbhaOtp)
     * 3) Extracts Aadhaar
     * 4) Calls ABHA sendAadhaarOtp
     * 5) Enriches & saves AbhaTransaction (Kafka)
     * 6) Returns txnId
     */
    public String resendAbhaOtp(AbhaOtpResendRequest request) {
        RequestInfo requestInfo = request.getRequestInfo();

        String tenantId = Optional.ofNullable(requestInfo)
                .map(ri -> ri.getUserInfo() != null ? ri.getUserInfo().getTenantId() : null)
                .orElse(null);

        if (tenantId == null || tenantId.isBlank()) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        final String individualId = request.getIndividualId();
        final String aadhaarNumber = request.getAadhaarNumber();
        log.info("Re-triggering ABHA OTP for individualId: {}, tenantId: {}", individualId, tenantId);

        // (A) Strict lookup by BOTH individualId + Aadhaar
        Individual individual = fetchByIndividualIdAndAadhaarOrThrow(individualId, tenantId, aadhaarNumber, requestInfo);

        // (B) Send OTP using provided Aadhaar
        AbhaOtpRequest otpRequest = AbhaOtpRequest.builder()
                .aadhaarNumber(aadhaarNumber)
                .build();


        // (C) Ensure an existing txn exists; otherwise fail (resend must not create)
        SearchResponse<AbhaTransaction> existingResp = abhaRepository.findByIndividualId(individualId, tenantId);
        if (existingResp.getResponse() == null || existingResp.getResponse().isEmpty()) {
            throw new CustomException(
                    "ABHA_TXN_NOT_FOUND",
                    "No existing ABHA OTP transaction found for individualId: " + individualId +
                            ". Trigger the initial OTP via the create flow."
            );
        }
        AbhaTransaction current = existingResp.getResponse().get(0);
        
        AbhaOtpResponse otpResponse = abhaService.sendAadhaarOtp(otpRequest);
        String txnId = otpResponse.getTxnId();
        
        // (D) Upsert update (same row), revive if soft-deleted
        AbhaTransaction abhaTxn = AbhaTransaction.builder()
                .id(current.getId())
                .individualId(individualId)
                .tenantId(tenantId)
                .transactionId(txnId)
                .additionalDetails(current.getAdditionalDetails())
                .isDeleted(Boolean.FALSE)
                .rowVersion(current.getRowVersion())
                .build();

        List<AbhaTransaction> batch = Collections.singletonList(abhaTxn);
        enrichmentService.enrichAbhaTransactionForUpdate(batch, requestInfo);
        abhaRepository.save(batch, properties.getSaveAbhaTransactionTopic()); // ON CONFLICT (individualId, tenantId) DO UPDATE

        log.info("ABHA txn UPDATED via resend; individualId={}, oldTxnId={}, newTxnId={}",
                individualId, current.getTransactionId(), txnId);
        return txnId;
    }

    // ---------- Helpers (same style as in your verify flow) ----------

    private Individual fetchByIndividualIdAndAadhaarOrThrow(String individualId,
                                                            String tenantId,
                                                            String aadhaarNumber,
                                                            RequestInfo requestInfo) {
        // ---- 1) Search by Individual ID only ----
        IndividualSearch byId = IndividualSearch.builder()
                .individualId(Collections.singletonList(individualId))
                .build();

        SearchResponse<Individual> idResp = search(byId, 1, 0, tenantId, null, false, requestInfo);
        if (idResp.getResponse() == null || idResp.getResponse().isEmpty()) {
            throw new CustomException(
                    "INDIVIDUAL_NOT_FOUND",
                    "Individual not found for ID: " + individualId
            );
        }
        Individual idHit = idResp.getResponse().get(0);

        // ---- 2) Search by Aadhaar identifier only (triggers your IdentifierEncrypt path) ----
        Identifier aadhaarFilter = Identifier.builder()
                .identifierType(AADHAR_IDENTIFIER) // "AADHAAR"
                .identifierId(aadhaarNumber)
                .build();

        IndividualSearch byAadhaar = IndividualSearch.builder()
                .identifier(aadhaarFilter)
                .build();

        SearchResponse<Individual> idfResp = search(byAadhaar, 10, 0, tenantId, null, false, requestInfo);
        if (idfResp.getResponse() == null || idfResp.getResponse().isEmpty()) {
            throw new CustomException(
                    "AADHAAR_NOT_FOUND",
                    "No individual found with the provided Aadhaar number."
            );
        }

        // ---- 3) Validate that Aadhaar belongs to the same individualId ----
        // pick the record from identifier search whose individualId matches the requested one
        Individual match = idfResp.getResponse()
                .stream()
                .filter(ind -> individualId.equals(ind.getIndividualId()))
                .findFirst()
                .orElse(null);

        if (match == null) {
            throw new CustomException(
                    "INDIVIDUAL_AADHAAR_MISMATCH",
                    "The provided Aadhaar does not belong to individualId: " + individualId
            );
        }

        // (Optional) If you require enableAbhaCreation=true on the identifier, enforce it here by checking match.getIdentifiers()

        return idHit; // or 'match' (both refer to the same Individual by now)
    }

}
