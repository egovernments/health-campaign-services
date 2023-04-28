package org.egov.project.validator.beneficiary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectType;
import org.egov.common.service.MdmsService;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.ProjectService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.project.Constants.BENEFICIARY_CLIENT_REFERENCE_ID;
import static org.egov.project.Constants.BENEFICIARY_ID;
import static org.egov.project.Constants.GET_CLIENT_REFERENCE_ID;
import static org.egov.project.Constants.GET_ID;
import static org.egov.project.Constants.GET_PROJECT_ID;
import static org.egov.project.Constants.INTERNAL_SERVER_ERROR;
import static org.egov.project.Constants.MDMS_RESPONSE;
import static org.egov.project.Constants.PROJECT_TYPES;

@Component
@Order(value = 4)
@Slf4j
public class BeneficiaryValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    private final MdmsService mdmsService;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectService projectService;

    private final ProjectConfiguration projectConfiguration;

    public BeneficiaryValidator(MdmsService mdmsService, ServiceRequestClient serviceRequestClient,
                                ProjectService projectService, ProjectConfiguration projectConfiguration) {
        this.mdmsService = mdmsService;
        this.serviceRequestClient = serviceRequestClient;
        this.projectService = projectService;
        this.projectConfiguration = projectConfiguration;
    }

    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest beneficiaryBulkRequest) {
        log.info("validating the beneficiary");
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectBeneficiary> validProjectBeneficiaries = beneficiaryBulkRequest.getProjectBeneficiaries()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validProjectBeneficiaries.isEmpty()) {
            String tenantId = getTenantId(validProjectBeneficiaries);

            Set<String> projectIds = getSet(validProjectBeneficiaries, GET_PROJECT_ID);

            log.info("fetch the projects");
            List<Project> existingProjects = projectService.findByIds(new ArrayList<>(projectIds));
            log.info("fetch the project types");
            List<ProjectType> projectTypes = getProjectTypes(tenantId, beneficiaryBulkRequest.getRequestInfo());

            log.info("creating projectType map");
            Map<String, ProjectType> projectTypeMap = getIdToObjMap(projectTypes);
            log.info("creating project map");
            Map<String, Project> projectMap = getIdToObjMap(existingProjects);

            log.info("creating beneficiaryType map");
            Map<String, List<ProjectBeneficiary>> beneficiaryTypeMap = validProjectBeneficiaries.stream()
                    .collect(Collectors.groupingBy(b -> projectTypeMap.get(projectMap.get(b
                            .getProjectId()).getProjectTypeId()).getBeneficiaryType()));

            for (Map.Entry<String, List<ProjectBeneficiary>> entry : beneficiaryTypeMap.entrySet()) {
                log.info("fetch the beneficiaries for type {}", entry.getKey());
                searchBeneficiary(entry.getKey(), entry.getValue(), beneficiaryBulkRequest.getRequestInfo(),
                        tenantId, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }

    private void searchBeneficiary(String beneficiaryType, List<ProjectBeneficiary> beneficiaryList,
                                   RequestInfo requestInfo, String tenantId,
                                   Map<ProjectBeneficiary, List<Error>> errorDetailsMap) {
        switch (beneficiaryType) {
            case "HOUSEHOLD":
                searchHouseholdBeneficiary(beneficiaryList, requestInfo, tenantId, errorDetailsMap);
                break;
            case "INDIVIDUAL":
                searchIndividualBeneficiary(beneficiaryList, requestInfo, tenantId, errorDetailsMap);
                break;
            default:
                throw new CustomException("INVALID_BENEFICIARY_TYPE", beneficiaryType);
        }
    }

    private void searchHouseholdBeneficiary(
            List<ProjectBeneficiary> beneficiaryList,
            RequestInfo requestInfo,
            String tenantId,
            Map<ProjectBeneficiary, List<Error>> errorDetailsMap
    ) {

        HouseholdSearch householdSearch = null;
        boolean isBeneficiaryId = true;
        Method idMethod = getIdMethod(beneficiaryList, "beneficiaryId");
        Method clientReferenceIdMethod = getIdMethod(beneficiaryList, "beneficiaryClientReferenceId");

        if (beneficiaryList.stream().anyMatch(b -> b.getBeneficiaryId() != null)) {
            householdSearch = HouseholdSearch
                    .builder()
                    .id(getIdList(beneficiaryList, idMethod))
                    .build();
        } else if (beneficiaryList.stream().anyMatch(b -> b.getBeneficiaryClientReferenceId() != null)) {
            isBeneficiaryId = false;
            householdSearch = HouseholdSearch
                    .builder()
                    .clientReferenceId(getIdList(beneficiaryList, clientReferenceIdMethod))
                    .build();
        }

        if (householdSearch == null) {
            beneficiaryList.forEach(b -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(b, error, errorDetailsMap);
            });
            return;
        }

        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(requestInfo)
                .household(householdSearch)
                .build();

        HouseholdBulkResponse response = null;
        try {
            response = serviceRequestClient.fetchResult(
                    new StringBuilder(projectConfiguration.getHouseholdServiceHost()
                            + projectConfiguration.getHouseholdServiceSearchUrl()
                            + "?limit=" + projectConfiguration.getSearchApiLimit()
                            + "&offset=0&tenantId=" + tenantId),
                    householdSearchRequest,
                    HouseholdBulkResponse.class);
            log.info("household search returned with size {}", response.getHouseholds().size());
            if (response.getHouseholds().size() != beneficiaryList.size()) {
                if (isBeneficiaryId) {
                    populateHouseHoldBeneficiaryErrorDetails(beneficiaryList, errorDetailsMap, response,
                            getMethod(GET_ID, Household.class), idMethod);
                } else {
                    populateHouseHoldBeneficiaryErrorDetails(beneficiaryList, errorDetailsMap, response,
                            getMethod(GET_CLIENT_REFERENCE_ID, Household.class), clientReferenceIdMethod);
                }
            }
        } catch (Exception e) {
            log.error("error while fetching households list", e);
            beneficiaryList.forEach(b -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(b, error, errorDetailsMap);
            });
        }
    }

    private void populateHouseHoldBeneficiaryErrorDetails(List<ProjectBeneficiary> beneficiaryList,
                                                          Map<ProjectBeneficiary, List<Error>> errorDetailsMap,
                                                          HouseholdBulkResponse response, Method idMethod, Method beneficiaryMethod) {
        List<String> responseIds = response.getHouseholds().stream()
                .map(household -> (String) ReflectionUtils.invokeMethod(idMethod, household))
                .collect(Collectors.toList());
        beneficiaryList.stream()
                .filter(beneficiary -> !responseIds.contains((String) ReflectionUtils
                        .invokeMethod(beneficiaryMethod, beneficiary)))
                .forEach(projectBeneficiary -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
                });
    }

    private void populateIndividualBeneficiaryErrorDetails(List<ProjectBeneficiary> beneficiaryList,
                                                           Map<ProjectBeneficiary, List<Error>> errorDetailsMap,
                                                           IndividualBulkResponse response, Method idMethod,
                                                           Method beneficiaryMethod) {
        List<String> responseIds = response.getIndividual().stream()
                .map(individual -> (String) ReflectionUtils.invokeMethod(idMethod, individual))
                .collect(Collectors.toList());
        beneficiaryList.stream()
                .filter(beneficiary -> !responseIds.contains((String) ReflectionUtils
                        .invokeMethod(beneficiaryMethod, beneficiary)))
                .forEach(projectBeneficiary -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
                });
    }

    private void searchIndividualBeneficiary(
            List<ProjectBeneficiary> beneficiaryList,
            RequestInfo requestInfo,
            String tenantId,
            Map<ProjectBeneficiary, List<Error>> errorDetailsMap
    ) {
        IndividualSearch individualSearch = null;
        boolean isBeneficiaryId = true;
        Method idMethod = getIdMethod(beneficiaryList, BENEFICIARY_ID);
        Method clientReferenceIdMethod = getIdMethod(beneficiaryList, BENEFICIARY_CLIENT_REFERENCE_ID);

        if (beneficiaryList.stream().anyMatch(b -> b.getBeneficiaryId() != null)) {
            individualSearch = IndividualSearch
                    .builder()
                    .id(getIdList(beneficiaryList, idMethod))
                    .build();
        } else if (beneficiaryList.stream().anyMatch(b -> b.getBeneficiaryClientReferenceId() != null)) {
            individualSearch = IndividualSearch
                    .builder()
                    .clientReferenceId(getIdList(beneficiaryList, clientReferenceIdMethod))
                    .build();
        }

        if (individualSearch == null) {
            beneficiaryList.forEach(b -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(b, error, errorDetailsMap);
            });
            return;
        }

        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .requestInfo(requestInfo)
                .individual(individualSearch)
                .build();

        IndividualBulkResponse response = null;
        try {
            response = serviceRequestClient.fetchResult(
                    new StringBuilder(projectConfiguration.getIndividualServiceHost()
                            + projectConfiguration.getIndividualServiceSearchUrl()
                            + "?limit=" + projectConfiguration.getSearchApiLimit()
                            + "&offset=0&tenantId=" + tenantId),
                    individualSearchRequest,
                    IndividualBulkResponse.class);
            log.info("individuals search returned with size {}", response.getIndividual().size());
            if (response.getIndividual().size() != beneficiaryList.size()) {
                if (isBeneficiaryId) {
                    populateIndividualBeneficiaryErrorDetails(beneficiaryList, errorDetailsMap, response,
                            getMethod(GET_ID, Individual.class), idMethod);
                } else {
                    populateIndividualBeneficiaryErrorDetails(beneficiaryList, errorDetailsMap, response,
                            getMethod(GET_CLIENT_REFERENCE_ID, Individual.class), clientReferenceIdMethod);
                }
            }
        } catch (Exception exception) {
            log.error("error while fetching individuals list", exception);
            beneficiaryList.forEach(b -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(b, error, errorDetailsMap);
            });
        }
    }

    private List<ProjectType> getProjectTypes(String tenantId, RequestInfo requestInfo) {
        JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                projectConfiguration.getMdmsModule());
        return convertToProjectTypeList(response);
    }

    private JsonNode fetchMdmsResponse(RequestInfo requestInfo, String tenantId, String name,
                                       String moduleName) {
        MdmsCriteriaReq serviceRegistry = getMdmsRequest(requestInfo, tenantId, name, moduleName);
        try {
            return mdmsService.fetchConfig(serviceRegistry, JsonNode.class).get(MDMS_RESPONSE);
        } catch (Exception e) {
            throw new CustomException(INTERNAL_SERVER_ERROR, "Error while fetching mdms config");
        }
    }

    private List<ProjectType> convertToProjectTypeList(JsonNode jsonNode) {
        JsonNode projectTypesNode = jsonNode.get(projectConfiguration.getMdmsModule()).withArray(PROJECT_TYPES);
        return new ObjectMapper().convertValue(projectTypesNode, new TypeReference<List<ProjectType>>() {
        });
    }

    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, String masterName,
                                           String moduleName) {
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
}
