package org.egov.household.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.household.config.HouseholdConfiguration;
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
        log.info("starting create method for households");

        log.info("generating IDs for households using IdGenService");
        List<String> idList =  idGenService.getIdList(request.getRequestInfo(),
                getTenantId(households),
                configuration.getIdgenFormat(), "", households.size());

        log.info("enriching households with generated IDs");
        enrichForCreate(households, idList, request.getRequestInfo());

        log.info("filtering households that have addresses");
        List<Address> addresses = households.stream().map(Household::getAddress)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            log.info("generating IDs for addresses");
            List<String> ids = uuidSupplier().apply(addresses.size());
            log.info("enriching addresses with generated IDs");
            enrichId(addresses, ids);
        }
        log.info("completed create method for households");
    }

    public void update(List<Household> households, HouseholdBulkRequest request) {
        log.info("starting update method for households");
        log.info("filtering households that have addresses without IDs");
        List<Address> addresses = households.stream().map(Household::getAddress)
                .filter(Objects::nonNull).filter(address ->  address.getId() == null).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            log.info("generating IDs for addresses");
            List<String> ids = uuidSupplier().apply(addresses.size());
            log.info("enriching addresses with generated IDs");
            enrichId(addresses, ids);
        }
        Map<String, Household> hMap = getIdToObjMap(households);
        log.info("enriching households for update");
        enrichForUpdate(hMap, request);
        log.info("completed update method for households {}", households);

    }

    public void delete(List<Household> households, HouseholdBulkRequest request) {
        log.info("starting delete method for households {}", households);
        for (Household household : households) {
            if (household.getIsDeleted()) {
                log.info("updating audit details for deleted households");
                AuditDetails auditDetails = getAuditDetailsForUpdate(household.getAuditDetails(),
                        request.getRequestInfo().getUserInfo().getUuid());
                household.setAuditDetails(auditDetails);
                household.setRowVersion(household.getRowVersion() + 1);
            }
        }
        log.info("completed delete method for households {}", households);
    }
}
