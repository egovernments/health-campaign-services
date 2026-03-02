package org.egov.healthnotification.service;

import com.fasterxml.jackson.databind.JsonNode;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.egov.healthnotification.web.models.MdmsV2Criteria;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.healthnotification.web.models.MdmsV2Response;
import org.egov.healthnotification.web.models.MdmsV2SearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with MDMS (Master Data Management Service).
 * Used to fetch notification templates, configuration data, and other master data.
 */
@Service
@Slf4j
public class MdmsService {

    private final ServiceRequestClient serviceRequestClient;
    private final HealthNotificationProperties properties;

    @Autowired
    public MdmsService(ServiceRequestClient serviceRequestClient,
                       HealthNotificationProperties properties) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
    }

    /**
     * Generic method to fetch MDMS configuration.
     *
     * @param request The MDMS criteria request
     * @param clazz The response class type
     * @param <T> Response type
     * @return The MDMS response
     * @throws Exception if fetch fails
     */
    public <T> T fetchConfig(MdmsCriteriaReq request, Class<T> clazz) throws Exception {
        T response;
        try {
            StringBuilder uri = new StringBuilder(properties.getMdmsHost())
                    .append(properties.getMdmsSearchEndpoint());

            response = serviceRequestClient.fetchResult(uri, request, clazz);
        } catch (HttpClientErrorException e) {
            log.error("HTTP error while fetching MDMS config: {}", e.getMessage());
            throw new CustomException("HTTP_CLIENT_ERROR",
                    String.format("%s - %s", e.getMessage(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Error while fetching MDMS config", e);
            throw new CustomException("MDMS_FETCH_ERROR",
                    "Error while fetching MDMS configuration: " + e.getMessage());
        }
        return response;
    }

    /**
     * Fetches notification templates from MDMS.
     *
     * @param tenantId The tenant ID
     * @param templateCode The template code to search for (optional filter)
     * @return JsonNode containing template data
     */
    public JsonNode fetchNotificationTemplates(String tenantId, String templateCode) {
        log.info("Fetching notification templates for tenant: {}, templateCode: {}",
                tenantId, templateCode);

        String filter = templateCode != null
                ? "$[?(@.code == '" + templateCode + "')]"
                : null;

        MdmsCriteriaReq request = buildMdmsRequest(
                tenantId,
                "NotificationConfig",
                properties.getNotificationModule(),
                filter);

        try {
            return fetchConfig(request, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching notification templates", e);
            throw new CustomException("MDMS_TEMPLATE_FETCH_ERROR",
                    "Error fetching notification templates for tenant: " + tenantId);
        }
    }

    /**
     * Builds MDMS criteria request.
     *
     * @param tenantId The tenant ID
     * @param masterName The master data name
     * @param moduleName The module name
     * @param filter Optional JSON path filter
     * @return MdmsCriteriaReq object
     */
    private MdmsCriteriaReq buildMdmsRequest(String tenantId, String masterName,
                                              String moduleName, String filter) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        if (filter != null) {
            masterDetail.setFilter(filter);
        }

        List<MasterDetail> masterDetailList = new ArrayList<>();
        masterDetailList.add(masterDetail);

        ModuleDetail moduleDetail = new ModuleDetail();
        moduleDetail.setMasterDetails(masterDetailList);
        moduleDetail.setModuleName(moduleName);

        List<ModuleDetail> moduleDetailList = new ArrayList<>();
        moduleDetailList.add(moduleDetail);

        MdmsCriteria mdmsCriteria = new MdmsCriteria();
        mdmsCriteria.setTenantId(tenantId.split("\\.")[0]); // Get state level tenant
        mdmsCriteria.setModuleDetails(moduleDetailList);

        MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
        mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
        mdmsCriteriaReq.setRequestInfo(RequestInfoUtil.buildSystemRequestInfo());

        return mdmsCriteriaReq;
    }

    /**
     * Fetches notification configuration from MDMS v2 for a specific project type.
     *
     * @param projectType The project type (campaign type)
     * @param tenantId The tenant ID
     * @return MdmsV2Data containing notification configuration
     * @throws CustomException if no configuration found
     */
    public MdmsV2Data fetchNotificationConfigByProjectType(String projectType, String tenantId) {
        log.info("Fetching notification config for projectType: {}, tenantId: {}",
                projectType, tenantId);

        // Build filters for campaign type
        Map<String, String> filters = new HashMap<>();
        filters.put("campaignType", projectType);

        // Build MDMS v2 criteria
        MdmsV2Criteria mdmsCriteria = MdmsV2Criteria.builder()
                .tenantId(tenantId.split("\\.")[0]) // Get state level tenant
                .schemaCode(properties.getNotificationModule() + ".NotificationConfig")
                .filters(filters)
                .limit(100000)
                .offset(0)
                .build();

        // Build MDMS v2 search request
        MdmsV2SearchRequest searchRequest = MdmsV2SearchRequest.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .mdmsCriteria(mdmsCriteria)
                .build();

        try {
            StringBuilder uri = new StringBuilder(properties.getMdmsHost())
                    .append(properties.getMdmsSearchV2Endpoint());

            MdmsV2Response response = serviceRequestClient.fetchResult(uri, searchRequest, MdmsV2Response.class);

            if (response == null || CollectionUtils.isEmpty(response.getMdms())) {
                log.error("No MDMS notification configuration found for projectType: {}, tenantId: {}",
                        projectType, tenantId);
                throw new CustomException("MDMS_NOTIFICATION_CONFIG_NOT_FOUND",
                        String.format("No notification configuration found for projectType: %s, tenantId: %s",
                                projectType, tenantId));
            }

            MdmsV2Data notificationConfig = response.getMdms().get(0);
            log.info("Successfully fetched notification config for projectType: {}", projectType);
            return notificationConfig;

        } catch (HttpClientErrorException e) {
            log.error("HTTP error while fetching MDMS v2 notification config: {}", e.getMessage());
            throw new CustomException("HTTP_CLIENT_ERROR",
                    String.format("%s - %s", e.getMessage(), e.getResponseBodyAsString()));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while fetching MDMS v2 notification config", e);
            throw new CustomException("MDMS_V2_FETCH_ERROR",
                    "Error while fetching MDMS v2 notification configuration: " + e.getMessage());
        }
    }
}
