package org.egov.individual.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.collectFromList;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class IndividualService {

    private final IdGenService idGenService;

    private final IndividualRepository individualRepository;

    private final List<Validator<IndividualRequest, Individual>> validators;

    private final Predicate<Validator<IndividualRequest, Individual>> isApplicableForUpdate = validator ->
            validator.getClass().equals(NullIdValidator.class)
            || validator.getClass().equals(NonExistentEntityValidator.class)
            || validator.getClass().equals(AddressTypeValidator.class)
            || validator.getClass().equals(RowVersionValidator.class);

    private final Predicate<Validator<IndividualRequest, Individual>> isApplicableForCreate = validator ->
                    validator.getClass().equals(AddressTypeValidator.class);

    @Autowired
    public IndividualService(IdGenService idGenService,
                             IndividualRepository individualRepository,
                             List<Validator<IndividualRequest, Individual>> validators) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
        this.validators = validators;
    }

    public List<Individual> create(IndividualRequest request) throws Exception {
        return create(request, false);
    }


    public List<Individual> create(IndividualRequest request, Boolean isBulk) throws Exception {
        // validation layer
        List<ErrorDetails> errorDetails = (List<ErrorDetails>) validators.stream().filter(isApplicableForCreate)
                .map(validator -> validator.validate(request))
                .map(e -> e.values());

        if (!errorDetails.isEmpty()) {
            if (isBulk) {
                System.out.println("HANDLE ERRORS()");
                throw new CustomException("HANDLE_ERRORS", "handle errors");
            } else {
                System.out.println("THROW CUSTOM_EXCEPTION()");
                throw new CustomException("CUSTOM_EXCEPTION", "custom exception");
            }
        }

        final String tenantId = getTenantId(request.getIndividuals());
        log.info("Generating id for individuals");
        List<String> indIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, "individual.id",
                null, request.getIndividuals().size());
        enrichForCreate(request.getIndividuals(), indIdList, request.getRequestInfo());
        List<Address> addresses = collectFromList(request.getIndividuals(),
                Individual::getAddress);
        if (!addresses.isEmpty()) {
            log.info("Enriching addresses");
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichForCreate(addresses, addressIdList, request.getRequestInfo());
            enrichIndividualIdInAddress(request);
        }
        log.info("Enriching identifiers");
        request.setIndividuals(request.getIndividuals().stream()
                .map(IndividualService::enrichWithSystemGeneratedIdentifier)
                        .map(IndividualService::enrichIndividualIdInIdentifiers)
                .collect(Collectors.toList()));
        List<Identifier> identifiers = collectFromList(request.getIndividuals(),
                Individual::getIdentifiers);
        List<String> identifierIdList = uuidSupplier().apply(identifiers.size());
        enrichForCreate(identifiers, identifierIdList, request.getRequestInfo());
        if (request.getIndividuals().stream().anyMatch(individual -> individual.getIdentifiers().stream()
                .anyMatch(identifier -> identifier.getIdentifierType().equals("SYSTEM_GENERATED")))) {
            List<String> sysGenIdList = idGenService.getIdList(request.getRequestInfo(),
                    tenantId, "sys.gen.identifier.id",
                    null, identifiers.size());
            enrichWithSysGenId(identifiers, sysGenIdList);
        }
        individualRepository.save(request.getIndividuals(), "save-individual-topic");
        return request.getIndividuals();
    }

    private void validateAddressType(List<Individual> individuals) {
        for (Individual individual : individuals) {
            Map<AddressType, Integer> addressTypeCountMap = new EnumMap<>(AddressType.class);
            if (individual.getAddress() == null) {
                continue;
            }
            for (Address address : individual.getAddress()) {
                addressTypeCountMap.merge(address.getType(), 1, Integer::sum);
            }
            addressTypeCountMap.entrySet().stream().filter(e -> e.getValue() > 1).findFirst().ifPresent(e -> {
                throw new CustomException("ERROR_IN_ADDRESS",
                        String.format("Found %d of type %s, allowed 1",
                        e.getValue(), e.getKey().name()));
            });
        }
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

    private static void enrichIndividualIdInAddress(IndividualRequest request) {
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

    public List<Individual> update(IndividualRequest request) {
//        Method idMethod = getIdMethod(request.getIndividuals());
//        Map<String, Individual> iMap = getIdToObjMap(request.getIndividuals(), idMethod);
//        List<ErrorDetails> errorDetails = validators.stream().filter(isApplicableForUpdate)
//                .map(validator -> validator.validate(request))
//                .flatMap(Collection::stream).collect(Collectors.toList());
//        log.error(errorDetails.toString());
//
//        // necessary to enrich server gen id in case where request has only clientReferenceId
//        // this is because persister config has where clause on server gen id only
//        // to solve this we might have to create different topic which would only cater to clientReferenceId
//
//        if (request.getIndividuals().stream().anyMatch(notHavingErrors())) {
//            log.info("Updating lastModifiedTime and lastModifiedBy");
//            enrichForUpdate(iMap, request);
//            individualRepository.save(request.getIndividuals(), "update-individual-topic");
//        }
//        return request.getIndividuals();
        return null;
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
                identifierInReq.setRowVersion(identifierInReq.getRowVersion() + 1);
            }

            // also deletes in household_individual_mapping table if such an individual exists
        });
    }
}
