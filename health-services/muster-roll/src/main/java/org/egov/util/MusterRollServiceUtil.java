package org.egov.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.models.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Skill;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.*;
import org.egov.works.services.common.models.bankaccounts.BankAccount;
import org.egov.works.services.common.models.bankaccounts.BankAccountDetails;
import org.egov.works.services.common.models.musterroll.Status;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.egov.util.MusterRollServiceConstants.*;

@Component
@Slf4j
public class MusterRollServiceUtil {

	private final ObjectMapper mapper;

	private final RestTemplate restTemplate;

	private final MusterRollServiceConfiguration config;
	private static final String SKILL_CODE = "skillCode";

	@Autowired
	public MusterRollServiceUtil(ObjectMapper mapper, RestTemplate restTemplate, MusterRollServiceConfiguration config) {
		this.mapper = mapper;
		this.restTemplate = restTemplate;
		this.config = config;
	}

	/**
	 * Method to return auditDetails for create/update flows
	 *
	 * @param by
	 * @param isCreate
	 * @return AuditDetails
	 */
	public AuditDetails getAuditDetails(String by, MusterRoll musterRoll, Boolean isCreate) {
		Long time = System.currentTimeMillis();
		if (Boolean.TRUE.equals(isCreate))
			return AuditDetails.builder().createdBy(by).lastModifiedBy(by).createdTime(time).lastModifiedTime(time)
					.build();
		else
			return AuditDetails.builder().createdBy(musterRoll.getAuditDetails().getCreatedBy()).lastModifiedBy(by)
					.createdTime(musterRoll.getAuditDetails().getCreatedTime()).lastModifiedTime(time).build();
	}

	/**
	 * Fetch the individual skill level from MDMS
	 * 
	 * @param mdmsData
	 * @param individualEntry
	 * @param skillCode
	 *
	 */
	public void populateAdditionalDetails(Object mdmsData, IndividualEntry individualEntry, String skillCode,
			Individual matchedIndividual, BankAccount bankAccount, boolean isCreate) {
		final String jsonPathForWorksMuster = "$.MdmsRes." + "WORKS-SOR" + "."
				+ "SOR" + ".*";
		List<LinkedHashMap<String, String>> musterRes = null;

		try {
			musterRes = JsonPath.read(mdmsData, jsonPathForWorksMuster);

		} catch (Exception e) {
			log.error(e.getMessage());
			throw new CustomException("MusterRollServiceUtil::populateAdditionalDetails::JSONPATH_ERROR",
					"Failed to parse mdms response");
		}

		String skillValue = "";
		if (skillCode != null && !CollectionUtils.isEmpty(musterRes)) {
			for (LinkedHashMap<String, String> codeValueMap : musterRes) {
				if (codeValueMap.get("id").equalsIgnoreCase(skillCode)) {
					skillValue = codeValueMap.get("name");
					break;
				}
			}
		}

		// populate individual details for estimate and create
		log.info("MusterRollServiceUtil::populateAdditionalDetails::start");
		JSONObject additionalDetails = new JSONObject();

		additionalDetails.put("userId", matchedIndividual.getIndividualId());
		additionalDetails.put("userName", matchedIndividual.getName().getGivenName());
		additionalDetails.put("fatherName", matchedIndividual.getFatherName());
		additionalDetails.put("mobileNo", matchedIndividual.getMobileNumber());
		additionalDetails.put("gender",matchedIndividual.getGender());
		Identifier aadhaar = matchedIndividual.getIdentifiers().stream()
				.filter(identifier -> identifier.getIdentifierType().contains("AADHAAR")).findFirst().orElse(null);
		if (aadhaar != null) {
			additionalDetails.put("aadharNumber", aadhaar.getIdentifierId());
		}

		// populate individual's skill details in create and update (user selected skill
		// will be set in additionalDetails)
		if (isCreate) {
			additionalDetails.put(SKILL_CODE, skillCode);
			additionalDetails.put("skillValue", skillValue);
		}

		// populate list of skills of the individual in estimate additionalDetails
		populateSkillsInEstimateDetails(isCreate,matchedIndividual,additionalDetails);

		if (bankAccount != null) {
			List<BankAccountDetails> bankAccountDetails = bankAccount.getBankAccountDetails();
			if (!CollectionUtils.isEmpty(bankAccountDetails)) {
				String accountNumber = bankAccountDetails.get(0).getAccountNumber();
				String ifscCode = bankAccountDetails.get(0).getBankBranchIdentifier().getCode();
				String accountHolderName = bankAccountDetails.get(0).getAccountHolderName();
				String accountType = bankAccountDetails.get(0).getAccountType();
				additionalDetails.put("bankDetails", accountNumber + "-" + ifscCode);
				additionalDetails.put("accountHolderName", accountHolderName);
				additionalDetails.put("accountType", accountType);
			}
		}

		try {
			individualEntry.setAdditionalDetails(mapper.readValue(additionalDetails.toString(), Object.class));
		} catch (IOException e) {
			throw new CustomException("MusterRollServiceUtil::populateAdditionalDetails::PARSING ERROR",
					"Failed to set additionalDetail object");
		}

	}
	private void populateSkillsInEstimateDetails(boolean isCreate,Individual matchedIndividual,JSONObject additionalDetails){
		if (!isCreate && !CollectionUtils.isEmpty(matchedIndividual.getSkills())) {
			List<String> skillList = new ArrayList<>();
			for (Skill skill : matchedIndividual.getSkills()) {
				skillList.add(skill.getLevel());
			}
			additionalDetails.put(SKILL_CODE, skillList);
		}
	}

