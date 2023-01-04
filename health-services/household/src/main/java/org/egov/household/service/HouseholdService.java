package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.service.IdGenService;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.web.models.Address;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdRequest;
import org.egov.household.web.models.HouseholdSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

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

    @Autowired
    public HouseholdService(HouseholdRepository householdRepository, IdGenService idGenService) {
        this.householdRepository = householdRepository;
        this.idGenService = idGenService;
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

        householdRepository.save(householdRequest.getHousehold(), "save-household-topic");
        return householdRequest.getHousehold();
    }

    public List<Household> search(HouseholdSearch householdSearch, Integer limit, Integer offset, String tenantId,
                                  Long lastChangedSince, Boolean includeDeleted) {

        String idFieldName = getIdFieldName(householdSearch);
        if (isSearchByIdOnly(householdSearch, idFieldName)) {
            List<String> ids = new ArrayList<>();
            ids.add((String) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdSearch)),
                    householdSearch));
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
        identifyNullIds(request.getHousehold());
        Map<String, Household> hMap = getIdToObjMap(request.getHousehold());

        log.info("Checking if already exists");
        List<String> householdIds = new ArrayList<>(hMap.keySet());
        List<Household> existingHouseholds = householdRepository.findById(householdIds, "id", false);
        validateEntities(hMap, existingHouseholds);
        checkRowVersion(hMap, existingHouseholds);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(hMap, existingHouseholds, request);

        householdRepository.save(request.getHousehold(), "update-household-topic");
        return request.getHousehold();
    }
}
