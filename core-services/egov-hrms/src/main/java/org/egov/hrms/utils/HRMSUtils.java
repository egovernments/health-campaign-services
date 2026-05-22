package org.egov.hrms.utils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import org.egov.common.contract.request.RequestInfo;
import org.egov.hrms.service.MDMSService;
import org.egov.hrms.web.contract.EmployeeSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HRMSUtils {
	
	@Value("${egov.hrms.default.pwd.length}")
	private Integer pwdLength;

	@Value("${egov.pwd.allowed.special.characters}")
	private String allowedPasswordSpecialCharacters;

	@Value("${egov.hrms.mobile.number.default.min}")
	private Long defaultMobileNumberMin;

	@Value("${egov.hrms.mobile.number.default.max}")
	private Long defaultMobileNumberMax;

	@Autowired
	private MDMSService mdmsService;
	
	/**
	 * Generates random password for the user to login. Process:
	 * 1. Takes a list of parameters for password
	 * 2. Applies a random select logic and generates a password of constant length.
	 * 3. The length of the password is configurable.
	 * 
	 * @param params
	 * @return
	 */
	public String generatePassword(List<String> params) {
		StringBuilder password = new StringBuilder();
		SecureRandom random = new SecureRandom();
		params.add(allowedPasswordSpecialCharacters);
		try {
			for(int i = 0; i < params.size(); i++) {
				String param = params.get(i);
				String val;
				if(param.length() == 1)
					val = param;
				else
					val = param.split("")[random.nextInt(param.length() - 1)];
				if(val.equals(".") || val.equals("-") || val.equals(" "))
					password.append("x");
				else
					password.append(val);
				if(password.length() == pwdLength)
					break;
				else {
					if(i == params.size() - 1)
						i = 0;
				}
			}
		}catch(Exception e) {
			password.append("123456");
		}

		return password.toString().replaceAll("\\s+", "");
	}

	public boolean isAssignmentSearchReqd(EmployeeSearchCriteria criteria) {
		return (! CollectionUtils.isEmpty(criteria.getPositions()) || null != criteria.getAsOnDate()
				|| !CollectionUtils.isEmpty(criteria.getDepartments()) || !CollectionUtils.isEmpty(criteria.getDesignations()));
	}

	public String generateMobileNumber(RequestInfo requestInfo, String tenantId) {
		Random random = new Random();

		// Fetch mobile pattern from MDMS
		String pattern = mdmsService.fetchMobileNumberPattern(requestInfo, tenantId);

		try {
			String generatedNumber = generateNumberFromPattern(pattern, random);
			if (generatedNumber != null) {
				return generatedNumber;
			}
		} catch (Exception e) {
			log.warn("Failed to parse mobile pattern: " + pattern + ". Using default generation.", e);
		}

		// Default generation if pattern is not available or parsing fails
		long mobileNumber = Math.abs(random.nextLong() % (defaultMobileNumberMax - defaultMobileNumberMin + 1)) + defaultMobileNumberMin;
		return Long.toString(mobileNumber);
	}

	/**
	 * Generates a mobile number based on the provided pattern.
	 *
	 * @param pattern The pattern to use for generating the mobile number
	 * @param random  The Random instance to use for generation
	 * @return The generated mobile number string, or null if pattern is invalid
	 */
	private String generateNumberFromPattern(String pattern, Random random) {
		// If pattern exists, parse it and generate number based on pattern
		if (pattern != null && pattern.matches("\\^\\[\\d+-\\d+\\]\\[0-9\\]\\{\\d+\\}\\$")) {
			// Extract first digit range (e.g., from "^[6-9][0-9]{9}$" extract 6-9)
			int startIdx = pattern.indexOf('[');
			int endIdx = pattern.indexOf(']');
			String firstDigitRange = pattern.substring(startIdx + 1, endIdx);
			String[] range = firstDigitRange.split("-");
			int minFirstDigit = Integer.parseInt(range[0]);
			int maxFirstDigit = Integer.parseInt(range[1]);

			// Extract remaining digits count (e.g., from {9} extract 9)
			int braceStart = pattern.indexOf('{');
			int braceEnd = pattern.indexOf('}');
			String digitCountStr = pattern.substring(braceStart + 1, braceEnd);
			int remainingDigits = Integer.parseInt(digitCountStr);

			// Generate first digit
			int firstDigit = minFirstDigit + random.nextInt(maxFirstDigit - minFirstDigit + 1);

			// Generate remaining digits
			StringBuilder mobileNumber = new StringBuilder();
			mobileNumber.append(firstDigit);

			for (int i = 0; i < remainingDigits; i++) {
				mobileNumber.append(random.nextInt(10));
			}

			return mobileNumber.toString();
		}
		return null;
	}
}