	public void updateAdditionalDetails(Object mdmsData, IndividualEntry individualEntry, String skillCode) {
		final String jsonPathForWorksMuster = "$.MdmsRes." + "WORKS-SOR" + "."
				+ "SOR" + ".*";
		List<LinkedHashMap<String, String>> musterRes = null;

		try {
			musterRes = JsonPath.read(mdmsData, jsonPathForWorksMuster);

		} catch (Exception e) {
			log.error(e.getMessage());
			throw new CustomException("MusterRollServiceUtil::updateAdditionalDetails::JSONPATH_ERROR",
					"Failed to parse mdms response");
		}

		String skillValue = "";
		if (skillCode != null && !CollectionUtils.isEmpty(musterRes)) {
			for (LinkedHashMap<String, String> codeValueMap : musterRes) {
				if (codeValueMap.get("id").equalsIgnoreCase(skillCode)) {
					skillValue = codeValueMap.get("name");
					break;
				}
			}
		}

		try {
			JsonNode node = mapper.readTree(mapper.writeValueAsString(individualEntry.getAdditionalDetails()));
			((ObjectNode) node).put(SKILL_CODE, skillCode);
			((ObjectNode) node).put("skillValue", skillValue);
			individualEntry.setAdditionalDetails(mapper.readValue(node.toString(), Object.class));

		} catch (IOException e) {
			log.info(
					"MusterRollServiceUtil::updateAdditionalDetails::Failed to parse additionalDetail object from request"
							+ e);
			throw new CustomException("PARSING ERROR",
					"Failed to parse additionalDetail object from request on update");
		}

	}

	/**
	 * Sets the attendanceLogId in additionalDetails of the attendanceEntry
	 * 
	 * @param attendanceEntry
	 * @param entryAttendanceLogId
	 * @param exitAttendanceLogId
	 */
	public void populateAdditionalDetailsAttendanceEntry(AttendanceEntry attendanceEntry, String entryAttendanceLogId,
			String exitAttendanceLogId) {
		JSONObject additionalDetails = new JSONObject();
		additionalDetails.put("entryAttendanceLogId", entryAttendanceLogId);
		additionalDetails.put("exitAttendanceLogId", exitAttendanceLogId);
		try {
			attendanceEntry.setAdditionalDetails(mapper.readValue(additionalDetails.toString(), Object.class));
		} catch (IOException e) {
			throw new CustomException("MusterRollServiceUtil::populateAdditionalDetailsAttendanceEntry::PARSING ERROR",
					"Failed to set additionalDetail object");
		}
	}

	/**
	 * Checks if the search is based only on tenantId
	 * 
	 * @param searchCriteria
	 * @return
	 */
	public boolean isTenantBasedSearch(MusterRollSearchCriteria searchCriteria) {
        return (searchCriteria.getIds() == null || searchCriteria.getIds().isEmpty())
                && StringUtils.isBlank(searchCriteria.getMusterRollNumber())
                && StringUtils.isBlank(searchCriteria.getRegisterId()) && searchCriteria.getFromDate() == null
                && searchCriteria.getToDate() == null && searchCriteria.getStatus() == null
                && StringUtils.isBlank(searchCriteria.getMusterRollStatus())
                && StringUtils.isNotBlank(searchCriteria.getTenantId());
    }

	public AttendanceRegisterResponse fetchAttendanceRegister(MusterRoll musterRoll, RequestInfo requestInfo) {
		log.info("MusterRollValidator::Fetching attendance register with tenantId::" + musterRoll.getTenantId()
				+ " and register ID: " + musterRoll.getRegisterId());
		String id = requestInfo.getUserInfo().getUuid();

		StringBuilder uri = new StringBuilder();
		uri.append(config.getAttendanceLogHost()).append(config.getAttendanceRegisterEndpoint());
		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri.toString())
				.queryParam("tenantId", musterRoll.getTenantId()).queryParam("ids", musterRoll.getRegisterId())
				.queryParam("status", Status.ACTIVE);
		RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();

		AttendanceRegisterResponse attendanceRegisterResponse = null;

		try {
			attendanceRegisterResponse = restTemplate.postForObject(uriBuilder.toUriString(), requestInfoWrapper,
					AttendanceRegisterResponse.class);
		} catch (HttpClientErrorException | HttpServerErrorException httpClientOrServerExc) {
			log.error("MusterRollValidator::Error thrown from attendance register service::"
					+ httpClientOrServerExc.getStatusCode());
			throw new CustomException("ATTENDANCE_REGISTER_SERVICE_EXCEPTION",
					"Error thrown from attendance register service::" + httpClientOrServerExc.getStatusCode());
		}

		if (attendanceRegisterResponse == null
				|| CollectionUtils.isEmpty(attendanceRegisterResponse.getAttendanceRegister())) {
			log.error("MusterRollValidator::User with id::" + id + " is not enrolled in the attendance register::"
					+ musterRoll.getRegisterId());
			throw new CustomException("ACCESS_EXCEPTION",
					"User is not enrolled in the attendance register and not authorized to fetch it");
		}
		return attendanceRegisterResponse;
	}
}
