package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.service.IdGenService;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.web.models.Address;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdRequest;
import org.egov.household.web.models.HouseholdSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.checkRowVersion;
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
import static org.egov.common.utils.CommonUtils.validateEntities;

@Service
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;

    private final IdGenService idGenService;

    private final HouseholdConfiguration householdConfiguration;

    @Autowired
    public HouseholdService(HouseholdRepository householdRepository, IdGenService idGenService,
                            HouseholdConfiguration householdConfiguration) {
        this.householdRepository = householdRepository;
        this.idGenService = idGenService;
        this.householdConfiguration = householdConfiguration;
    }

    public List<Household> create(HouseholdRequest householdRequest) throws Exception {
        List<String> idList =  idGenService.getIdList(householdRequest.getRequestInfo(),
                getTenantId(householdRequest.getHousehold()),
                "household.id", "", householdRequest.getHousehold().size());
        enrichForCreate(householdRequest.getHousehold(), idList, householdRequest.getRequestInfo());

        List<Address> addresses = householdRequest.getHousehold().stream().map(Household::getAddress)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
           IntStream.range(0, addresses.size()).forEach(i -> addresses.get(i).setId(UUID.randomUUID().toString()));
        }

        householdRepository.save(householdRequest.getHousehold(), householdConfiguration.getCreateTopic());
        return householdRequest.getHousehold();
    }

    public List<Household> search(HouseholdSearch householdSearch, Integer limit, Integer offset, String tenantId,
                                  Long lastChangedSince, Boolean includeDeleted) {

        String idFieldName = getIdFieldName(householdSearch);
        if (isSearchByIdOnly(householdSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdSearch)),
                    householdSearch);
            return householdRepository.findById(ids,
                    idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        try {
            return householdRepository.find(householdSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    public List<Household> update(HouseholdRequest request) {
        Method idMethod = getIdMethod(request.getHousehold());
        identifyNullIds(request.getHousehold(), idMethod);

        List<Address> addresses = request.getHousehold().stream().map(Household::getAddress)
                .filter(Objects::nonNull).filter(address ->  address.getId() == null).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            addresses.forEach(address -> address.setId(UUID.randomUUID().toString()));
        }

        Map<String, Household> hMap = getIdToObjMap(request.getHousehold(), idMethod);

        log.info("Checking if already exists");
        List<String> householdIds = new ArrayList<>(hMap.keySet());
        List<Household> existingHouseholds = householdRepository.findById(householdIds, getIdFieldName(idMethod), false);
        validateEntities(hMap, existingHouseholds, idMethod);
        checkRowVersion(hMap, existingHouseholds, idMethod);
        enrichIdsFromExistingEntities(hMap, existingHouseholds, idMethod);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(hMap, existingHouseholds, request, idMethod);

        householdRepository.save(request.getHousehold(), householdConfiguration.getUpdateTopic(), "id");
        return request.getHousehold();
    }

    public List<Household> findById(List<String> houseHoldIds, String columnName, boolean includeDeleted){
       return householdRepository.findById(houseHoldIds, columnName, includeDeleted);
    }

}
