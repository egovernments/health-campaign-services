package org.egov.referralmanagement.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectResponse;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.egov.referralmanagement.Constants.HCM_MASTER_PROJECTTYPE;
import static org.egov.referralmanagement.Constants.HCM_MDMS_PROJECTTYPE_RES_PATH;
import static org.egov.referralmanagement.Constants.HCM_MDMS_PROJECT_MODULE_NAME;
import static org.egov.referralmanagement.Constants.HCM_PT_FILTER_CODE;

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
				.filter(String.format(HCM_PT_FILTER_CODE, projectCode)) // projectCode FIXME
				.build();

		ModuleDetail moduleDetail = ModuleDetail.builder()
				.masterDetails(Arrays.asList(masterDetail))
				.moduleName(HCM_MDMS_PROJECT_MODULE_NAME)
				.build();

		MdmsCriteria mdmsCriteria = MdmsCriteria.builder()
				.moduleDetails(Arrays.asList(moduleDetail))
				.tenantId(downsyncCriteria.getTenantId().split("//.")[0])
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

		return (LinkedHashMap<String, Object>)projectTypeRes.get(1); //FIXME hardcoded because of duplicates in MDMS impel DEMO

	}

	public List<Integer> getCycleIndicesForTask(LinkedHashMap<String, Object> projectType) {

		@SuppressWarnings("unchecked")
		List<LinkedHashMap<String, Object>> cycleList = (List<LinkedHashMap<String, Object>>) projectType.get("cycles");
		Map<Integer, LinkedHashMap<String, Object>> map = cycleList.stream()
				.collect(Collectors.toMap(entry -> (Integer) entry.get("id"), Function.identity()));

		List<Integer> cycleIndicesToBeDownloaded = new ArrayList<>();

		for (Integer cycleIndex : map.keySet()) {

			LinkedHashMap<String, Object> currentCycle = map.get(cycleIndex);
			Long startDate = (Long) currentCycle.get("startDate");
			Long endDate = (Long) currentCycle.get("endDate");

			Long currentTime = System.currentTimeMillis();

			/*
			 * If the current time falls in-between the current cycle date
			 *
			 * then the tasks to be download is of the current cycle and the previous cycle
			 */
			if (currentTime >= startDate && currentTime <= endDate) {

				if (cycleIndex < 3)
					break;
				else {
					cycleIndicesToBeDownloaded.add(cycleIndex - 1);
					cycleIndicesToBeDownloaded.add(cycleIndex);
				}
			}

			/*
			 * If the current time is less than the start date of current cycle
			 *
			 * then the cycle to be executed will be the current cycle
			 *
			 * So the Task(delivery) data to be download is of last cycle
			 */
			else if (currentTime < startDate) {

				if (cycleIndex < 3)
					break;
				else {
					cycleIndicesToBeDownloaded.add(cycleIndex - 1);
				}
			}

			/*
			 * If the current time is greater than the end date of last cycle in the project
			 * then the delivery download will be of the last cycle itself not the
			 * previous cycle
			 */
			else if (cycleIndex.equals(map.size())) {
				cycleIndicesToBeDownloaded.add(cycleIndex);
			}

		}

		return cycleIndicesToBeDownloaded;
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
