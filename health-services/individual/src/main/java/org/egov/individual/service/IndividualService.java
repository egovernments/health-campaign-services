package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getTenantId;

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
        List<String> indIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, "individual.id",
                null, request.getIndividual().size());
        enrichForCreate(request.getIndividual(), indIdList, request.getRequestInfo());
        List<Address> addresses = collectFromList(request.getIndividual(),
                Individual::getAddress);
        if (!addresses.isEmpty()) {
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichForCreate(addresses, addressIdList, request.getRequestInfo());
            enrichIndividualIdInAddress(request);
        }
        request.setIndividual(request.getIndividual().stream()
                .map(IndividualService::enrichWithSystemGeneratedIdentifier)
                        .map(IndividualService::enrichIndividualIdInIdentifiers)
                .collect(Collectors.toList()));
        List<Identifier> identifiers = collectFromList(request.getIndividual(),
                Individual::getIdentifiers);
        List<String> identifierIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, "identifier.id",
                null, identifiers.size());
        enrichForCreate(identifiers, identifierIdList, request.getRequestInfo());
        List<String> sysGenIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, "sys.gen.identifier.id",
                null, identifiers.size());
        enrichWithSysGenId(identifiers, sysGenIdList);
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

    private static Function<Integer, List<String>> uuidSupplier() {
        return integer ->  {
            List<String> uuidList = new ArrayList<>();
            for (int i = 0; i < integer; i++) {
                uuidList.add(UUID.randomUUID().toString());
            }
            return uuidList;
        };
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

    private <T, R> List<R> collectFromList(List<T> objList, Function<T, List<R>> function) {
        return objList.stream()
                .flatMap(obj -> {
                    List<R> aList = function.apply(obj);
                    if (aList == null || aList.isEmpty()) {
                        return new ArrayList<R>().stream();
                    }
                    return aList.stream();
                })
                .collect(Collectors.toList());
    }
}
