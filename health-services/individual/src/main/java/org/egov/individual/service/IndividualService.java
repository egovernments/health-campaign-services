package org.egov.individual.service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.user.enums.UserType;
import org.egov.common.ds.Tuple;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.core.Role;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualRequest;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.project.ApiOperation;
import org.egov.common.models.user.UserRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.util.BeneficiaryIdGenUtil;
import org.egov.individual.validators.AadharNumberValidator;
import org.egov.individual.validators.AadharNumberValidatorForCreate;
import org.egov.individual.validators.AddressTypeValidator;
import org.egov.individual.validators.IBoundaryValidator;
import org.egov.individual.validators.IExistentEntityValidator;
import org.egov.individual.validators.IdPoolValidatorForCreate;
import org.egov.individual.validators.IdPoolValidatorForUpdate;
import org.egov.individual.validators.IsDeletedSubEntityValidator;
import org.egov.individual.validators.IsDeletedValidator;
import org.egov.individual.validators.MobileNumberValidator;
import org.egov.individual.validators.NonExistentEntityValidator;
import org.egov.individual.validators.NullIdValidator;
import org.egov.individual.validators.RowVersionValidator;
import org.egov.individual.validators.UniqueEntityValidator;
import org.egov.individual.validators.UniqueSubEntityValidator;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
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
            ;

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForCreate = validator ->
            validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(IExistentEntityValidator.class)
                    || validator.getClass().equals(IBoundaryValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class)
                    || validator.getClass().equals(MobileNumberValidator.class)
                    || validator.getClass().equals(AadharNumberValidatorForCreate.class)
                    || validator.getClass().equals(IdPoolValidatorForCreate.class)
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
                             BeneficiaryIdGenUtil beneficiaryIdGenUtil) {
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.properties = properties;
        this.enrichmentService = enrichmentService;
        this.individualEncryptionService = individualEncryptionService;
        this.userIntegrationService = userIntegrationService;
        this.notificationService = notificationService;
        this.beneficiaryIdGenUtil = beneficiaryIdGenUtil;
    }

    public List<Individual> create(IndividualRequest request) {
        return create(request, true);
    }

    public List<Individual> create(IndividualRequest request, boolean generateDummyMobile) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        List<Individual> individuals = create(bulkRequest, false, generateDummyMobile);

        // check if sms feature is enable for the environment role
        if(properties.getIsSMSEnabled() && isSmsEnabledForRole(request))
            notificationService.sendNotification(request, true);
        return individuals;
    }

    public List<Individual> create(IndividualBulkRequest request, boolean isBulk) {
        return create(request, isBulk, true);
    }

    public List<Individual> create(IndividualBulkRequest request, boolean isBulk, boolean generateDummyMobile) {
        // Preserved signature — errors are still processed via handleErrors (Kafka
        // error topic in bulk mode). Callers that want to inspect per-record errors
        // synchronously should use the overload below.
        return create(request, isBulk, generateDummyMobile, new HashMap<>());
    }

    /**
     * Same as {@link #create(IndividualBulkRequest, boolean, boolean)} but writes
     * the collected per-record error details into {@code errorDetailsMapOut} for
     * the caller to inspect BEFORE {@code handleErrors} routes them to the Kafka
     * error topic. Used by the synchronous controller branch to include a rich
     * errors array in the HTTP response.
     */
    public List<Individual> create(IndividualBulkRequest request, boolean isBulk, boolean generateDummyMobile,
                                   Map<Individual, ErrorDetails> errorDetailsMapOut) {

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
                validIndividuals = integrateWithUserService(request, validIndividuals, ApiOperation.CREATE, errorDetailsMap, generateDummyMobile);
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
                if (!validIndividuals.isEmpty()) {
                    encryptedIndividualList = individualEncryptionService
                            .encrypt(request, validIndividuals, "IndividualEncrypt", isBulk);
                    individualRepository.save(encryptedIndividualList,
                            properties.getSaveIndividualTopic());
                    // update beneficiary ids in idgen
                    if (properties.getBeneficiaryIdGenIntegrationEnabled()) {
                        beneficiaryIdGenUtil.updateBeneficiaryIds(beneficiaryIds, validIndividuals.get(0).getTenantId(), request.getRequestInfo());
                    }
                }
            }
        } catch (CustomException exception) {
            log.error("error occurred", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        // Copy the collected errors to the caller's map BEFORE handleErrors
        // (which pushes them to the Kafka error topic in bulk mode and then
        // returns without preserving them in-memory).
        errorDetailsMapOut.putAll(errorDetailsMap);

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

                List<String> beneficiaryIdsToUpdate = individualsToEncrypt.stream()
                        .flatMap(individual -> Optional.ofNullable(individual.getIdentifiers()).
                                orElse(Collections.emptyList()).stream())
                        .filter(identifier -> UNIQUE_BENEFICIARY_ID.equals(identifier.getIdentifierType()))
                        .map(Identifier::getIdentifierId)
                        .filter(identifierId -> !ObjectUtils.isEmpty(identifierId) && !identifierId.startsWith("*"))
                        .toList();

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
                if (properties.getBeneficiaryIdGenIntegrationEnabled()) {
                    // update beneficiary ids in idgen
                    beneficiaryIdGenUtil.updateBeneficiaryIds(beneficiaryIdsToUpdate, validIndividuals.get(0).getTenantId(), request.getRequestInfo());
                }
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
                    .anyMatch(address -> address.getWard() != null
                            && StringUtils.compare(wardCode, address.getWard().getCode()) == 0);
        }
        return individual -> individual.getAddress()
                .stream()
                .anyMatch(address -> address.getLocality() != null
                        && address.getLocality().getCode().equalsIgnoreCase(boundaryCode));

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
        return integrateWithUserService(request, individualList, apiOperation, errorDetails, true);
    }

    private List<Individual> integrateWithUserService(IndividualBulkRequest request,
                                          List<Individual> individualList, ApiOperation apiOperation,
                                          Map<Individual, ErrorDetails> errorDetails, boolean generateDummyMobile) {
        List<Individual> validIndividuals = new ArrayList<>(individualList);
        if (!properties.isUserSyncEnabled()) return validIndividuals;

        if (apiOperation.equals(ApiOperation.CREATE)) {
            integrateCreateBulk(request, individualList, errorDetails, validIndividuals, generateDummyMobile);
        } else {
            // UPDATE and DELETE still go per-individual — egov-user v2 only
            // ships bulk-create today; bulk update/delete are follow-up work.
            for (Individual individual : individualList) {
                if (!Boolean.TRUE.equals(individual.getIsSystemUser())) continue;
                try {
                    if (apiOperation.equals(ApiOperation.UPDATE)) {
                        userIntegrationService.updateUser(individual, request.getRequestInfo());
                        log.info("successfully updated user for {} ", individual.getName());
                    } else {
                        userIntegrationService.deleteUser(Collections.singletonList(individual),
                                request.getRequestInfo());
                        log.info("successfully soft deleted user for {} ", individual.getName());
                    }
                } catch (Exception exception) {
                    log.error("error occurred in user service call", ExceptionUtils.getStackTrace(exception));
                    String code = "INDIVIDUAL_USER_SERVICE_" + apiOperation + "_ERROR";
                    String msg = String.format(
                            "User service %s call failed for individual [id=%s, userId=%s, userUuid=%s, "
                                    + "clientReferenceId=%s, mobileNumber=%s]: exceptionClass=%s, exceptionMessage=%s",
                            apiOperation, individual.getId(), individual.getUserId(), individual.getUserUuid(),
                            individual.getClientReferenceId(), individual.getMobileNumber(),
                            exception.getClass().getSimpleName(),
                            exception.getMessage() != null ? exception.getMessage() : "(no message)");
                    recordUserServiceError(request, errorDetails, validIndividuals, individual, code, msg);
                }
            }
        }
        return validIndividuals;
    }

    /**
     * Batch-create all system users for the individuals in a single HTTP call
     * to egov-user's v2 bulk-create endpoint. On return, each individual whose
     * matching response entry has {@code id != null} is stamped with the
     * returned userId/userUuid. Duplicates (v2 returns {@code id == null}) and
     * any transport-level failure are recorded via {@code populateErrorDetails}
     * and removed from {@code validIndividuals}.
     */
    private void integrateCreateBulk(IndividualBulkRequest request,
                                     List<Individual> individualList,
                                     Map<Individual, ErrorDetails> errorDetails,
                                     List<Individual> validIndividuals,
                                     boolean generateDummyMobile) {
        List<Individual> toCreate = individualList.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsSystemUser()))
                .collect(Collectors.toList());
        if (toCreate.isEmpty()) return;

        UserIntegrationService.BulkUserResult result;
        try {
            result = userIntegrationService.createUsersBulk(toCreate, request.getRequestInfo(), generateDummyMobile);
        } catch (Exception e) {
            log.error("bulk user create failed", ExceptionUtils.getStackTrace(e));
            String transportMsg = String.format(
                    "Downstream egov-user v2/_create call failed for individual-batch of size=%d: "
                            + "exceptionClass=%s, exceptionMessage=%s. All %d individuals are marked as failed. "
                            + "Check that egov-user is reachable at the configured URL and healthy.",
                    toCreate.size(), e.getClass().getSimpleName(),
                    e.getMessage() != null ? e.getMessage() : "(no message)", toCreate.size());
            for (Individual individual : toCreate) {
                recordUserServiceError(request, errorDetails, validIndividuals, individual,
                        "INDIVIDUAL_BULK_CREATE_USER_SERVICE_TRANSPORT_ERROR", transportMsg);
            }
            return;
        }

        // Correlate saved users AND downstream errors by username.
        Map<String, Map<String, Object>> byUsername = new HashMap<>();
        for (Map<String, Object> u : result.users) {
            Object uname = u.get("username");
            if (uname != null) byUsername.put(String.valueOf(uname), u);
        }
        Map<String, Map<String, Object>> errorByUsername = new HashMap<>();
        for (Map<String, Object> e : result.errors) {
            Object uname = e.get("username");
            if (uname != null) errorByUsername.put(String.valueOf(uname), e);
        }

        for (Individual individual : toCreate) {
            String expectedUsername = individual.getUserDetails() != null
                    ? individual.getUserDetails().getUsername() : null;
            Map<String, Object> savedUser = expectedUsername == null ? null : byUsername.get(expectedUsername);
            if (savedUser != null && savedUser.get("id") != null) {
                individual.setUserId(String.valueOf(savedUser.get("id")));
                individual.setUserUuid((String) savedUser.get("uuid"));
                log.info("bulk-created user for username={} individualClientRef={}",
                        expectedUsername, individual.getClientReferenceId());
                continue;
            }

            // Not created — propagate the exact downstream code+message with field values.
            Map<String, Object> downstreamErr = expectedUsername == null ? null : errorByUsername.get(expectedUsername);
            String code;
            String message;
            String mobile = individual.getMobileNumber();
            String indId = individual.getId();
            String cliRef = individual.getClientReferenceId();
            if (downstreamErr != null && downstreamErr.get("code") != null) {
                // egov-user gave us a specific reason (dedup, etc.) — surface it verbatim
                // and add Individual-level context so callers can trace.
                code = "INDIVIDUAL_BULK_CREATE_" + downstreamErr.get("code");
                message = String.format(
                        "User creation was rejected by egov-user for username='%s' (individualClientRef=%s, mobileNumber=%s). "
                                + "Downstream code=%s. Downstream message: %s",
                        expectedUsername, cliRef, mobile,
                        downstreamErr.get("code"), downstreamErr.get("message"));
            } else {
                // Fallback — no per-user error came back but the id is missing
                code = "INDIVIDUAL_BULK_CREATE_USER_NOT_RETURNED";
                message = String.format(
                        "egov-user did not return an id for username='%s' (individualClientRef=%s, mobileNumber=%s, "
                                + "individualId=%s) and no matching error entry was present in the response. "
                                + "Likely causes: username collision without dedup metadata, or downstream INSERT rollback.",
                        expectedUsername, cliRef, mobile, indId);
            }
            log.warn("bulk create failed for username={} code={} message={}", expectedUsername, code, message);
            recordUserServiceError(request, errorDetails, validIndividuals, individual, code, message);
        }
    }

    private void recordUserServiceError(IndividualBulkRequest request,
                                        Map<Individual, ErrorDetails> errorDetails,
                                        List<Individual> validIndividuals,
                                        Individual individual,
                                        String errorCode,
                                        String errorMessage) {
        Error error = Error.builder().errorMessage(errorMessage)
                .errorCode(errorCode)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(errorCode, errorMessage)).build();
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        populateErrorDetails(individual, error, errorDetailsMap);
        populateErrorDetails(request, errorDetails, errorDetailsMap, SET_INDIVIDUALS);
        validIndividuals.remove(individual);
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
}
