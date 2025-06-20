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

		Object additionalDetails = project.getAdditionalDetails();

		if (additionalDetails instanceof Map) {
			Map<String, Object> additionalDetailsMap = (Map<String, Object>) additionalDetails;
			Object projectType = additionalDetailsMap.get("projectType");
			return (LinkedHashMap<String, Object>) projectType;
		} else {
			throw new CustomException("JSONPATH_ERROR", "Invalid project type");
		}
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
