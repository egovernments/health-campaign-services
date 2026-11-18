package org.egov.facility.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.facility.Facility;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.repository.FacilityRepository;
import org.egov.facility.repository.FacilityStaffRepository;
import org.egov.facility.web.models.staff.FacilityStaff;
import org.egov.facility.web.models.staff.FacilityStaffRequest;
import org.egov.facility.web.models.staff.FacilityStaffSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import static org.egov.facility.validator.FAllowedFacilityValidator.FACILITY_ACCESS_DENIED;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class FacilityStaffService {

    private final FacilityStaffRepository facilityStaffRepository;

    private final FacilityRepository facilityRepository;

    private final FacilityConfiguration configuration;

    private final FacilityAccessService facilityAccessService;

    public FacilityStaffService(FacilityStaffRepository facilityStaffRepository,
                                FacilityRepository facilityRepository,
                                FacilityConfiguration configuration,
                                FacilityAccessService facilityAccessService) {
        this.facilityStaffRepository = facilityStaffRepository;
        this.facilityRepository = facilityRepository;
        this.configuration = configuration;
        this.facilityAccessService = facilityAccessService;
    }

    public List<FacilityStaff> create(FacilityStaffRequest request) throws Exception {
        denyRestrictedCaller(request.getRequestInfo());
        List<FacilityStaff> entities = request.getFacilityStaff();
        String tenantId = singleTenantId(entities);
        validateFacilitiesExist(tenantId, entities);
        validateNoDuplicates(tenantId, entities, Set.of());
        enrichForCreate(entities, uuidSupplier().apply(entities.size()), request.getRequestInfo());
        facilityStaffRepository.save(entities, configuration.getCreateFacilityStaffTopic());
        return entities;
    }

    public List<FacilityStaff> update(FacilityStaffRequest request) throws Exception {
        denyRestrictedCaller(request.getRequestInfo());
        List<FacilityStaff> entities = request.getFacilityStaff();
        String tenantId = singleTenantId(entities);
        List<FacilityStaff> existing = fetchExisting(tenantId, entities);
        Map<String, FacilityStaff> existingById = existing.stream()
                .collect(Collectors.toMap(FacilityStaff::getId, Function.identity()));
        entities.forEach(entity -> {
            if (!Objects.equals(entity.getRowVersion(), existingById.get(entity.getId()).getRowVersion())) {
                throw new CustomException("MISMATCHED_ROW_VERSION",
                        "Row version mismatch for facility staff " + entity.getId());
            }
        });
        validateFacilitiesExist(tenantId, entities);
        validateNoDuplicates(tenantId, entities, existingById.keySet());
        enrichForUpdate(getIdToObjMap(entities), existing, request);
        facilityStaffRepository.save(entities, configuration.getUpdateFacilityStaffTopic());
        return entities;
    }

    public List<FacilityStaff> delete(FacilityStaffRequest request) throws Exception {
        denyRestrictedCaller(request.getRequestInfo());
        List<FacilityStaff> entities = request.getFacilityStaff();
        String tenantId = singleTenantId(entities);
        List<FacilityStaff> existing = fetchExisting(tenantId, entities);
        enrichForDelete(existing, request.getRequestInfo(), true);
        facilityStaffRepository.save(existing, configuration.getDeleteFacilityStaffTopic());
        return existing;
    }

    public SearchResponse<FacilityStaff> search(FacilityStaffSearchRequest request, Integer limit, Integer offset,
                                                String tenantId, Long lastChangedSince,
                                                Boolean includeDeleted) throws Exception {
        if (facilityAccessService.isRestricted(request.getRequestInfo())) {
            request.getFacilityStaff().setStaffId(new ArrayList<>(List.of(request.getRequestInfo().getUserInfo().getUuid())));
        }
        return facilityStaffRepository.findWithCount(request.getFacilityStaff(), limit, offset, tenantId,
                lastChangedSince, includeDeleted);
    }

    private void denyRestrictedCaller(RequestInfo requestInfo) {
        if (facilityAccessService.isRestricted(requestInfo)) {
            throw new CustomException(FACILITY_ACCESS_DENIED, "User is not allowed to manage facility staff mappings");
        }
    }

    private String singleTenantId(List<FacilityStaff> entities) {
        Set<String> tenantIds = entities.stream().map(FacilityStaff::getTenantId).collect(Collectors.toSet());
        if (tenantIds.size() != 1 || tenantIds.contains(null)) {
            throw new CustomException("INVALID_TENANT_ID", "All facility staff must have the same non-null tenantId");
        }
        return tenantIds.iterator().next();
    }

    private List<FacilityStaff> fetchExisting(String tenantId, List<FacilityStaff> entities) throws Exception {
        List<String> ids = entities.stream().map(FacilityStaff::getId).filter(Objects::nonNull).distinct().toList();
        if (ids.size() != entities.size()) {
            throw new CustomException("NULL_OR_DUPLICATE_ID", "Every facility staff must have a unique id");
        }
        List<FacilityStaff> existing = facilityStaffRepository.findById(tenantId, new ArrayList<>(ids), false);
        if (existing.size() != ids.size()) {
            Set<String> found = existing.stream().map(FacilityStaff::getId).collect(Collectors.toSet());
            throw new CustomException("NON_EXISTENT_ENTITY", "Facility staff not found: "
                    + ids.stream().filter(id -> !found.contains(id)).toList());
        }
        return existing;
    }

    private void validateFacilitiesExist(String tenantId, List<FacilityStaff> entities) throws Exception {
        List<String> facilityIds = entities.stream().map(FacilityStaff::getFacilityId).distinct().toList();
        Set<String> valid = facilityRepository.findById(tenantId, new ArrayList<>(facilityIds), "id", false)
                .getResponse().stream().map(Facility::getId).collect(Collectors.toSet());
        List<String> invalid = facilityIds.stream().filter(id -> !valid.contains(id)).toList();
        if (!invalid.isEmpty()) {
            throw new CustomException("INVALID_FACILITY_ID", "Facilities not found: " + invalid);
        }
    }

    private void validateNoDuplicates(String tenantId, List<FacilityStaff> entities, Set<String> ownIds)
            throws Exception {
        Set<String> seen = new HashSet<>();
        for (FacilityStaff entity : entities) {
            if (!seen.add(pairKey(entity))) {
                throw new CustomException("DUPLICATE_FACILITY_STAFF", "Duplicate mapping in request: " + pairKey(entity));
            }
        }
        List<String> facilityIds = entities.stream().map(FacilityStaff::getFacilityId).distinct().toList();
        List<String> staffIds = entities.stream().map(FacilityStaff::getStaffId).distinct().toList();
        List<String> clashes = facilityStaffRepository.findActive(tenantId, facilityIds, staffIds).stream()
                .filter(row -> !ownIds.contains(row.getId()))
                .map(this::pairKey)
                .filter(seen::contains)
                .toList();
        if (!clashes.isEmpty()) {
            throw new CustomException("DUPLICATE_FACILITY_STAFF", "Mapping already exists: " + clashes);
        }
    }

    private String pairKey(FacilityStaff entity) {
        return entity.getFacilityId() + ":" + entity.getStaffId();
    }
}
