package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberRelationship;
import org.egov.common.service.IdGenService;
import org.egov.household.repository.HouseholdMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForUpdate;
import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.uuidSupplier;
import static org.egov.household.utils.CommonUtils.getColumnName;

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
        log.info("generating uuids for household members");
        List<String> uuidList = uuidSupplier().apply(householdMembers.size());
        log.info("enriching household for household members");
        enrichHousehold(householdMembers);
        log.info("enriching household members for create request");
        enrichForCreate(householdMembers, uuidList, request.getRequestInfo());
        enrichRelationshipsForCreate(request, householdMembers);
        log.info("completed enriching household members for create request");
    }

    public void update(List<HouseholdMember> householdMembers,
                       HouseholdMemberBulkRequest beneficiaryRequest) {
        log.info("updating household members");
        log.info("enriching household for household members");
        enrichHousehold(householdMembers);
        Map<String, HouseholdMember> hMap = getIdToObjMap(householdMembers);
        List<String> householdMemberIds = new ArrayList<>(hMap.keySet());
        List<HouseholdMember> existingHouseholdMembers = householdMemberRepository.findById(householdMemberIds,
                "id", false).getResponse();
        log.info("updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(hMap, existingHouseholdMembers, beneficiaryRequest);
        enrichRelationshipsForUpdate(beneficiaryRequest, householdMembers, existingHouseholdMembers);
        log.info("household Members updated successfully.");
    }

    public void enrichHousehold(List<HouseholdMember> householdMembers) {
        log.info("getting method for householdId and householdClientReferenceId");
        Method idMethod = getIdMethod(householdMembers, "householdId",
                "householdClientReferenceId");
        String columnName = getColumnName(idMethod);
        log.info("getting houseHoldIds for householdMembers");
        List<String> houseHoldIds = getIdList(householdMembers, idMethod);
        log.info("finding households from householdService with ids: {}", houseHoldIds);
        List<Household> householdList = householdService.findById(houseHoldIds, columnName, false).getResponse();
        log.info("getting method for householdList with columnName: {}", columnName);
        Method householdMethod = getIdMethod(householdList, columnName);
        log.info("getting Map of households");
        Map<String, Household> householdMap = getIdToObjMap(householdList, householdMethod);
        log.info("enriching householdMembers with households");
        for (HouseholdMember householdMember : householdMembers) {
            enrichWithHouseholdId(householdMap, householdMember);
        }
    }

    public void delete(List<HouseholdMember> householdMembers,
                       HouseholdMemberBulkRequest beneficiaryRequest) {
        log.info("enriching HouseholdMember with delete information before deletion");
        if (!CollectionUtils.isEmpty(householdMembers)) {
            for (HouseholdMember householdMember: householdMembers) {
                List<HouseholdMemberRelationship> relationships = householdMember.getRelationships();
                if (!CollectionUtils.isEmpty(relationships)) {
                    enrichForDelete(relationships, beneficiaryRequest.getRequestInfo(), true);
                }
            }
        }
        enrichForDelete(householdMembers, beneficiaryRequest.getRequestInfo(), true);
    }

    private void enrichWithHouseholdId(Map<String, Household> householdMap, HouseholdMember householdMember) {
        log.info("enriching householdMember with household id and householdClientReferenceId");
        Household household = householdMap.get(getHouseholdId(householdMember));
        householdMember.setHouseholdId(household.getId());
        householdMember.setHouseholdClientReferenceId(household.getClientReferenceId());
    }

    private String getHouseholdId(HouseholdMember householdMember) {
        String householdId = householdMember.getHouseholdId()!= null
                ? householdMember.getHouseholdId() :
                householdMember.getHouseholdClientReferenceId();
        log.info("retrieved householdId");
        return householdId;
    }

    private static void enrichRelationshipsForCreate(HouseholdMemberBulkRequest request,
                                                 List<HouseholdMember> validHouseholdMembers) {
        for (HouseholdMember householdMember : validHouseholdMembers) {
            log.info("enriching relationships");
            List<HouseholdMemberRelationship> relationships = householdMember.getRelationships();
            if(CollectionUtils.isEmpty(relationships))
                continue;
            List<String> ids = uuidSupplier().apply(relationships.size());
            enrichForCreate(relationships, ids, request.getRequestInfo(), false);
            enrichRelationshipsForCreate(request, relationships, householdMember);
        }
    }

    private static void enrichRelationshipsForCreate(HouseholdMemberBulkRequest request,
                                                 List<HouseholdMemberRelationship> relationships, HouseholdMember householdMember) {
        log.info("enriching resources");
        List<String> ids = uuidSupplier().apply(relationships.size());
        enrichForCreate(relationships, ids, request.getRequestInfo(), true);
        relationships.forEach(relationship -> {
            relationship.setHouseholdMemberId(householdMember.getId());
            relationship.setHouseholdMemberClientReferenceId(householdMember.getClientReferenceId());
            relationship.setTenantId(householdMember.getTenantId());
        });
    }

    private static void enrichRelationshipsForUpdate(HouseholdMemberBulkRequest request, List<HouseholdMember> householdMembers, List<HouseholdMember> existingHouseholdMembers) {
        log.info("enriching relationships for update");
        for (HouseholdMember householdMember : householdMembers) {
            List<HouseholdMemberRelationship> resourcesToCreate = new ArrayList<>();
            List<HouseholdMemberRelationship> resourcesToUpdate = new ArrayList<>();
            List<HouseholdMemberRelationship> updatedResources = householdMember.getRelationships();
            if(!CollectionUtils.isEmpty(updatedResources)) {
                resourcesToCreate = householdMember.getRelationships().stream()
                        .filter(r -> r.getId() == null).toList();
                resourcesToUpdate = householdMember.getRelationships().stream()
                        .filter(r -> r.getId() != null).toList();
            }

            if (!CollectionUtils.isEmpty(resourcesToCreate)) {
                 enrichRelationshipsForCreate(request, resourcesToCreate, householdMember);
            }
            if (!CollectionUtils.isEmpty(resourcesToUpdate)) {
                Map<String, HouseholdMemberRelationship> hmrMap = getIdToObjMap(resourcesToUpdate);
                enrichForUpdate(hmrMap, resourcesToUpdate, request);
            }
        }
    }
}
