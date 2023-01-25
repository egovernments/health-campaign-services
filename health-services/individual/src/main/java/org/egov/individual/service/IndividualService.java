package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.Validator;
import org.egov.individual.config.IndividualProperties;
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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.collectFromList;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForUpdate;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.CommonUtils.uuidSupplier;
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
                    || validator.getClass().equals(UniqueSubEntityValidator.class);

    private final Predicate<Validator<IndividualBulkRequest, Individual>> isApplicableForDelete = validator ->
            validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class);

    @Autowired
    public IndividualService(IdGenService idGenService,
                             IndividualRepository individualRepository,
                             List<Validator<IndividualBulkRequest, Individual>> validators,
                             @Qualifier("objectMapper") ObjectMapper objectMapper,
                             IndividualProperties properties) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
        this.validators = validators;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<Individual> create(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return create(bulkRequest, false);
    }

    @KafkaListener(topics = "${individual.consumer.bulk.create.topic}")
    public List<Individual> bulkCreate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        IndividualBulkRequest request = objectMapper.convertValue(consumerRecord, IndividualBulkRequest.class);
        return create(request, true);
    }

    public List<Individual> create(IndividualBulkRequest request, boolean isBulk) {

        Tuple<List<Individual>, Map<Individual, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
                isBulk);
        Map<Individual, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Individual> validIndividuals = tuple.getX();
        try {
            if (!validIndividuals.isEmpty()) {
                log.info("extracting tenantId");
                final String tenantId = getTenantId(validIndividuals);
                log.info("generating id for individuals");
                List<String> indIdList = idGenService.getIdList(request.getRequestInfo(),
                        tenantId, "individual.id",
                        null, validIndividuals.size());
                log.info("enriching individuals");
                enrichForCreate(validIndividuals, indIdList, request.getRequestInfo());
                enrichAddressesForCreate(request, validIndividuals);
                enrichIdentifiersForCreate(request, validIndividuals);
                individualRepository.save(validIndividuals,
                        properties.getSaveIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(isBulk, errorDetailsMap);

        return validIndividuals;
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

    private static void enrichAddressesForCreate(IndividualBulkRequest request, List<Individual> validIndividuals) {
        List<Address> addresses = collectFromList(validIndividuals,
                Individual::getAddress);
        if (!addresses.isEmpty()) {
            log.info("enriching addresses");
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichForCreate(addresses, addressIdList, request.getRequestInfo(), false);
            enrichIndividualIdInAddress(validIndividuals);
        }
    }

    private static void enrichIdentifiersForCreate(IndividualBulkRequest request, List<Individual> validIndividuals) {
        log.info("enriching identifiers");
        request.setIndividuals(validIndividuals.stream()
                .map(IndividualService::enrichWithSystemGeneratedIdentifier)
                .map(IndividualService::enrichIndividualIdInIdentifiers)
                .collect(Collectors.toList()));
        List<Identifier> identifiers = collectFromList(validIndividuals,
                Individual::getIdentifiers);
        List<String> identifierIdList = uuidSupplier().apply(identifiers.size());
        enrichForCreate(identifiers, identifierIdList, request.getRequestInfo(), false);
    }

    public List<Individual> update(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return update(bulkRequest, false);
    }

    @KafkaListener(topics = "${individual.consumer.bulk.update.topic}")
    public List<Individual> bulkUpdate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        IndividualBulkRequest request = objectMapper.convertValue(consumerRecord, IndividualBulkRequest.class);
        return update(request, true);
    }

    public List<Individual> update(IndividualBulkRequest request, boolean isBulk) {
        Tuple<List<Individual>, Map<Individual, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);
        Map<Individual, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Individual> validIndividuals = tuple.getX();

        try {
            if (!validIndividuals.isEmpty()) {
                validIndividuals.forEach(
                        individual -> {
                            enrichAddressForUpdate(request, individual);
                            enrichIdentifierForUpdate(request, individual);
                        }
                );
                Map<String, Individual> iMap = getIdToObjMap(validIndividuals);
                log.info("enriching individuals");
                enrichForUpdate(iMap, request);
                individualRepository.save(validIndividuals,
                        properties.getUpdateIndividualTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, SET_INDIVIDUALS);
        }

        handleErrors(isBulk, errorDetailsMap);

        return validIndividuals;
    }

    private static Individual enrichIndividualIdInIdentifiers(Individual individual) {
        List<Identifier> identifiers = individual.getIdentifiers();
        identifiers.forEach(identifier -> identifier.setIndividualId(individual.getId()));
        individual.setIdentifiers(identifiers);
        return individual;
    }

    private static void enrichIndividualIdInAddress(List<Individual> individuals) {
        individuals.stream().filter(individual -> individual.getAddress() != null)
                .forEach(individual -> individual.getAddress()
                        .forEach(address -> address.setIndividualId(individual.getId())));
    }

    private static Individual enrichWithSystemGeneratedIdentifier(Individual individual) {
        if (individual.getIdentifiers() == null || individual.getIdentifiers().isEmpty()) {
            List<Identifier> identifiers = new ArrayList<>();
            identifiers.add(Identifier.builder()
                    .identifierType("SYSTEM_GENERATED")
                    .identifierId(individual.getId())
                    .build());
            individual.setIdentifiers(identifiers);
        }
        return individual;
    }

    private static void enrichAddressForUpdate(IndividualBulkRequest request, Individual individual) {
        List<Address> addressesToCreate = individual.getAddress().stream()
                .filter(ad1 -> ad1.getId() == null)
                .collect(Collectors.toList());
        if (!addressesToCreate.isEmpty()) {
            log.info("enriching addresses to create");
            List<String> addressIdList = uuidSupplier().apply(addressesToCreate.size());
            enrichForCreate(addressesToCreate, addressIdList, request.getRequestInfo(), false);
            addressesToCreate.forEach(address -> address.setIndividualId(individual.getId()));
        }

        List<Address> addressesToUpdate = individual.getAddress().stream()
                .filter(ad1 -> ad1.getId() != null)
                .collect(Collectors.toList());
        if (!addressesToUpdate.isEmpty()) {
            log.info("enriching addresses to update");
            addressesToUpdate.forEach(address -> {
                address.setIndividualId(individual.getId());
                AuditDetails existingAuditDetails = address.getAuditDetails();
                AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                        request.getRequestInfo().getUserInfo().getUuid());
                address.setAuditDetails(auditDetails);
                if (address.getIsDeleted() == null) {
                    address.setIsDeleted(Boolean.FALSE);
                }
            });
        }
    }

    private static void enrichIdentifierForUpdate(IndividualBulkRequest request,
                                                  Individual individual) {
        if (individual.getIdentifiers() != null) {
            List<Identifier> identifiersToCreate = individual.getIdentifiers().stream().filter(havingNullId())
                    .collect(Collectors.toList());
            if (!identifiersToCreate.isEmpty()) {
                List<String> addressIdList = uuidSupplier().apply(identifiersToCreate.size());
                enrichForCreate(identifiersToCreate, addressIdList, request.getRequestInfo(), false);
                identifiersToCreate.forEach(identifier -> identifier.setIndividualId(individual.getId()));
            }

            List<Identifier> identifiersToUpdate = individual.getIdentifiers().stream()
                    .filter(notHavingNullId())
                    .collect(Collectors.toList());
            if (!identifiersToUpdate.isEmpty()) {
                identifiersToUpdate.forEach(identifier -> {
                    identifier.setIndividualId(individual.getId());
                    AuditDetails existingAuditDetails = identifier.getAuditDetails();
                    AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                            request.getRequestInfo().getUserInfo().getUuid());
                    identifier.setAuditDetails(auditDetails);
                    if (identifier.getIsDeleted() == null) {
                        identifier.setIsDeleted(Boolean.FALSE);
                    }
                });
            }
        }
    }

    private static Predicate<Identifier> havingNullId() {
        return identifier -> identifier.getId() == null;
    }

    private static Predicate<Identifier> notHavingNullId() {
        return havingNullId().negate();
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

    public List<Individual> delete(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return delete(bulkRequest, false);
    }

    @KafkaListener(topics = "${individual.consumer.bulk.delete.topic}")
    public List<Individual> bulkDelete(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        IndividualBulkRequest request = objectMapper.convertValue(consumerRecord, IndividualBulkRequest.class);
        return delete(request, true);
    }

    private List<Individual> delete(IndividualBulkRequest request, boolean isBulk) {
        Tuple<List<Individual>, Map<Individual, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
                isBulk);
        Map<Individual, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Individual> validIndividuals = tuple.getX();

        try {
            if (!validIndividuals.isEmpty()) {
                enrichIndividualIdInAddress(validIndividuals);
                validIndividuals = validIndividuals.stream()
                        .map(IndividualService::enrichIndividualIdInIdentifiers)
                        .collect(Collectors.toList());
                validIndividuals.forEach(individual -> {
                    RequestInfo requestInfo = request.getRequestInfo();
                    if (individual.getIsDeleted()) {
                        enrichForDelete(Collections.singletonList(individual), requestInfo, true);
                        enrichForDelete(individual.getAddress(), requestInfo, false);
                        enrichForDelete(individual.getIdentifiers(), requestInfo, false);
                    } else {
                        Integer previousRowVersion = individual.getRowVersion();
                        individual.getIdentifiers().stream().filter(Identifier::getIsDeleted)
                                .forEach(identifier -> {
                                    AuditDetails existingAuditDetails = identifier.getAuditDetails();
                                    AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                                            request.getRequestInfo().getUserInfo().getUuid());
                                    identifier.setAuditDetails(auditDetails);
                                    individual.setAuditDetails(auditDetails);
                                    individual.setRowVersion(previousRowVersion + 1);
                                });
                        individual.getAddress().stream().filter(Address::getIsDeleted)
                                .forEach(address -> {
                                    AuditDetails existingAuditDetails = address.getAuditDetails();
                                    AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                                            request.getRequestInfo().getUserInfo().getUuid());
                                    address.setAuditDetails(auditDetails);
                                    individual.setAuditDetails(auditDetails);
                                    individual.setRowVersion(previousRowVersion + 1);
                                });
                    }
                });
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
}
