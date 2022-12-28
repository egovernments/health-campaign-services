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
        // get all the addresses
        List<Address> addresses = collectFromList(request.getIndividual(),
                Individual::getAddress);
        // check if the list is not empty
        if (!addresses.isEmpty()) {
            //  generate id for address
            List<String> addressIdList = idGenService.getIdList(request.getRequestInfo(),
                    getTenantId(addresses), "address.id",
                    null, addresses.size());
            //  enrich id
            IntStream.range(0, addresses.size()).forEach(i -> addresses.get(i).setId(addressIdList.get(i)));
        }
        //  -----
        //  generate id for individual
        final String tenantId = getTenantId(request.getIndividual());
        List<String> indIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, "individual.id",
                null, request.getIndividual().size());
        //  enrich id and audit details
        enrichForCreate(request.getIndividual(), indIdList, request.getRequestInfo());
        //  -----
        //  generate identifier if none present
        request.setIndividual(request.getIndividual().stream()
                .filter(individual -> individual.getIdentifiers() == null
                || individual.getIdentifiers().isEmpty())
                .map(IndividualService::enrichWithSystemGeneratedIdentifier)
                .collect(Collectors.toList()));
        //  generate id for identifier
        List<Identifier> identifiers = collectFromList(request.getIndividual(),
                Individual::getIdentifiers);
        List<String> identifierIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, "identifier.id",
                null, identifiers.size());
        //  enrich id for identifier
        IntStream.range(0, identifiers.size())
                .forEach(i -> identifiers.get(i).setId(identifierIdList.get(i)));
        //  -----
        //  save
        individualRepository.save(request.getIndividual(), "save-individual-topic");
        return request.getIndividual();
    }

    private static Individual enrichWithSystemGeneratedIdentifier(Individual individual) {
        List<Identifier> identifiers = new ArrayList<>();
        identifiers.add(Identifier.builder()
                        .identifierType("SYSTEM_GENERATED")
                        .indentifierId(UUID.randomUUID().toString())
                .build());
        individual.setIdentifiers(identifiers);
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
