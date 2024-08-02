package org.egov.project.service.enrichment;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.LocationCaptureRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.project.Constants.LOCATION_CAPTURE_USER_ACTION_ENRICHMENT_ERROR;

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

    public void create(List<UserAction> entities, UserActionBulkRequest request) throws Exception {
        log.info("starting the enrichment for create LocationCaptures");
        log.info("generating IDs using UUID");
        try {
            List<String> idList = CommonUtils.uuidSupplier().apply(entities.size());
            log.info("enriching LocationCaptures with generated IDs");
            enrichForCreate(entities, idList, request.getRequestInfo());
            log.info("enrichment done");
        } catch (Exception exception) {
            log.error("Error during enrichment for create LocationCaptures", exception);
            throw new CustomException(LOCATION_CAPTURE_USER_ACTION_ENRICHMENT_ERROR, "Error during enrichment for create LocationCaptures" + exception);
        }
    }

}
