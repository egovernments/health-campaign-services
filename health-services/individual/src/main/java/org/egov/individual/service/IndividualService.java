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
import org.egov.individual.util.OtpUtil;
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
import org.egov.individual.web.models.register.IndividualRegisterRequest;
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

    private final OtpUtil otpUtil;

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
                             BeneficiaryIdGenUtil beneficiaryIdGenUtil,
                             OtpUtil otpUtil) {
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.properties = properties;
        this.enrichmentService = enrichmentService;
        this.individualEncryptionService = individualEncryptionService;
        this.userIntegrationService = userIntegrationService;
        this.notificationService = notificationService;
        this.beneficiaryIdGenUtil = beneficiaryIdGenUtil;
        this.otpUtil = otpUtil;
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
        return integrateWithUserService(request, individualList, apiOperation, errorDetails, true);
    }

    private List<Individual> integrateWithUserService(IndividualBulkRequest request,
                                          List<Individual> individualList, ApiOperation apiOperation,
                                          Map<Individual, ErrorDetails> errorDetails, boolean generateDummyMobile) {
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
                                request.getRequestInfo(), generateDummyMobile);
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

    public List<Individual> registerIndividualWithUser(IndividualRegisterRequest request) {
        // Extract the IndividualRegister data
        var registerData = request.getIndividualRegister();

        // Check and enrich RequestInfo with userInfo if missing
        if (request.getRequestInfo().getUserInfo() == null ||
            StringUtils.isBlank(request.getRequestInfo().getUserInfo().getUuid())) {
            log.info("UserInfo or UUID is missing, enriching with default system user");
            org.egov.common.contract.request.User systemUser = org.egov.common.contract.request.User.builder()
                    .uuid("ff1c0f86-d362-4420-b93b-fec4714cc604")
                    .build();
            request.getRequestInfo().setUserInfo(systemUser);
        }

        // Validate that at least one contact method is present
        if (StringUtils.isBlank(registerData.getMobileNumber()) && StringUtils.isBlank(registerData.getEmailId())) {
            throw new CustomException("CONTACT_REQUIRED", "At least one contact method (mobile number or email) is required for registration");
        }

        // Search for existing individual before creating a new one
        IndividualSearch.IndividualSearchBuilder searchBuilder = IndividualSearch.builder();

        // Search by mobile number if present, otherwise by username (email)
        if (StringUtils.isNotBlank(registerData.getMobileNumber())) {
            searchBuilder.mobileNumber(Collections.singletonList(registerData.getMobileNumber()));
        } else if (StringUtils.isNotBlank(registerData.getEmailId())) {
            searchBuilder.username(Collections.singletonList(registerData.getEmailId()));
        }

        IndividualSearch individualSearch = searchBuilder.build();

        SearchResponse<Individual> searchResponse = search(
                individualSearch,
                1, // limit
                0, // offset
                registerData.getTenantId(),
                null, // lastChangedSince
                false, // includeDeleted
                request.getRequestInfo()
        );

        // If individual exists
        if (searchResponse != null && !CollectionUtils.isEmpty(searchResponse.getResponse())) {
            Individual existingIndividual = searchResponse.getResponse().get(0);
            log.info("Found existing individual with id: {}", existingIndividual.getId());

            // If RequestType is "Login", update to activate the user
            if (StringUtils.isNotBlank(registerData.getRequestType())
                    && "Login".equalsIgnoreCase(registerData.getRequestType())) {
                log.info("RequestType is Login, validating OTP");

                // Check if OTP is provided
                if (StringUtils.isBlank(registerData.getOtp())) {
                    log.error("OTP is required for login but not provided");
                    throw new CustomException("OTP_REQUIRED", "OTP is required for login");
                }

                // Validate OTP before activating user
                if (!otpUtil.validateOtp(request)) {
                    log.error("OTP validation failed for user");
                    throw new CustomException("INVALID_OTP", "The OTP provided is invalid or has expired. Please request a new OTP.");
                }

                log.info("OTP validation successful, activating user");

                // Update isSystemUserActive to true
                existingIndividual.setIsSystemUserActive(true);

                IndividualRequest updateRequest = IndividualRequest.builder()
                        .requestInfo(request.getRequestInfo())
                        .individual(existingIndividual)
                        .build();

                return update(updateRequest);
            }

            // For non-Login RequestType, return existing individual without modification
            return Collections.singletonList(existingIndividual);
        }

        // Individual doesn't exist, proceed with creation
        log.info("Individual not found, proceeding with creation");

        // Determine username: use mobileNumber if present, otherwise email
        String username = StringUtils.isNotBlank(registerData.getMobileNumber())
                ? registerData.getMobileNumber()
                : registerData.getEmailId();

        // Build UserDetails with STUDIO_CITIZEN role
        org.egov.common.models.individual.UserDetails userDetails =
                org.egov.common.models.individual.UserDetails.builder()
                .username(username)
                .tenantId(registerData.getTenantId())
                .roles(Collections.singletonList(
                        Role.builder()
                                .name(properties.getRole())
                                .code(properties.getRole())
                                .tenantId(registerData.getTenantId())
                                .build()
                ))
                .userType(UserType.CITIZEN)
                .build();

        // Build the Individual object from IndividualRegister
        Individual.IndividualBuilder individualBuilder = Individual.builder()
                .clientReferenceId(UUID.randomUUID().toString())
                .tenantId(registerData.getTenantId())
                .name(org.egov.common.models.individual.Name.builder()
                        .givenName(registerData.getName())
                        .build())
                .isSystemUser(true)
                .isSystemUserActive(true)
                .userDetails(userDetails);

        // Conditionally add email if present
        if (StringUtils.isNotBlank(registerData.getEmailId())) {
            individualBuilder.email(registerData.getEmailId());
        }

        // Conditionally add mobileNumber if present
        if (StringUtils.isNotBlank(registerData.getMobileNumber())) {
            individualBuilder.mobileNumber(registerData.getMobileNumber());
        }

        Individual individual = individualBuilder.build();

        // Build IndividualRequest with RequestInfo and Individual
        IndividualRequest individualRequest = IndividualRequest.builder()
                .requestInfo(request.getRequestInfo())
                .individual(individual)
                .build();

        // Call the existing create method with generateDummyMobile = false for register API
        List<Individual> individuals;
        log.info("individualRequest:"+individualRequest);
        try {
            individuals = create(individualRequest, false);
        } catch (Exception e) {
            log.error("Failed to create individual during registration: {}", e.getMessage(), e);
            throw e; // Re-throw exception without calling sendOtp
        }

        // Only send OTP if individual creation was successful
        try {
            otpUtil.sendOtp(request);
        } catch (Exception e) {
            log.error("Failed to send OTP after individual creation: {}", e.getMessage(), e);
            // Individual is already created, so we can choose to continue or throw
            // Throwing here so the user is aware OTP wasn't sent
            throw new CustomException("OTP_SEND_FAILED",
                    "Individual created successfully but failed to send OTP: " + e.getMessage());
        }

        return individuals;
    }
}
