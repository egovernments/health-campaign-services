package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.individual.repository.AddressRepository;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressRequest;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Individual;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.identifyNullIds;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class AddressService {

    private final IndividualRepository individualRepository;

    private final AddressRepository addressRepository;

    public AddressService(IndividualRepository individualRepository, AddressRepository addressRepository) {
        this.individualRepository = individualRepository;
        this.addressRepository = addressRepository;
    }


    public List<Address> create(AddressRequest request) {
        Method individualIdMethod = getIdMethod(request.getAddress(), "individualId",
                "individualClientReferenceId");
        identifyNullIds(request.getAddress(), individualIdMethod);
        Set<String> individualIds = request.getAddress().stream().map(address ->
                (String) ReflectionUtils.invokeMethod(individualIdMethod, address))
                .collect(Collectors.toSet());
        List<Individual> existingIndividuals = individualRepository.findById(new ArrayList<>(individualIds),
                getIdFieldName(individualIdMethod), false);
        if (existingIndividuals.size() < individualIds.size()) {
            throw new CustomException("INVALID_PARENT_ENTITY",
                    "Parent entity does not exist for one or more payloads");
        }
        String idFieldName = getIdFieldName(individualIdMethod);
        Method idMethod = getIdMethod(existingIndividuals, idFieldName);
        Map<String, Individual> idToObjMapForIndividuals = getIdToObjMap(existingIndividuals, idMethod);
        for (Address address : request.getAddress()) {
            Individual existingIndividual = idToObjMapForIndividuals.get(ReflectionUtils
                    .invokeMethod(individualIdMethod, address));
            address.setIndividualId(existingIndividual.getId());
            address.setIndividualClientReferenceId(existingIndividual.getClientReferenceId());
            if (existingIndividual.getAddress() != null && !existingIndividual.getAddress().isEmpty()) {
                List<AddressType> typeList = existingIndividual.getAddress().stream().map(Address::getType)
                        .collect(Collectors.toList());
                if (typeList.contains(address.getType())) {
                    throw new CustomException("INVALID_ADDRESS_TYPE",
                            String.format("Address type %s already exists", address.getType()));
                } else {
                    // update for cache
                    existingIndividual.getAddress().add(address);
                }
            } else {
                // update for cache
                List<Address> addressList = new ArrayList<>();
                addressList.add(address);
                existingIndividual.setAddress(addressList);
            }
        }
        List<String> idList = uuidSupplier().apply(request.getAddress().size());
        enrichForCreate(request.getAddress(), idList, request.getRequestInfo());
        individualRepository.putInCache(existingIndividuals);
        addressRepository.save(request.getAddress(), "save-individual-address-topic");
        return request.getAddress();
    }
}
