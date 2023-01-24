package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.Validator;
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
import java.util.stream.IntStream;

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
import static org.egov.common.utils.CommonUtils.validate;

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
                    enrichForCreate(addresses, addressIdList, request.getRequestInfo(), false);
                    enrichIndividualIdInAddress(validIndividuals);
                }
                log.info("Enriching identifiers");
                request.setIndividuals(validIndividuals.stream()
                        .map(IndividualService::enrichWithSystemGeneratedIdentifier)
                        .map(IndividualService::enrichIndividualIdInIdentifiers)
                        .collect(Collectors.toList()));
                List<Identifier> identifiers = collectFromList(validIndividuals,
                        Individual::getIdentifiers);
                List<String> identifierIdList = uuidSupplier().apply(identifiers.size());
                enrichForCreate(identifiers, identifierIdList, request.getRequestInfo(), false);
                individualRepository.save(validIndividuals, "save-individual-topic");
            }
        } catch (Exception exception) {
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, "setIndividuals");
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
                            AuditDetails auditDetails = getAuditDetailsForUpdate(request.getRequestInfo().getUserInfo().getUuid());
                            enrichAddress(request, individual, auditDetails);
                            enrichIdentifier(request, individual, auditDetails);
                        }
                );
                Map<String, Individual> iMap = getIdToObjMap(validIndividuals);
                log.info("Updating lastModifiedTime and lastModifiedBy");
                enrichForUpdate(iMap, request);
                individualRepository.save(validIndividuals, "update-individual-topic");
            }
        } catch (Exception exception) {
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, "setIndividuals");
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

    private static void enrichWithSysGenId(List<Identifier> identifiers, List<String> sysGenIdList) {
        IntStream.range(0, identifiers.size()).forEach(i -> {
            if (identifiers.get(i).getIdentifierType().equals("SYSTEM_GENERATED")) {
                identifiers.get(i).setIdentifierId(sysGenIdList.get(i));
            }
        });
    }

    private static Individual enrichIndividualIdInIdentifiers(Individual individual) {
        List<Identifier> identifiers = individual.getIdentifiers();
        identifiers.forEach(identifier -> {
            identifier.setIndividualId(individual.getId());
        });
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

    private static void enrichAddress(IndividualBulkRequest request, Individual individual,
                                      AuditDetails auditDetails) {
        List<Address> addresses = individual.getAddress().stream().filter(ad1 -> ad1.getId() == null)
                .collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichForCreate(addresses, addressIdList, request.getRequestInfo(), false);
            addresses.forEach(address -> address.setIndividualId(individual.getId()));
        }

        List<Address> addressesForUpdate = individual.getAddress().stream().filter(ad1 -> ad1.getId() != null)
                .collect(Collectors.toList());
        if (!addressesForUpdate.isEmpty()) {
            addressesForUpdate.forEach(address -> {
                address.setIndividualId(individual.getId());
                address.setAuditDetails(auditDetails);
                if (address.getIsDeleted() == null) {
                    address.setIsDeleted(Boolean.FALSE);
                }
            });
        }
    }

    private static void enrichIdentifier(IndividualBulkRequest request,
                                         Individual individual, AuditDetails auditDetails) {
        if (individual.getIdentifiers() != null) {
            List<Identifier> identifiers = individual.getIdentifiers().stream().filter(id -> id.getId() == null)
                    .collect(Collectors.toList());
            if (!identifiers.isEmpty()) {
                List<String> addressIdList = uuidSupplier().apply(identifiers.size());
                enrichForCreate(identifiers, addressIdList, request.getRequestInfo(), false);
                identifiers.forEach(identifier -> identifier.setIndividualId(individual.getId()));
            }

            List<Identifier> identifiersForUpdate = individual.getIdentifiers().stream().filter(id -> id.getId() != null)
                    .collect(Collectors.toList());
            if (!identifiersForUpdate.isEmpty()) {
                identifiersForUpdate.forEach(identifier -> {
                    identifier.setIndividualId(individual.getId());
                    identifier.setAuditDetails(auditDetails);
                    if (identifier.getIsDeleted() == null) {
                        identifier.setIsDeleted(Boolean.FALSE);
                    }
                });
            }
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

    public List<Individual> delete(IndividualRequest request) {
        IndividualBulkRequest bulkRequest = IndividualBulkRequest.builder().requestInfo(request.getRequestInfo())
                .individuals(Collections.singletonList(request.getIndividual())).build();
        return delete(bulkRequest, false);
    }

    @KafkaListener(topics = "bulk-delete")
    public List<Individual> bulkDelete(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        IndividualBulkRequest request = objectMapper.convertValue(consumerRecord, IndividualBulkRequest.class);
        return delete(request, true);
    }

    private List<Individual> delete(IndividualBulkRequest request, boolean isBulk) {
        Map<Individual, ErrorDetails> errorDetailsMap = validate(validators, isApplicableForDelete, request,
                "setIndividuals");

        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException("VALIDATION_ERROR", errorDetailsMap.values().toString());
        }

        List<Individual> validIndividuals = request.getIndividuals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());

        try {
            if (!validIndividuals.isEmpty()) {
                enrichIndividualIdInAddress(validIndividuals);
                validIndividuals = validIndividuals.stream()
                        .map(IndividualService::enrichIndividualIdInIdentifiers)
                        .collect(Collectors.toList());
                validIndividuals.forEach(individual -> {
                    enrichForDelete(Collections.singletonList(individual), request, true);
                    enrichForDelete(individual.getAddress(), request, false);
                    enrichForDelete(individual.getIdentifiers(), request, false);
                });
                individualRepository.save(validIndividuals, "delete-individual-topic");
            }
        } catch(Exception exception) {
            populateErrorDetails(request, errorDetailsMap, validIndividuals, exception, "setIndividuals");
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
}
