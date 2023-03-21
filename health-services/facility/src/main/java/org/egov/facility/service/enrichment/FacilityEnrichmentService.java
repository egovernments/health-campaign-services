package org.egov.facility.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.Address;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.facility.config.FacilityConfiguration;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.enrichId;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class FacilityEnrichmentService {

    private final IdGenService idGenService;

    private final FacilityConfiguration configuration;

    public FacilityEnrichmentService(IdGenService idGenService, FacilityConfiguration configuration) {
        this.idGenService = idGenService;
        this.configuration = configuration;
    }

    public void create(List<Facility> entities, FacilityBulkRequest request) throws Exception {
        log.info("starting create method for facility enrichment");
        log.info("generating IDs for facility enrichment using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(entities),
                configuration.getFacilityIdFormat(), "", entities.size());

        log.info("enriching facility enrichment with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());

        log.info("filtering facilities that have addresses");
        List<Address> addresses = entities.stream().map(Facility::getAddress)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            log.info("generating IDs for addresses");
            List<String> ids = uuidSupplier().apply(addresses.size());
            log.info("enriching addresses with generated IDs");
            enrichId(addresses, ids);
        }

        log.info("completed create method for facility enrichment");
    }

    public void update(List<Facility> entities, FacilityBulkRequest request) {
        log.info("starting update method for facility enrichment");
        log.info("filtering facility that have addresses without IDs");
        List<Address> addresses = entities.stream().map(Facility::getAddress)
                .filter(Objects::nonNull).filter(address ->  address.getId() == null).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            log.info("generating IDs for addresses");
            List<String> ids = uuidSupplier().apply(addresses.size());
            log.info("enriching addresses with generated IDs");
            enrichId(addresses, ids);
        }

        Map<String, Facility> idToObjMap = getIdToObjMap(entities);

        log.info("enriching facility enrichment with generated IDs");
        enrichForUpdate(idToObjMap, entities, request);

        log.info("completed update method for facility enrichment");
    }

    public void delete(List<Facility> entities, FacilityBulkRequest request) {
        log.info("starting delete method for facility enrichment");

        log.info("enriching facility enrichment with generated IDs");
        enrichForDelete(entities, request.getRequestInfo(), true);

        log.info("completed delete method for facility enrichment");
    }
}
