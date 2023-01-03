package org.egov.project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.IdGenService;
import org.egov.common.service.MdmsService;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.BeneficiaryRequest;
import org.egov.project.web.models.BeneficiaryResponse;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.project.web.models.HouseholdResponse;
import org.egov.project.web.models.HouseholdSearch;
import org.egov.project.web.models.HouseholdSearchRequest;
import org.egov.project.web.models.IndividualResponse;
import org.egov.project.web.models.IndividualSearch;
import org.egov.project.web.models.IndividualSearchRequest;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.project.web.models.ProjectType;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkRowVersion;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.identifyNullIds;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.validateEntities;
import static org.egov.common.utils.CommonUtils.validateIds;

@Service
@Slf4j
public class ProjectBeneficiaryService {

    public static final String SAVE_KAFKA_TOPIC = "save-project-beneficiary-topic";

    public static final String UPDATE_KAFKA_TOPIC = "update-project-beneficiary-topic";

    private final IdGenService idGenService;

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    private final MdmsService mdmsService;

    private final ProjectService projectService;

    private final ServiceRequestClient serviceRequestClient;

    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId,String masterName,String moduleName) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
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

    @Value("${egov.household.host}")
    private String householdServiceHost;

    @Value("${egov.search.household.url}")
    private String householdServiceSearchUrl;

    @Value("${egov.individual.host}")
    private String individualServiceHost;

    @Value("${egov.search.household.url}")
    private String individualServiceSearchUrl;

    @Autowired
    public ProjectBeneficiaryService(
            IdGenService idGenService,
            ProjectBeneficiaryRepository projectBeneficiaryRepository,
            ProjectService projectService,
            MdmsService mdmsService,
            ServiceRequestClient serviceRequestClient
    ) {
        this.idGenService = idGenService;
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
        this.projectService = projectService;
        this.mdmsService = mdmsService;
        this.serviceRequestClient = serviceRequestClient;
    }

    public List<ProjectBeneficiary> create(BeneficiaryRequest beneficiaryRequest) throws Exception {

        List<ProjectBeneficiary> projectBeneficiary = beneficiaryRequest.getProjectBeneficiary();

        String tenantId = getTenantId(projectBeneficiary);


        List<Project> existingProjects = projectService.findByIds(new ArrayList<>(getSet(projectBeneficiary, "getProjectId")));
        List<ProjectType> projectTypes = getProjectTypes(tenantId, beneficiaryRequest.getRequestInfo());

        Map<String, ProjectType> projectTypeMap = getIdToObjMap(projectTypes);
        Map<String, Project> projectMap = getIdToObjMap(existingProjects);


        // TODO - CHeck if project exists
        validateIds(getSet(projectBeneficiary, "getProjectId"), projectService::validateProjectIds);

        for (ProjectBeneficiary beneficiary : projectBeneficiary) {
            Project project = projectMap.get(beneficiary.getProjectId());
            String beneficiaryType = projectTypeMap.get(project.getProjectTypeId()).getBeneficiaryType();
            log.info(" beneficiaryType {} {}", beneficiaryType, project);

            // TODO - Search Beneficiary
            searchBeneficiary(beneficiaryType, beneficiary, beneficiaryRequest.getRequestInfo(), tenantId);

        }


        // TODO - GENERTAE ID
        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(beneficiaryRequest.getRequestInfo(),
                getTenantId(projectBeneficiary),
                "project.beneficiary.id",
                "",
                projectBeneficiary.size());

        log.info("IDs generated");

        enrichForCreate(projectBeneficiary, idList, beneficiaryRequest.getRequestInfo());
        log.info("Enrichment done");
        projectBeneficiaryRepository.save(projectBeneficiary,SAVE_KAFKA_TOPIC);
        log.info("Pushed to kafka");
        return projectBeneficiary;
    }

    private void searchBeneficiary(String beneficiaryType, ProjectBeneficiary beneficiary, RequestInfo requestInfo, String tenantId) throws Exception {
        Object response;

        switch (beneficiaryType) {
            case "HOUSEHOLD":
                response = searchHouseholdBeneficiary(beneficiary, requestInfo, tenantId);
                break;
            case "INDIVIDUAL":
                response = searchIndividualBeneficiary(beneficiary, requestInfo, tenantId);
                break;
            default:
                throw new CustomException("INVALID_BENEFICIARY_TYPE", beneficiaryType);
        }

        if (response instanceof HouseholdResponse && ((HouseholdResponse) response).getHousehold().isEmpty()) {
            throw new CustomException("INVALID_BENEFICIARY", beneficiary.getBeneficiaryId());
        } else if (response instanceof IndividualResponse && ((IndividualResponse) response).getIndividual().isEmpty()) {
            throw new CustomException("INVALID_BENEFICIARY", beneficiary.getBeneficiaryId());
        }
    }

    private HouseholdResponse searchHouseholdBeneficiary(ProjectBeneficiary beneficiary, RequestInfo requestInfo, String tenantId) throws Exception {
        HouseholdSearch householdSearch = HouseholdSearch
                .builder()
                .id(beneficiary.getBeneficiaryId())
                .build();

        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(requestInfo)
                .household(householdSearch)
                .build();

        return serviceRequestClient.fetchResult(
                new StringBuilder(householdServiceHost + householdServiceSearchUrl + "?limit=10&offset=0&tenantId=" + tenantId),
                householdSearchRequest,
                HouseholdResponse.class);
    }

    private IndividualResponse searchIndividualBeneficiary(ProjectBeneficiary beneficiary, RequestInfo requestInfo, String tenantId) throws Exception {
        IndividualSearch individualSearch = IndividualSearch
                .builder()
                .id(beneficiary.getBeneficiaryId())
                .build();

        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .requestInfo(requestInfo)
                .individual(individualSearch)
                .build();

        return serviceRequestClient.fetchResult(
                new StringBuilder(individualServiceHost + individualServiceSearchUrl + "?limit=10&offset=0&tenantId=" + tenantId),
                individualSearchRequest,
                IndividualResponse.class);
    }

    private List<ProjectType> getProjectTypes(String tenantId, RequestInfo requestInfo) throws Exception {
        JsonNode response = fetchMdmsResponse(requestInfo, tenantId, "projectTypes", "HCM-PROJECT-TYPES");
        return convertToProjectTypeList(response);
    }

    private JsonNode fetchMdmsResponse(RequestInfo requestInfo, String tenantId, String name, String moduleName) throws Exception {
        MdmsCriteriaReq serviceRegistry = getMdmsRequest(requestInfo, tenantId, name, moduleName);
        return mdmsService.fetchResultFromMdms(serviceRegistry, JsonNode.class).get("MdmsRes");
    }

    private List<ProjectType> convertToProjectTypeList(JsonNode jsonNode) {
        JsonNode projectTypesNode = jsonNode.get("HCM-PROJECT-TYPES").withArray("projectTypes");
        return new ObjectMapper().convertValue(projectTypesNode, new TypeReference<List<ProjectType>>() {});
    }

    public List<ProjectBeneficiary> update(BeneficiaryRequest beneficiaryRequest) {
        List<ProjectBeneficiary> projectBeneficiary = beneficiaryRequest.getProjectBeneficiary();

        identifyNullIds(projectBeneficiary);
        log.info("Checking existing project beneficiary");
        validateIds(getSet(projectBeneficiary, "getProjectId"),
                projectService::validateProjectIds);
        log.info("Checking existing project beneficiary");


        // TODO - CHeck if client reference id or server reference id exists
        List<String> ids = projectBeneficiary.stream().map(ProjectBeneficiary::getBeneficiaryClientReferenceId)
                .filter(Objects::nonNull).collect(Collectors.toList());

        List<String> alreadyExists = projectBeneficiaryRepository.validateIds(ids, "beneficiaryClientReferenceId");

        if(alreadyExists.size() != 0 ){
            throw new CustomException("ERROR","ERROR");
        }
        Map<String, ProjectBeneficiary> projectBeneficiaryMap = getIdToObjMap(projectBeneficiary);

        log.info("Checking if already exists");
        List<String> projectBeneficiaryIds = new ArrayList<>(projectBeneficiaryMap.keySet());
        List<ProjectBeneficiary> existingProjectBeneficiaryIds = projectBeneficiaryRepository
                .findById(projectBeneficiaryIds);

        validateEntities(projectBeneficiaryMap, existingProjectBeneficiaryIds);

        checkRowVersion(projectBeneficiaryMap, existingProjectBeneficiaryIds);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(projectBeneficiaryMap, existingProjectBeneficiaryIds, beneficiaryRequest);

        projectBeneficiaryRepository.save(projectBeneficiary, UPDATE_KAFKA_TOPIC);
        log.info("Pushed to kafka");
        return projectBeneficiary;
    }

    public List<ProjectBeneficiary> search(BeneficiarySearchRequest beneficiarySearchRequest,
                                     Integer limit,
                                     Integer offset,
                                     String tenantId,
                                     Long lastChangedSince,
                                     Boolean includeDeleted) throws Exception {

        if (isSearchByIdOnly(beneficiarySearchRequest.getProjectBeneficiary())) {
            List<String> ids = new ArrayList<>();
            ids.add(beneficiarySearchRequest.getProjectBeneficiary().getId());
            return projectBeneficiaryRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return projectBeneficiaryRepository.find(beneficiarySearchRequest.getProjectBeneficiary(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

}
