package org.egov.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.UserDetailResponse;
import digit.models.coremodels.UserSearchRequest;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.service.IdGenService;
import org.egov.common.service.MdmsService;
import org.egov.common.service.UserService;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.BeneficiaryRequest;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.project.web.models.ProjectSearch;
import org.egov.project.web.models.ProjectSearchRequest;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.egov.project.web.models.ProjectType;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkRowVersion;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdList;
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

    @Autowired
    public ProjectBeneficiaryService(
            IdGenService idGenService,
            ProjectBeneficiaryRepository projectBeneficiaryRepository,
            ProjectService projectService,
            MdmsService mdmsService
    ) {
        this.idGenService = idGenService;
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
        this.projectService = projectService;
        this.mdmsService = mdmsService;
    }

    public List<ProjectBeneficiary> create(BeneficiaryRequest beneficiaryRequest) throws Exception {
        List<ProjectBeneficiary> projectBeneficiary = beneficiaryRequest.getProjectBeneficiary();
        String tenantId = getTenantId(projectBeneficiary);
        // TODO - CHeck if client reference id or server reference id exists

        // TODO - CHeck if project exists
        validateIds(getSet(projectBeneficiary, "getProjectId"), projectService::validateProjectIds);

        // TODO - Fetch beneficiary from mdms based on project
        List<String> beneficiaryTypes = getBeneficiaryTypes(projectBeneficiary, tenantId, beneficiaryRequest.getRequestInfo());
        // TODO - Search beneficiary

        // TODO - GENERTAE ID

        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(beneficiaryRequest.getRequestInfo(),
                getTenantId(projectBeneficiary),
                "project.beneficiary.id", "", projectBeneficiary.size());
        log.info("IDs generated");

        enrichForCreate(projectBeneficiary, idList, beneficiaryRequest.getRequestInfo());
        log.info("Enrichment done");
        projectBeneficiaryRepository.save(projectBeneficiary,SAVE_KAFKA_TOPIC);
        log.info("Pushed to kafka");
        return projectBeneficiary;
    }

    private List<String> getBeneficiaryTypes(List<ProjectBeneficiary> projectBeneficiary, String tenantId, @NotNull @Valid RequestInfo requestInfo) throws Exception {
        List<Project> existingProjects = projectService.findByIds(new ArrayList<>(getSet(projectBeneficiary, "getProjectId")));

        MdmsCriteriaReq serviceRegistry = getMdmsRequest(
                requestInfo,
                tenantId,
                "projectTypes",
                "HCM-PROJECT-TYPES"
        );

        log.info(serviceRegistry.toString());
        JsonNode response = mdmsService.fetchResultFromMdms(serviceRegistry, JsonNode.class);

        ObjectMapper mapper = new ObjectMapper();
//        List<ProjectType> projectTypes = mapper.readValue(response.get("MdmsRes").get("HCM-PROJECT-TYPES").get("projectTypes"),
//                ProjectType.class);


        // make call to mdms using projectTypeId
        return null;
    }


    public List<ProjectBeneficiary> update(BeneficiaryRequest beneficiaryRequest) {
        List<ProjectBeneficiary> projectBeneficiary = beneficiaryRequest.getProjectBeneficiary();

        identifyNullIds(projectBeneficiary);
        log.info("Checking existing project beneficiary");
        validateIds(getSet(projectBeneficiary, "getProjectId"),
                projectService::validateProjectIds);
        log.info("Checking existing project beneficiary");

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
