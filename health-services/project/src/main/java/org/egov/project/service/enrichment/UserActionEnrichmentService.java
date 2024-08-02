package org.egov.project.service.enrichment;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.UserActionRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.project.Constants.USER_ACTION_ENRICHMENT_ERROR;

@Service
@Slf4j
public class UserActionEnrichmentService {
    private final IdGenService idGenService;

    private final ProjectConfiguration projectConfiguration;

    private final UserActionRepository userActionRepository;

    @Autowired
    public UserActionEnrichmentService(
            IdGenService idGenService,
            ProjectConfiguration projectConfiguration,
            UserActionRepository userActionRepository
    ) {
        this.idGenService = idGenService;
        this.projectConfiguration = projectConfiguration;
        this.userActionRepository = userActionRepository;
    }

    public void create(List<UserAction> entities, UserActionBulkRequest request) {
        log.info("starting the enrichment for create UserActions");
        log.info("generating IDs using UUID");
        try {
            List<String> idList = CommonUtils.uuidSupplier().apply(entities.size());
            log.info("enriching UserActions with generated IDs");
            enrichForCreate(entities, idList, request.getRequestInfo(),false);
            log.info("enrichment done");
        } catch (Exception exception) {
            log.error("Error during enrichment for create UserActions", exception);
            throw new CustomException(USER_ACTION_ENRICHMENT_ERROR, "Error during enrichment for create UserActions" + exception);
        }
    }

    public void update(List<UserAction> entities, UserActionBulkRequest request) {
        log.info("starting the enrichment for update UserActions");
        try {
            Map<String, UserAction> userActionMap = getIdToObjMap(entities);
            enrichForUpdate(userActionMap, entities, request);
            log.info("enrichment done");
        } catch (Exception exception) {
            log.error("Error during enrichment for update UserActions", exception);
            throw new CustomException(USER_ACTION_ENRICHMENT_ERROR, "Error during enrichment for update UserActions" + exception);
        }
    }
}
