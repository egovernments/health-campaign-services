package org.egov.referralmanagement.service;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import com.jayway.jsonpath.JsonPath;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectResponse;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;

import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static org.egov.referralmanagement.Constants.HCM_MASTER_PROJECTTYPE;
import static org.egov.referralmanagement.Constants.HCM_MDMS_PROJECTTYPE_RES_PATH;
import static org.egov.referralmanagement.Constants.HCM_MDMS_PROJECT_MODULE_NAME;
import static org.egov.referralmanagement.Constants.HCM_PROJECT_TYPE_FILTER_CODE;

@Slf4j
@Service
public class MasterDataService {

	private ServiceRequestClient restClient;

	private ReferralManagementConfiguration configs;

	@Autowired
	public MasterDataService(ServiceRequestClient serviceRequestClient,
			ReferralManagementConfiguration referralManagementConfiguration) {

		this.restClient = serviceRequestClient;
		this.configs = referralManagementConfiguration;

	}


	@SuppressWarnings("unchecked")
	public LinkedHashMap<String, Object> getProjectType(DownsyncRequest downsyncRequest) {

		DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();
		RequestInfo info = downsyncRequest.getRequestInfo();
		String projectId = downsyncCriteria.getProjectId();


		Project project = getProject(downsyncCriteria, info, projectId);

		String projectCode = project.getProjectType(); // FIXME
		return fetchProjectType(downsyncCriteria.getTenantId(), projectCode, info);
	}

	/**
	 * Campaign beneficiary type (HOUSEHOLD | INDIVIDUAL) for an MDMS project-type code.
	 * Used by the downsync file generator, which reads the code straight from the
	 * project row and therefore needs no project-service round-trip.
	 */
	public String getBeneficiaryType(String tenantId, String projectTypeCode, RequestInfo info) {
		if (!StringUtils.hasText(projectTypeCode)) {
			throw new CustomException("PROJECT_TYPE_MISSING",
					"Project row has no projectType code; cannot resolve beneficiaryType from MDMS");
		}
		LinkedHashMap<String, Object> projectType = fetchProjectType(tenantId, projectTypeCode, info);
		Object beneficiaryType = projectType.get("beneficiaryType");
		if (beneficiaryType == null || !StringUtils.hasText(beneficiaryType.toString())) {
			throw new CustomException("BENEFICIARY_TYPE_MISSING",
					"MDMS projectType '" + projectTypeCode + "' has no beneficiaryType");
		}
		return beneficiaryType.toString().trim().toUpperCase();
	}

	@SuppressWarnings("unchecked")
	private LinkedHashMap<String, Object> fetchProjectType(String tenantId, String projectCode, RequestInfo info) {

		/*
		 * TODO FIXME code should get upgraded when next version of project is created with execution plan (project type master) in the additional details
		 */
		StringBuilder mdmsUrl = new StringBuilder(configs.getMdmsHost())
				.append(configs.getMdmsSearchUrl());

		/*
		 * Assumption is that the project code is always unique
		 */
		MasterDetail masterDetail = MasterDetail.builder()
				.name(HCM_MASTER_PROJECTTYPE)
				.filter(String.format(HCM_PROJECT_TYPE_FILTER_CODE, projectCode)) // projectCode FIXME
				.build();

		ModuleDetail moduleDetail = ModuleDetail.builder()
				.masterDetails(Arrays.asList(masterDetail))
				.moduleName(HCM_MDMS_PROJECT_MODULE_NAME)
				.build();

		MdmsCriteria mdmsCriteria = MdmsCriteria.builder()
				.moduleDetails(Arrays.asList(moduleDetail))
				.tenantId(tenantId.split("\\.")[0])   // state-level tenant for MDMS
				.build();

		MdmsCriteriaReq mdmsCriteriaReq = MdmsCriteriaReq.builder()
				.mdmsCriteria(mdmsCriteria)
				.requestInfo(info)
				.build();

		Map<String, Object> mdmsRes = restClient.fetchResult(mdmsUrl, mdmsCriteriaReq, HashMap.class);
		List<Object> projectTypeRes = null;
		try {
			projectTypeRes = JsonPath.read(mdmsRes, HCM_MDMS_PROJECTTYPE_RES_PATH);
		} catch (Exception e) {
			log.error(e.getMessage());
			throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
		}

		if (projectTypeRes == null || projectTypeRes.isEmpty()) {
			throw new CustomException("PROJECT_TYPE_NOT_FOUND",
					"No MDMS projectType found with code '" + projectCode + "' for tenant " + tenantId);
		}
		return (LinkedHashMap<String, Object>) projectTypeRes.get(0);

	}


	private Project getProject(DownsyncCriteria downsyncCriteria, RequestInfo info, String projectId) {

		StringBuilder url = new StringBuilder(configs.getProjectHost())
				.append(configs.getProjectSearchUrl())
				.append("?offset=0")
				.append("&limit=100")
				.append("&tenantId=").append(downsyncCriteria.getTenantId());

		Project project = Project.builder()
				.id(projectId)
				.tenantId(downsyncCriteria.getTenantId())
				.build();

		ProjectRequest projectRequest = ProjectRequest.builder()
				.projects(Arrays.asList(project))
				.requestInfo(info)
				.build();

		ProjectResponse res = restClient.fetchResult(url, projectRequest, ProjectResponse.class);
		return res.getProject().get(0);
	}
}
