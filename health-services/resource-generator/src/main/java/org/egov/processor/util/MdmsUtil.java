package org.egov.processor.util;

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

import static org.egov.processor.config.ServiceConstants.*;

@Slf4j
@Component
public class MdmsUtil {

	private RestTemplate restTemplate;

	private ObjectMapper mapper;

	private Configuration configs;

	private ParsingUtil parsingUtil;

	public MdmsUtil(RestTemplate restTemplate, ObjectMapper mapper, Configuration configs, ParsingUtil parsingUtil) {
		this.restTemplate = restTemplate;
		this.mapper = mapper;
		this.configs = configs;
        this.parsingUtil = parsingUtil;
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
					"no data found for the given tenantid "+tenantId + " for master name "+ServiceConstants.MDMS_MASTER_ADMIN_SCHEMA);
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
		ModuleDetail adminConsoleModuleDetail = getAdminConsoleModuleDetail();
		List<ModuleDetail> moduleDetails = new LinkedList<>();
		moduleDetails.add(adminConsoleModuleDetail);
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

	private ModuleDetail getAdminConsoleModuleDetail() {
		List<MasterDetail> adminSchemaMasterDetails = new ArrayList<>();
		MasterDetail schemaDetails = MasterDetail.builder().name(ServiceConstants.MDMS_MASTER_ADMIN_SCHEMA).build();
		adminSchemaMasterDetails.add(schemaDetails);

		return ModuleDetail.builder().masterDetails(adminSchemaMasterDetails)
				.moduleName(ServiceConstants.MDMS_ADMIN_CONSOLE_MODULE_NAME).build();
	}

	/**
	 * Filters master data based on the provided parameters.
	 * 
	 * @param masterDataJson The JSON string representing the master data.
	 * @param campaignType The campaign type.
	 * @return A map containing filtered properties from the master data.
	 * @throws JsonMappingException if there's an issue mapping JSON.
	 * @throws JsonProcessingException if there's an issue processing JSON.
	 */
	public Map<String, Object> filterMasterData(String masterDataJson, String campaignType) {
		Map<String, Object> properties = new HashMap<>();
		Map<String, Object> masterData = JsonUtils.parseJson(masterDataJson, Map.class);
		Map<String, Object> adminConsoleModule = (Map<String, Object>) masterData.get(ServiceConstants.MDMS_ADMIN_CONSOLE_MODULE_NAME);
		List<Map<String, Object>> adminSchema = (List<Map<String, Object>>) adminConsoleModule
				.get(ServiceConstants.MDMS_MASTER_ADMIN_SCHEMA);
		log.info("masterDataJson ==> " + adminSchema);

		for (Map<String, Object> schema : adminSchema) {
			String campaign = (String) schema.get(ServiceConstants.MDMS_CAMPAIGN_TYPE);

			if (schema.get(ServiceConstants.MDMS_SCHEMA_TITLE).equals(ServiceConstants.FILE_TEMPLATE_IDENTIFIER_BOUNDARY)
					&& campaign.equals(MICROPLAN_PREFIX + campaignType)) {
				Map<String, List<Object>> schemaProperties = (Map<String, List<Object>>) schema.get("properties");

				schemaProperties.forEach((propertyType, propertyList) ->
						propertyList.forEach(property -> {
							String propertyName = (String) parsingUtil.extractFieldsFromJsonObject(property, "name");
							properties.put(propertyName, property);
						})
				);
			}
		}

		return properties;
	}
	
	/**
	 * Parses the provided JSON string containing master data, extracts common constants,
	 * and returns them as a map of name-value pairs.
	 *
	 * @param masterDataJson JSON string representing master data
	 * @return Map<String, Object> containing common constants where keys are constant names and values are constant values
	 */
	public Map<String, Object> filterMasterDataForLocale(String masterDataJson)  {
		Map<String, Object> properties = new HashMap<>();
		Map<String, Object> masterData = JsonUtils.parseJson(masterDataJson, Map.class);
		Map<String, Object> planModule = (Map<String, Object>) masterData.get(ServiceConstants.MDMS_PLAN_MODULE_NAME);
		List<Map<String, Object>> commonConstantsMap = (List<Map<String, Object>>) planModule
				.get(ServiceConstants.MDMS_MASTER_COMMON_CONSTANTS);
		log.info("masterDataJson ==>" + commonConstantsMap);
		for (Map<String, Object> commonConstantMap : commonConstantsMap) {
			properties.put((String) commonConstantMap.get("name"), (String) commonConstantMap.get("value"));
	
		}

		return properties;
	}
	
	/**
	 * Fetches MDMS (Master Data Management System) data for common constants based on the provided request info and tenant ID.
	 * Constructs an MDMS request, sends a POST request to the configured MDMS endpoint, and processes the response.
	 *
	 * @param requestInfo The request information containing context like user details and timestamp.
	 * @param tenantId    The ID of the tenant for which MDMS data is requested.
	 * @return A filtered map of MDMS data for common constants, specific to the locale.
	 * @throws CustomException       If no MDMS data is found for the given tenant ID.
	 * @throws JsonMappingException  If there's an issue mapping JSON response to Java objects.
	 * @throws JsonProcessingException If there's an issue processing JSON during conversion.
	 */
	public  Map<String, Object> fetchMdmsDataForCommonConstants(RequestInfo requestInfo, String tenantId) {
		StringBuilder uri = new StringBuilder();
		uri.append(configs.getMdmsHost()).append(configs.getMdmsEndPoint());
		MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequestForCommonConstants(requestInfo, tenantId);
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
					"no data found for the given tenantid "+tenantId + " for master name "+ServiceConstants.MDMS_MASTER_COMMON_CONSTANTS);
		}
		return filterMasterDataForLocale(result.toString());
	}
	
	/**
	 * Constructs an MDMS (Master Data Management System) request object for fetching common constants.
	 *
	 * @param requestInfo The request information containing context like user details and timestamp.
	 * @param tenantId    The ID of the tenant for which MDMS data is requested.
	 * @return MdmsCriteriaReq object encapsulating the MDMS criteria for fetching common constants.
	 */
	public MdmsCriteriaReq getMdmsRequestForCommonConstants(RequestInfo requestInfo, String tenantId) {

		ModuleDetail moduleDetail = getPlanModulesCommonConstants();
		List<ModuleDetail> moduleDetails = new LinkedList<>();
		moduleDetails.add(moduleDetail);
		MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId).build();
		return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
	}
	
	
	/**
	 * Constructs a ModuleDetail object for fetching common constants from MDMS.
	 *
	 * @return ModuleDetail object representing the module configuration for common constants.
	 */
	private ModuleDetail getPlanModulesCommonConstants() {
		List<MasterDetail> assumptionMasterDetails = new ArrayList<>();
		MasterDetail schemaDetails = MasterDetail.builder().name(ServiceConstants.MDMS_MASTER_COMMON_CONSTANTS).build();
		assumptionMasterDetails.add(schemaDetails);

		return ModuleDetail.builder().masterDetails(assumptionMasterDetails)
				.moduleName(ServiceConstants.MDMS_PLAN_MODULE_NAME).build();
	}

}