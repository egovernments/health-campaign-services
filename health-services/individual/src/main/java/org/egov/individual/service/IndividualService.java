package org.egov.individual.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.ApiOperation;
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

import static org.egov.common.utils.CommonUtils.checkRowVersion;
import static org.egov.common.utils.CommonUtils.collectFromList;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.enrichIdsFromExistingEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.identifyNullIds;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.uuidSupplier;
import static org.egov.common.utils.CommonUtils.validateEntities;

@Service
@Slf4j
public class IndividualService {

    private final IdGenService idGenService;

    private final IndividualRepository individualRepository;

    @Autowired
    public IndividualService(IdGenService idGenService,
                             IndividualRepository individualRepository) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
    }


    public List<Individual> create(IndividualRequest request) throws Exception {
        validateAddressType(request.getIndividual());
        final String tenantId = getTenantId(request.getIndividual());
        log.info("Generating id for individuals");
        List<String> indIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, "individual.id",
                null, request.getIndividual().size());
        enrichForCreate(request.getIndividual(), indIdList, request.getRequestInfo());
        List<Address> addresses = collectFromList(request.getIndividual(),
                Individual::getAddress);
        if (!addresses.isEmpty()) {
            log.info("Enriching addresses");
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichForCreate(addresses, addressIdList, request.getRequestInfo());
            enrichIndividualIdInAddress(request);
        }
        log.info("Enriching identifiers");
        request.setIndividual(request.getIndividual().stream()
                .map(IndividualService::enrichWithSystemGeneratedIdentifier)
                        .map(IndividualService::enrichIndividualIdInIdentifiers)
                .collect(Collectors.toList()));
        List<Identifier> identifiers = collectFromList(request.getIndividual(),
                Individual::getIdentifiers);
        List<String> identifierIdList = uuidSupplier().apply(identifiers.size());
        enrichForCreate(identifiers, identifierIdList, request.getRequestInfo());
        if (request.getIndividual().stream().anyMatch(individual -> individual.getIdentifiers().stream()
                .anyMatch(identifier -> identifier.getIdentifierType().equals("SYSTEM_GENERATED")))) {
            List<String> sysGenIdList = idGenService.getIdList(request.getRequestInfo(),
                    tenantId, "sys.gen.identifier.id",
                    null, identifiers.size());
            enrichWithSysGenId(identifiers, sysGenIdList);
        }
        individualRepository.save(request.getIndividual(), "save-individual-topic");
        return request.getIndividual();
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
        request.getIndividual().stream().filter(individual -> individual.getAddress() != null)
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
        validateAddressType(request.getIndividual());
        Method idMethod = getIdMethod(request.getIndividual());
        identifyNullIds(request.getIndividual(), idMethod);
        Map<String, Individual> iMap = getIdToObjMap(request.getIndividual(), idMethod);

        log.info("Checking if already exists");
        List<String> householdIds = new ArrayList<>(iMap.keySet());
        List<Individual> existingIndividuals = individualRepository.findById(householdIds,
                getIdFieldName(idMethod), false);
        validateEntities(iMap, existingIndividuals, idMethod);
        checkRowVersion(iMap, existingIndividuals, idMethod);
        // enrich id and clientReferenceId from existingIndividuals for response
        // if update is using server generated id then we need to enrich client reference id
        // if update is using client reference id then we need to enrich the server generated id for response
        // if update has both client reference id and server generated id then nothing to enrich
        // so, we enrich both every time to be safe
        enrichIdsFromExistingEntities(iMap, existingIndividuals, idMethod);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(iMap, existingIndividuals, request, idMethod);

        if (request.getApiOperation().equals(ApiOperation.DELETE)) {
            deleteRelatedEntities(request, idMethod, iMap, existingIndividuals);
            individualRepository.save(request.getIndividual(), "delete-individual-topic");
            return request.getIndividual();
        }

        individualRepository.save(request.getIndividual(), "update-individual-topic");
        return request.getIndividual();
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
        });
    }
}
