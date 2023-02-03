package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;

@Slf4j
@Service
public class HouseholdMemberEnrichmentService {

    private final HouseholdMemberRepository householdMemberRepository;

    private final HouseholdService householdService;

    public HouseholdMemberEnrichmentService(IdGenService idGenService,
                                            HouseholdMemberRepository householdMemberRepository,
                                            HouseholdService householdService) {

        this.householdMemberRepository = householdMemberRepository;
        this.householdService = householdService;
    }


    public void create(List<HouseholdMember> householdMembers,
                       HouseholdMemberBulkRequest request) throws Exception {
        List<String> uuidList = Stream.generate(UUID::randomUUID)
                .limit(householdMembers.size())
                .map(UUID::toString)
                .collect(Collectors.toList());
        enrichForCreate(householdMembers, uuidList, request.getRequestInfo());
    }

    public void update(List<HouseholdMember> householdMembers,
                       HouseholdMemberBulkRequest beneficiaryRequest) {

        enrichHousehold(householdMembers);
        Map<String, HouseholdMember> hMap = getIdToObjMap(householdMembers);
        List<String> householdMemberIds = new ArrayList<>(hMap.keySet());
        List<HouseholdMember> existingHouseholdMembers = householdMemberRepository.findById(householdMemberIds,
                "id", false);
        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(hMap, existingHouseholdMembers, beneficiaryRequest);
    }

    public void enrichHousehold(List<HouseholdMember> householdMembers) {
        Method idMethod = getIdMethod(householdMembers, "householdId",
                "householdClientReferenceId");
        String columnName = getColumnName(idMethod);

        List<String> houseHoldIds = getIdList(householdMembers, idMethod);
        List<Household> householdList = householdService.findById(houseHoldIds, columnName, false);
        Method householdMethod = getIdMethod(householdList, columnName);
        Map<String, Household> householdMap = getIdToObjMap(householdList, householdMethod);

        for (HouseholdMember householdMember : householdMembers) {
            enrichWithHouseholdId(householdMap, householdMember);
        }
    }

    public void delete(List<HouseholdMember> householdMembers,
                       HouseholdMemberBulkRequest beneficiaryRequest) {
        enrichForDelete(householdMembers, beneficiaryRequest.getRequestInfo(), true);
    }

    private void enrichWithHouseholdId(Map<String, Household> householdMap, HouseholdMember householdMember) {
        Household household = householdMap.get(getHouseholdId(householdMember));
        householdMember.setHouseholdId(household.getId());
        householdMember.setHouseholdClientReferenceId(household.getClientReferenceId());
    }

    private String getHouseholdId(HouseholdMember householdMember) {
        return householdMember.getHouseholdId()!= null
                ? householdMember.getHouseholdId() :
                householdMember.getHouseholdClientReferenceId();
    }

    private String getColumnName(Method idMethod) {
        String columnName = "id";
        if ("getHouseholdClientReferenceId".equals(idMethod.getName())) {
            columnName = "clientReferenceId";
        }
        return columnName;
    }
}
