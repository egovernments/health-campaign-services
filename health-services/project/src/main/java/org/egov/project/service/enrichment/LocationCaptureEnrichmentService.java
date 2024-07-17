package org.egov.project.service.enrichment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.irs.LocationCapture;
import org.egov.common.models.project.irs.LocationCaptureBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.LocationCaptureRepository;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.enrichIdsFromExistingEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;

@Service
@Slf4j
public class LocationCaptureEnrichmentService {
    private final IdGenService idGenService;

    private final ProjectConfiguration projectConfiguration;

    private final LocationCaptureRepository locationCaptureRepository;

    @Autowired
    public LocationCaptureEnrichmentService(
            IdGenService idGenService,
            ProjectConfiguration projectConfiguration,
            LocationCaptureRepository locationCaptureRepository
    ) {
        this.idGenService = idGenService;
        this.projectConfiguration = projectConfiguration;
        this.locationCaptureRepository = locationCaptureRepository;
    }

    public void create(List<LocationCapture> entities, LocationCaptureBulkRequest request) throws Exception {
        log.info("starting the enrichment for create LocationCaptures");
        log.info("generating IDs using UUID");
        List<String> idList = CommonUtils.uuidSupplier().apply(entities.size());
        log.info("enriching LocationCaptures with generated IDs");
        enrichForCreate(entities, idList, request.getRequestInfo());
        log.info("enrichment done");
    }

}
