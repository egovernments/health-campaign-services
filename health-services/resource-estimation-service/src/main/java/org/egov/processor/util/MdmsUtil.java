package org.egov.processor.util;

import static org.egov.processor.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_MDMS;
import static org.egov.processor.config.ServiceConstants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE;
import static org.egov.processor.config.ServiceConstants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.MdmsResponse;
import org.egov.mdms.model.ModuleDetail;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.web.models.File;
import org.egov.tracer.model.CustomException;
import org.flywaydb.core.internal.util.JsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MdmsUtil {

	private RestTemplate restTemplate;

	private ObjectMapper mapper;

	private Configuration configs;

	public MdmsUtil(RestTemplate restTemplate, ObjectMapper mapper, Configuration configs) {
		this.restTemplate = restTemplate;
		this.mapper = mapper;
		this.configs = configs;
	}

	/**
	 * Fetches MDMS (Municipal Data Management System) data using the provided
	 * request information and tenant ID.
	 * 
	 * @param requestInfo The request information.
	 * @param tenantId    The ID of the tenant for which MDMS data is to be fetched.
	 * @return The MDMS response data.
	 * @throws CustomException if there's an error while fetching MDMS data or if no
	 *                         data is found for the given tenant.
	 */
	public Object fetchMdmsData(RequestInfo requestInfo, String tenantId) {
		StringBuilder uri = new StringBuilder();
		uri.append(configs.getMdmsHost()).append(configs.getMdmsEndPoint());
		MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId);
		Object response = new HashMap<>();
		MdmsResponse mdmsResponse = new MdmsResponse();
		try {
			response = restTemplate.postForObject(uri.toString(), mdmsCriteriaReq, Map.class);
			mdmsResponse = mapper.convertValue(response, MdmsResponse.class);
		} catch (Exception e) {
			log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
		}

		Object result = mdmsResponse.getMdmsRes();
		if (result == null || ObjectUtils.isEmpty(result)) {
			log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE + " - " + tenantId);
			throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE,
					NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE);
		}
		return result;
	}

	/**
	 * Constructs an MDMS request object based on the provided request information and tenant ID.
	 * 
	 * @param requestInfo The request information.
	 * @param tenantId The ID of the tenant for which MDMS data is to be fetched.
	 * @return The MDMS criteria request object.
	 */
	public MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId) {

		ModuleDetail moduleDetail = getPlanModuleDetail();
		List<ModuleDetail> moduleDetails = new LinkedList<>();
		moduleDetails.add(moduleDetail);
		MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId).build();
		return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
	}

	/**
	 * Retrieves the module details for the plan module.
	 * 
	 * @return ModuleDetail object containing master details for the plan module.
	 */
	private ModuleDetail getPlanModuleDetail() {
		List<MasterDetail> assumptionMasterDetails = new ArrayList<>();
		MasterDetail schemaDetails = MasterDetail.builder().name(ServiceConstants.MDMS_MASTER_SCHEMAS).build();
		assumptionMasterDetails.add(schemaDetails);

		return ModuleDetail.builder().masterDetails(assumptionMasterDetails)
				.moduleName(ServiceConstants.MDMS_PLAN_MODULE_NAME).build();
	}

	/**
	 * Filters master data based on the provided parameters.
	 * 
	 * @param masterDataJson The JSON string representing the master data.
	 * @param fileType The type of input file.
	 * @param templateIdentifier The template identifier.
	 * @param campaignType The campaign type.
	 * @return A map containing filtered properties from the master data.
	 * @throws JsonMappingException if there's an issue mapping JSON.
	 * @throws JsonProcessingException if there's an issue processing JSON.
	 */
	public Map<String, Object> filterMasterData(String masterDataJson, File.InputFileTypeEnum fileType,
			String templateIdentifier, String campaignType) throws JsonMappingException, JsonProcessingException {
		Map<String, Object> properties = new HashMap<>();
		Map<String, Object> masterData = JsonUtils.parseJson(masterDataJson, Map.class);
		Map<String, Object> planModule = (Map<String, Object>) masterData.get(ServiceConstants.MDMS_PLAN_MODULE_NAME);
		List<Map<String, Object>> schemas = (List<Map<String, Object>>) planModule
				.get(ServiceConstants.MDMS_MASTER_SCHEMAS);
		log.info("masterDataJson ==>" + schemas);
		for (Map<String, Object> schema : schemas) {
			String type = (String) schema.get(ServiceConstants.MDMS_SCHEMA_TYPE);
			String campaign = (String) schema.get(ServiceConstants.MDMS_CAMPAIGN_TYPE);
			// String fileT = InputFileTypeEnum.valueOf(type);
			if (schema.get(ServiceConstants.MDMS_SCHEMA_SECTION).equals(ServiceConstants.FILE_TEMPLATE_IDENTIFIER)
					&& campaign.equals(campaignType) && type.equals(fileType.toString())) {
				Map<String, Object> schemaProperties = (Map<String, Object>) schema.get("schema");
				properties = (Map<String, Object>) schemaProperties.get("Properties");
			}
		}

		return properties;
	}

}