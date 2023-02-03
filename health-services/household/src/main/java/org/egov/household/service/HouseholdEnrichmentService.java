package org.egov.household.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.web.models.Address;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdBulkRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.enrichId;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Component
@Slf4j
public class HouseholdEnrichmentService {
    private final IdGenService idGenService;

    private final HouseholdConfiguration configuration;

    public HouseholdEnrichmentService(IdGenService idGenService, HouseholdConfiguration configuration) {
        this.idGenService = idGenService;
        this.configuration = configuration;
    }

    public void create(List<Household> households, HouseholdBulkRequest request) throws Exception {
        List<String> idList =  idGenService.getIdList(request.getRequestInfo(),
                getTenantId(households),
                configuration.getIdgenFormat(), "", households.size());
        enrichForCreate(households, idList, request.getRequestInfo());

        List<Address> addresses = households.stream().map(Household::getAddress)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            List<String> ids = uuidSupplier().apply(addresses.size());
            enrichId(addresses, ids);
        }
    }

    public void update(List<Household> households, HouseholdBulkRequest request) {
        List<Address> addresses = households.stream().map(Household::getAddress)
                .filter(Objects::nonNull).filter(address ->  address.getId() == null).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            List<String> ids = uuidSupplier().apply(addresses.size());
            enrichId(addresses, ids);
        }

        Map<String, Household> hMap = getIdToObjMap(households);
        enrichForUpdate(hMap, request);
    }

    public void delete(List<Household> households, HouseholdBulkRequest request) {
        for (Household household : households) {
            if (household.getIsDeleted()) {
                AuditDetails auditDetails = getAuditDetailsForUpdate(household.getAuditDetails(),
                        request.getRequestInfo().getUserInfo().getUuid());
                household.setAuditDetails(auditDetails);
                household.setRowVersion(household.getRowVersion() + 1);
            }
        }
    }
}
