package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.collectFromList;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForCreate;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class IndividualService {

    private final IdGenService idGenService;

    private final IndividualRepository individualRepository;

    private final List<Validator<IndividualBulkRequest, Individual>> validators;

    private final ObjectMapper objectMapper;

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForUpdate = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class)
                    || validator.getClass().equals(AddressTypeValidator.class)
                    || validator.getClass().equals(RowVersionValidator.class)
                    || validator.getClass().equals(UniqueEntityValidator.class);

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForCreate = validator ->
            validator.getClass().equals(AddressTypeValidator.class);

    @Autowired
    public IndividualService(IdGenService idGenService,
                             IndividualRepository individualRepository,
                             List<Validator<IndividualBulkRequest, Individual>> validators,
                             @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.objectMapper = objectMapper;
    }

    public List<Individual> create(IndividualRequest request) throws Exception {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return create(bulkRequest, false);
    }

    @KafkaListener(topics = "bulk")
    public List<Individual> bulkCreate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        IndividualBulkRequest request = objectMapper.convertValue(consumerRecord, IndividualBulkRequest.class);
        return create(request, true);
    }

    public List<Individual> create(IndividualBulkRequest request, boolean isBulk) throws Exception {

        log.info("Validating");
        Map<Individual, ErrorDetails> errorDetailsMap = validate(validators, isApplicableForCreate, request,
                "setIndividuals");
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException("VALIDATION_ERROR", errorDetailsMap.values().toString());
        }
        List<Individual> validIndividuals = request.getIndividuals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        try {
            if (!validIndividuals.isEmpty()) {
                log.info("Getting tenantId");
                final String tenantId = getTenantId(validIndividuals);
                log.info("Generating id for individuals");
                List<String> indIdList = idGenService.getIdList(request.getRequestInfo(),
                        tenantId, "individual.id",
                        null, validIndividuals.size());
                enrichForCreate(validIndividuals, indIdList, request.getRequestInfo());
                List<Address> addresses = collectFromList(validIndividuals,
                        Individual::getAddress);
                if (!addresses.isEmpty()) {
                    log.info("Enriching addresses");
                    List<String> addressIdList = uuidSupplier().apply(addresses.size());
                    enrichForCreate(addresses, addressIdList, request.getRequestInfo());
                    enrichIndividualIdInAddress(request);
                }
                log.info("Enriching identifiers");
                request.setIndividuals(validIndividuals.stream()
                        .map(IndividualService::enrichWithSystemGeneratedIdentifier)
                        .map(IndividualService::enrichIndividualIdInIdentifiers)
                        .collect(Collectors.toList()));
                List<Identifier> identifiers = collectFromList(validIndividuals,
                        Individual::getIdentifiers);
                enrichForCreateIdentifier(identifiers, request.getRequestInfo());
                if (validIndividuals.stream().anyMatch(individual -> individual.getIdentifiers().stream()
                        .anyMatch(identifier -> identifier.getIdentifierType().equals("SYSTEM_GENERATED")))) {
                    List<String> sysGenIdList = idGenService.getIdList(request.getRequestInfo(),
                            tenantId, "sys.gen.identifier.id",
                            null, identifiers.size());
                    enrichWithSysGenId(identifiers, sysGenIdList);
                }
                individualRepository.save(validIndividuals, "save-individual-topic");
            }
        } catch (Exception exception) {
            populateErrorDetailsForException(request, errorDetailsMap, validIndividuals, exception, "setIndividuals");
        }

        if (!errorDetailsMap.isEmpty()) {
            if (isBulk) {
                log.info("call tracer.handleErrors(), {}", errorDetailsMap.values());
            } else {
                throw new CustomException("VALIDATION_ERROR", errorDetailsMap.values().toString());
            }
        }

        return validIndividuals;
    }

    public List<Individual> update(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return update(bulkRequest, false);
    }

    @KafkaListener(topics = "bulk-update")
    public List<Individual> bulkUpdate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        IndividualBulkRequest request = objectMapper.convertValue(consumerRecord, IndividualBulkRequest.class);
        return update(request, true);
    }

    public List<Individual> update(IndividualBulkRequest request, boolean isBulk) {
        Map<Individual, ErrorDetails> errorDetailsMap = validate(validators, isApplicableForUpdate, request,
                "setIndividuals");

        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException("VALIDATION_ERROR", errorDetailsMap.values().toString());
        }

        List<Individual> validIndividuals = request.getIndividuals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());

        try {
            if (!validIndividuals.isEmpty()) {
                validIndividuals.forEach(
                        individual -> {
                            AuditDetails auditDetails = getAuditDetails(request);
                            enrichAddress(request, individual, auditDetails);
                            enrichIdentifier(individual, auditDetails);
                        }
                );
                Map<String, Individual> iMap = getIdToObjMap(validIndividuals);
                log.info("Updating lastModifiedTime and lastModifiedBy");
                enrichForUpdate(iMap, request);
                individualRepository.save(validIndividuals, "update-individual-topic");
            }
        } catch (Exception exception) {
            populateErrorDetailsForException(request, errorDetailsMap, validIndividuals, exception, "setIndividuals");
        }

        if (!errorDetailsMap.isEmpty()) {
            if (isBulk) {
                log.info("call tracer.handleErrors(), {}", errorDetailsMap.values());
            } else {
                throw new CustomException("VALIDATION_ERROR", errorDetailsMap.values().toString());
            }
        }

        return validIndividuals;
    }

    private static <T, R> Map<T, ErrorDetails> validate(List<Validator<R, T>> validators,
                                                        Predicate<Validator<R, T>> applicableValidators,
                                                        R request,
                                                        String payloadMethodName) {
        Map<T, ErrorDetails> errorDetailsMap = new HashMap<>();
        validators.stream().filter(applicableValidators)
                .map(validator -> validator.validate(request))
                .forEach(e -> populateErrorDetailsGeneric(request, errorDetailsMap, e,
                        payloadMethodName));
        return errorDetailsMap;
    }

    private static <T, R> void populateErrorDetailsGeneric(R request,
                                                           Map<T, ErrorDetails> errorDetailsMap,
                                                           Map<T, List<Error>> errorMap,
                                                           String setPayloadMethodName) {
        try {
            for (Map.Entry<T, List<Error>> entry : errorMap.entrySet()) {
                T payload = entry.getKey();
                if (errorDetailsMap.containsKey(payload)) {
                    errorDetailsMap.get(payload).getErrors().addAll(entry.getValue());
                } else {
                    RequestInfo requestInfo = (RequestInfo) ReflectionUtils
                            .invokeMethod(getMethod("getRequestInfo", request.getClass()), request);
                    R newRequest = (R) ReflectionUtils.accessibleConstructor(request.getClass(), null).newInstance();
                    ReflectionUtils.invokeMethod(getMethod("setRequestInfo", newRequest.getClass()), newRequest, requestInfo);
                    ReflectionUtils.invokeMethod(getMethod(setPayloadMethodName, newRequest.getClass()), newRequest,
                            Collections.singletonList(payload));
                    ApiDetails apiDetails = ApiDetails.builder()
                            .methodType(HttpMethod.POST.name())
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .url(requestInfo.getApiId()).build();
                    apiDetails.setRequestBody(newRequest);
                    ErrorDetails errorDetails = ErrorDetails.builder()
                            .errors(entry.getValue())
                            .apiDetails(apiDetails)
                            .build();
                    errorDetailsMap.put(payload, errorDetails);
                }
            }
        } catch (Exception exception) {
            log.error("failure in error handling", exception);
            throw new CustomException("FAILURE_IN_ERROR_HANDLING", exception.getMessage());
        }
    }


    private static <R,T> void populateErrorDetailsForException(R request, Map<T, ErrorDetails> errorDetailsMap,
                                                               List<T> validIndividuals, Exception exception,
                                                               String payloadMethodName) {
        Error.ErrorType errorType = Error.ErrorType.NON_RECOVERABLE;
        String errorCode = "INTERNAL_SERVER_ERROR";
        if (exception instanceof CustomException) {
            errorCode = ((CustomException) exception).getCode();
            if (!((CustomException) exception).getCode().equals("IDGEN_ERROR")) {
                errorType = Error.ErrorType.RECOVERABLE;
            }
        }
        List<Error> errorList = new ArrayList<>();
        errorList.add(Error.builder().errorMessage(exception.getMessage())
                .errorCode(errorCode)
                .type(errorType)
                .exception(new CustomException(errorCode, exception.getMessage())).build());
        Map<T, List<Error>> errorListMap = new HashMap<>();
        validIndividuals.forEach(payload -> {
            errorListMap.put(payload, errorList);
            populateErrorDetailsGeneric(request, errorDetailsMap, errorListMap, payloadMethodName);
        });
    }

    private void enrichForCreateIdentifier(List<Identifier> identifiers, RequestInfo requestInfo) {
        AuditDetails auditDetails = getAuditDetailsForCreate(requestInfo);
        identifiers.forEach(identifier -> {
            identifier.setAuditDetails(auditDetails);
            identifier.setIsDeleted(Boolean.FALSE);
        });
    }

    private static void enrichWithSysGenId(List<Identifier> identifiers, List<String> sysGenIdList) {
        IntStream.range(0, identifiers.size()).forEach(i -> {
            if (identifiers.get(i).getIdentifierType().equals("SYSTEM_GENERATED")) {
                identifiers.get(i).setIdentifierId(sysGenIdList.get(i));
            }
        });
    }

    private static Individual enrichIndividualIdInIdentifiers(Individual individual) {
        List<Identifier> identifiers = individual.getIdentifiers();
        identifiers.forEach(identifier -> identifier.setIndividualId(individual.getId()));
        individual.setIdentifiers(identifiers);
        return individual;
    }

    private static void enrichIndividualIdInAddress(IndividualBulkRequest request) {
        request.getIndividuals().stream().filter(individual -> individual.getAddress() != null)
                .forEach(individual -> individual.getAddress()
                        .forEach(address -> address.setIndividualId(individual.getId())));
    }

    private static Individual enrichWithSystemGeneratedIdentifier(Individual individual) {
        if (individual.getIdentifiers() == null || individual.getIdentifiers().isEmpty()) {
            List<Identifier> identifiers = new ArrayList<>();
            identifiers.add(Identifier.builder()
                    .identifierType("SYSTEM_GENERATED")
                    .build());
            individual.setIdentifiers(identifiers);
        }
        return individual;
    }

    private static AuditDetails getAuditDetails(IndividualBulkRequest request) {
        return AuditDetails.builder()
                .createdBy(request.getRequestInfo().getUserInfo().getUuid())
                .lastModifiedBy(request.getRequestInfo().getUserInfo().getUuid())
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();
    }

    private static void enrichAddress(IndividualBulkRequest request, Individual individual,
                                      AuditDetails auditDetails) {
        List<Address> addresses = individual.getAddress().stream().filter(ad1 -> ad1.getId() == null)
                .collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichForCreate(addresses, addressIdList, request.getRequestInfo());
            addresses.forEach(address -> address.setIndividualId(individual.getId()));
        }

        List<Address> addressesForUpdate = individual.getAddress().stream().filter(ad1 -> ad1.getId() != null)
                .collect(Collectors.toList());
        if (!addressesForUpdate.isEmpty()) {
            addressesForUpdate.forEach(address -> {
                address.setIndividualId(individual.getId());
                address.setAuditDetails(auditDetails);
            });
        }
    }

    private static void enrichIdentifier(Individual individual, AuditDetails auditDetails) {
        if (individual.getIdentifiers() != null) {
            enrichIndividualIdInIdentifiers(individual);
            individual.getIdentifiers().forEach(identifier -> {
                identifier.setAuditDetails(auditDetails);
            });
        }
    }

    public List<Individual> search(IndividualSearch individualSearch,
                                   Integer limit,
                                   Integer offset,
                                   String tenantId,
                                   Long lastChangedSince,
                                   Boolean includeDeleted) {
        String idFieldName = getIdFieldName(individualSearch);
        if (isSearchByIdOnly(individualSearch, idFieldName)) {
            List<String> ids = new ArrayList<>();
            ids.add((String) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(individualSearch)),
                    individualSearch));
            return individualRepository.findById(ids, idFieldName, includeDeleted)
                    .stream().filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return individualRepository.find(individualSearch, limit, offset, tenantId,
                        lastChangedSince, includeDeleted).stream()
                .filter(havingBoundaryCode(individualSearch.getBoundaryCode()))
                .collect(Collectors.toList());
    }

    private Predicate<Individual> havingBoundaryCode(String boundaryCode) {
        if (boundaryCode == null) {
            return individual -> true;
        }
        return individual -> individual.getAddress()
                .stream()
                .anyMatch(address -> address.getLocality().getCode()
                        .equalsIgnoreCase(boundaryCode));
    }

    private void deleteRelatedEntities(IndividualRequest request, Method idMethod,
                                       Map<String, Individual> iMap,
                                       List<Individual> existingIndividuals) {
        IntStream.range(0, existingIndividuals.size()).forEach(i -> {
            Individual individualInReq = iMap.get(ReflectionUtils.invokeMethod(idMethod,
                    existingIndividuals.get(i)));
            if (existingIndividuals.get(i).getAddress() != null) {
                individualInReq.setAddress(new ArrayList<>(existingIndividuals.get(i).getAddress()));
                for (Address addressInReq : individualInReq.getAddress()) {
                    // update audit details and isDeleted
                    AuditDetails auditDetails = addressInReq.getAuditDetails();
                    auditDetails.setLastModifiedTime(System.currentTimeMillis());
                    auditDetails.setLastModifiedBy(request.getRequestInfo()
                            .getUserInfo().getUuid());
                    addressInReq.setIsDeleted(true);
                    addressInReq.setRowVersion(addressInReq.getRowVersion() + 1);
                }
            }
            individualInReq.setIdentifiers(new ArrayList<>(existingIndividuals.get(i)
                    .getIdentifiers()));
            for (Identifier identifierInReq : individualInReq.getIdentifiers()) {
                AuditDetails auditDetails = identifierInReq.getAuditDetails();
                auditDetails.setLastModifiedTime(System.currentTimeMillis());
                auditDetails.setLastModifiedBy(request.getRequestInfo()
                        .getUserInfo().getUuid());
                identifierInReq.setIsDeleted(true);
            }

            // also deletes in household_individual_mapping table if such an individual exists
        });
    }
}
