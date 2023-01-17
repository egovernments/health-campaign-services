package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.service.IdGenService;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.web.models.Address;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberRequest;
import org.egov.household.web.models.HouseholdMemberSearch;
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
public class HouseholdMemberService {

    private final HouseholdMemberRepository householdMemberRepository;

    private final IdGenService idGenService;

    private final HouseholdMemberConfiguration householdMemberConfiguration;

    @Autowired
    public HouseholdMemberService(HouseholdMemberRepository householdMemberRepository, IdGenService idGenService,
                                  HouseholdMemberConfiguration householdMemberConfiguration) {
        this.householdMemberRepository = householdMemberRepository;
        this.idGenService = idGenService;
        this.householdMemberConfiguration = householdMemberConfiguration;
    }

    public List<HouseholdMember> create(HouseholdMemberRequest householdMemberRequest) throws Exception {
        Method idMethod = getIdMethod(householdMemberRequest.getHouseholdMember());

        List<String> idList =  idGenService.getIdList(householdMemberRequest.getRequestInfo(),
                getTenantId(householdMemberRequest.getHouseholdMember()),
                "household.member.id", "", householdMemberRequest.getHouseholdMember().size());
        enrichForCreate(householdMemberRequest.getHouseholdMember(), idList, householdMemberRequest.getRequestInfo());

        householdMemberRepository.save(householdMemberRequest.getHouseholdMember(), householdMemberConfiguration.getCreateTopic());
        return householdMemberRequest.getHouseholdMember();
    }

    public List<HouseholdMember> search(HouseholdMemberSearch householdMemberSearch, Integer limit, Integer offset, String tenantId,
                                        Long lastChangedSince, Boolean includeDeleted) {

        String idFieldName = getIdFieldName(householdMemberSearch);
        if (isSearchByIdOnly(householdMemberSearch, idFieldName)) {
            List<String> ids = new ArrayList<>();
            ids.add((String) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdMemberSearch)),
                    householdMemberSearch));
            return householdMemberRepository.findById(ids,
                    idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        try {
            return householdMemberRepository.find(householdMemberSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    public List<HouseholdMember> update(HouseholdMemberRequest householdMemberRequest) {
        Method idMethod = getIdMethod(householdMemberRequest.getHouseholdMember());
        identifyNullIds(householdMemberRequest.getHouseholdMember(), idMethod);

        Map<String, HouseholdMember> hMap = getIdToObjMap(householdMemberRequest.getHouseholdMember(), idMethod);

        log.info("Checking if already exists");
        List<String> householdMemberIds = new ArrayList<>(hMap.keySet());
        List<HouseholdMember> existingHouseholdsMember = householdMemberRepository.findById(householdMemberIds, getIdFieldName(idMethod), false);
        validateEntities(hMap, existingHouseholdsMember, idMethod);
        checkRowVersion(hMap, existingHouseholdsMember, idMethod);
        enrichIdsFromExistingEntities(hMap, existingHouseholdsMember, idMethod);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(hMap, existingHouseholdsMember, householdMemberRequest, idMethod);

        householdMemberRepository.save(householdMemberRequest.getHouseholdMember(), householdMemberConfiguration.getUpdateTopic());
        return householdMemberRequest.getHouseholdMember();
    }
}
