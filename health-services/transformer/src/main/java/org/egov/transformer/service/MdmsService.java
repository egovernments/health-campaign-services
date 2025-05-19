package org.egov.transformer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.*;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.INDEX_LABEL;

@Slf4j
@Service
public class MdmsService {

    private final ServiceRequestClient restRepo;
    private final String mdmsHost;
    private final String mdmsUrl;
    private final TransformerProperties transformerProperties;
    private static Map<String, String> transformerLocalizations = new HashMap<>();
    private static Map<String, String> transformerElasticIndexLabelsMap = new HashMap<>();
    private static Map<String, HashMap<String, Integer>> mdmsProjectStaffRolesRankCache = new ConcurrentHashMap<>();

    @Autowired
    public MdmsService(ServiceRequestClient restRepo,
                       @Value("${egov.mdms.host}") String mdmsHost,
                       @Value("${egov.mdms.search.endpoint}") String mdmsUrl, TransformerProperties transformerProperties) {
        this.restRepo = restRepo;
        this.mdmsHost = mdmsHost;
        this.mdmsUrl = mdmsUrl;
        this.transformerProperties = transformerProperties;
    }

    public <T> T fetchConfig(Object request, Class<T> clazz) throws Exception {
        T response;
        try {
            response = restRepo.fetchResult(new StringBuilder(mdmsHost + mdmsUrl), request, clazz);
        } catch (HttpClientErrorException e) {
            throw new CustomException("HTTP_CLIENT_ERROR",
                    String.format("%s - %s", e.getMessage(), e.getResponseBodyAsString()));
        }
        return response;
    }

    public String getMDMSTransformerLocalizations(String text, String tenantId) {
        if (transformerLocalizations.containsKey(text)) {
            log.info("Fetching localization from transformerLocalization: {}", text);
            return transformerLocalizations.get(text);
        }
        return fetchLocalizationsFromMdms(text, tenantId);
    }

    private String fetchLocalizationsFromMdms(String text, String tenantId) {
        JSONArray transformerLocalizationsArray;

        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, TRANSFORMER_LOCALIZATIONS, transformerProperties.getTransformerLocalizationsMdmsModule(), "");
        try {
            MdmsResponse mdmsResponse = fetchConfig(mdmsCriteriaReq, MdmsResponse.class);

//            TODO null checks has to be set below get mdms module and master because response is not having those
//            if there no mdms data in that module-master
            transformerLocalizationsArray = mdmsResponse.getMdmsRes().get(transformerProperties.getTransformerLocalizationsMdmsModule())
                    .get(TRANSFORMER_LOCALIZATIONS);
            ObjectMapper objectMapper = new ObjectMapper();
            transformerLocalizationsArray.forEach(item -> {
                Map map = objectMapper.convertValue(item, new TypeReference<Map>() {
                });
                transformerLocalizations.put((String) map.get("text"), (String) map.get("translatedText"));
            });
        } catch (Exception e) {
            log.error("error while fetching TRANFORMER_LOCALIZATIONS from MDMS: {}", ExceptionUtils.getStackTrace(e));
        }
        return transformerLocalizations.getOrDefault(text, text);
    }

    public HashMap<String, Integer> getProjectStaffRoles(String tenantId) {
        HashMap<String, Integer> projectStaffRolesRankingMap = mdmsProjectStaffRolesRankCache.getOrDefault(tenantId, new HashMap<>());
        if (!projectStaffRolesRankingMap.isEmpty()) {
            log.info("Fetching projectStaffRoles from cache for tenantId: {}", tenantId);
            return projectStaffRolesRankingMap;
        }

        String moduleName = transformerProperties.getProjectStaffRolesMdmsModule();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, PROJECT_STAFF_ROLES, moduleName, null);
        JSONArray projectStaffRolesArray = new JSONArray();
        try {
            MdmsResponse mdmsResponse = fetchConfig(mdmsCriteriaReq, MdmsResponse.class);
            projectStaffRolesArray = mdmsResponse.getMdmsRes().get(moduleName).get(PROJECT_STAFF_ROLES);
        } catch (Exception e) {
            log.error("Exception while fetching mdms roles: {}", ExceptionUtils.getStackTrace(e));
            return projectStaffRolesRankingMap;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        projectStaffRolesArray.forEach(role -> {
            LinkedHashMap map = objectMapper.convertValue(role, new TypeReference<LinkedHashMap>() {
            });
            projectStaffRolesRankingMap.put((String) map.get("code"), (Integer) map.get("rank"));
        });
        mdmsProjectStaffRolesRankCache.put(tenantId, projectStaffRolesRankingMap);
        return projectStaffRolesRankingMap;
    }

    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, String masterName,
                                           String moduleName, String filter) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        if (filter != null && !filter.isEmpty()) {
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
        mdmsCriteria.setTenantId(tenantId.split("\\.")[0]);
        mdmsCriteria.setModuleDetails(moduleDetailList);
        MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
        mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
        mdmsCriteriaReq.setRequestInfo(requestInfo);
        return mdmsCriteriaReq;
    }

    public String getMDMSTransformerElasticIndexLabels(String label, String tenantId) {
        if (transformerElasticIndexLabelsMap.containsKey(label)) {
            return transformerElasticIndexLabelsMap.get(label);
        }
        return fetchIndexLabelsFromMdms(label, tenantId);
    }

    private String fetchIndexLabelsFromMdms(String label, String tenantId) {
        JSONArray transformerElasticIndexLabelsArray = new JSONArray();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, TRANSFORMER_ELASTIC_INDEX_LABELS, transformerProperties.getTransformerElasticIndexLabelsMdmsModule(), "");
        try {
            MdmsResponse mdmsResponse = fetchConfig(mdmsCriteriaReq, MdmsResponse.class);
            transformerElasticIndexLabelsArray = mdmsResponse.getMdmsRes().get(transformerProperties.getTransformerElasticIndexLabelsMdmsModule())
                    .get(TRANSFORMER_ELASTIC_INDEX_LABELS);
            ObjectMapper objectMapper = new ObjectMapper();
            transformerElasticIndexLabelsArray.forEach(item -> {
                Map map = objectMapper.convertValue(item, new TypeReference<Map>() {
                });
                transformerElasticIndexLabelsMap.put((String) map.get(LABEL), (String) map.get(INDEX_LABEL));
            });
        } catch (Exception e) {
            log.error("error while fetching ELASTIC_INDEX_LABELS from MDMS: {}", ExceptionUtils.getStackTrace(e));
        }
        return transformerElasticIndexLabelsMap.getOrDefault(label, label);
    }

    public JsonNode fetchChecklistInfoFromMDMS(String tenantId, String checklistName){
        JsonNode checklistInfo = null;
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, transformerProperties.getTransformerChecklistInfoMDMSMaster(), transformerProperties.getTransformerChecklistInfoMDMSModule(), "");
        try {
            MdmsResponse mdmsResponse = fetchConfig(mdmsCriteriaReq, MdmsResponse.class);
            JSONArray mdmsArray = mdmsResponse.getMdmsRes().get(transformerProperties.getTransformerChecklistInfoMDMSModule())
                    .get(transformerProperties.getTransformerChecklistInfoMDMSMaster());
            if (mdmsArray != null && !mdmsArray.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode mdmsInfo = mapper.convertValue(mdmsArray, JsonNode.class);

                for (JsonNode node : mdmsInfo) {
                    if (node.has(checklistName)) {
                        checklistInfo = node.get(checklistName);
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("error while fetching checklist info from MDMS: {}", ExceptionUtils.getStackTrace(e));
        }
        return checklistInfo;
    }


}
