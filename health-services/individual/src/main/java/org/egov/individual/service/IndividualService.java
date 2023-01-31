package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.individual.web.models.IndividualSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @Autowired
    public IndividualService(IdGenService idGenService,
                             IndividualRepository individualRepository) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
    }


    public List<Individual> create(IndividualRequest request) throws Exception {
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
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(individualSearch)),
                    individualSearch);
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
}
