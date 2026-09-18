/*
 * eGov suite of products aim to improve the internal efficiency,transparency,
 * accountability and the service delivery of the government  organizations.
 *
 *  Copyright (C) 2016  eGovernments Foundation
 *
 *  The updated version of eGov suite of products as by eGovernments Foundation
 *  is available at http://www.egovernments.org
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see http://www.gnu.org/licenses/ or
 *  http://www.gnu.org/licenses/gpl.html .
 *
 *  In addition to the terms of the GPL license to be adhered to in using this
 *  program, the following additional terms are to be complied with:
 *
 *      1) All versions of this program, verbatim or modified must carry this
 *         Legal Notice.
 *
 *      2) Any misrepresentation of the origin of the material is prohibited. It
 *         is required that all modified versions of this material be marked in
 *         reasonable ways as different from the original version.
 *
 *      3) This license does not grant any rights to any user of the program
 *         with regards to rights under trademark law for use of the trade names
 *         or trademarks of eGovernments Foundation.
 *
 *  In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 */

package org.egov.hrms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.hrms.config.PropertiesManager;
import org.egov.hrms.model.Employee;
import org.egov.hrms.repository.RestCallRepository;
import org.egov.hrms.utils.HRMSConstants;
import org.egov.hrms.web.contract.UserRequest;
import org.egov.hrms.web.contract.UserResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PostConstruct;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.egov.hrms.utils.HRMSConstants.*;

@Slf4j
@Setter
@Getter
public class DefaultUserService implements UserService {

	@Autowired
	private PropertiesManager propertiesManager;
	
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RestCallRepository restCallRepository;

	@Autowired
	private MultiStateInstanceUtil centralInstanceUtil;

	@Value("${egov.user.create.endpoint}")
	private String userCreateEndpoint;

	@Value("${egov.user.search.endpoint}")
	private String userSearchEndpoint;

	@Value("${egov.user.update.endpoint}")
	private String userUpdateEndpoint;

	private String internalMicroserviceRoleUuid = null;

	@PostConstruct
	void initalizeSystemuser(){
		log.info("initialising system user");
		RequestInfo requestInfo = new RequestInfo();
		StringBuilder uri = new StringBuilder();
		uri.append(propertiesManager.getUserHost()).append(propertiesManager.getUserSearchEndpoint()); // URL for user search call
		Map<String, Object> userSearchRequest = new HashMap<>();
		userSearchRequest.put("RequestInfo", requestInfo);
		userSearchRequest.put("tenantId", propertiesManager.getStateLevelTenantId());
		userSearchRequest.put("roleCodes", Collections.singletonList(INTERNALMICROSERVICEROLE_CODE));
		try {
			LinkedHashMap<String, Object> responseMap = (LinkedHashMap<String, Object>) restCallRepository.fetchResult(uri, userSearchRequest);
			List<LinkedHashMap<String, Object>> users = (List<LinkedHashMap<String, Object>>) responseMap.get("user");
			if(users.size()==0)
				createInternalMicroserviceUser(requestInfo);
			internalMicroserviceRoleUuid = (String) users.get(0).get("uuid");
		}catch (Exception e) {
			throw new CustomException("HRMS_USER_SEARCH_ERROR",
					String.format("Failed to fetch user from egov-user (roleCode=%s, tenantId=%s) at %s. "
									+ "downstreamExceptionClass=%s downstreamMessage=%s",
							INTERNALMICROSERVICEROLE_CODE,
							propertiesManager.getStateLevelTenantId(),
							uri.toString(),
							e.getClass().getSimpleName(),
							e.getMessage() != null ? e.getMessage() : "(no message)"));
		}

	}

	private void createInternalMicroserviceUser(RequestInfo requestInfo){
		Map<String, Object> userCreateRequest = new HashMap<>();
		//Creating role with INTERNAL_MICROSERVICE_ROLE
		Role role = Role.builder()
				.name(INTERNALMICROSERVICEROLE_NAME).code(INTERNALMICROSERVICEROLE_CODE)
				.tenantId(propertiesManager.getStateLevelTenantId()).build();
		User user = User.builder().userName(INTERNALMICROSERVICEUSER_USERNAME)
				.name(INTERNALMICROSERVICEUSER_NAME).mobileNumber(INTERNALMICROSERVICEUSER_MOBILENO)
				.type(INTERNALMICROSERVICEUSER_TYPE).tenantId(propertiesManager.getStateLevelTenantId())
				.roles(Collections.singletonList(role)).id(0L).build();

		userCreateRequest.put("RequestInfo", requestInfo);
		userCreateRequest.put("user", user);

		StringBuilder uri = new StringBuilder();
		uri.append(propertiesManager.getUserHost()).append(propertiesManager.getUserCreateEndpoint()); // URL for user create call

		try {
			LinkedHashMap<String, Object> responseMap = (LinkedHashMap<String, Object>) restCallRepository.fetchResult(uri, userCreateRequest);
			List<LinkedHashMap<String, Object>> users = (List<LinkedHashMap<String, Object>>) responseMap.get("user");
			internalMicroserviceRoleUuid = (String) users.get(0).get("uuid");
		}catch (Exception e) {
			throw new CustomException("HRMS_USER_CREATE_ERROR",
					String.format("Failed to create user in egov-user (userName=%s, roleCode=%s, tenantId=%s) at %s. "
									+ "downstreamExceptionClass=%s downstreamMessage=%s",
							INTERNALMICROSERVICEUSER_USERNAME,
							INTERNALMICROSERVICEROLE_CODE,
							propertiesManager.getStateLevelTenantId(),
							uri.toString(),
							e.getClass().getSimpleName(),
							e.getMessage() != null ? e.getMessage() : "(no message)"));
		}
	}
	
	@Override
	public UserResponse createUser(UserRequest userRequest) {
		StringBuilder uri = new StringBuilder();
		uri.append(propertiesManager.getUserHost()).append(propertiesManager.getUserCreateEndpoint());
		UserResponse userResponse = null;
		try {
			userResponse = userCall(userRequest,uri);
		}catch(Exception e) {
			log.error("User created failed: ",e);
		}

		return userResponse;
	}
	
	@Override
	public UserResponse updateUser(UserRequest userRequest) {
		StringBuilder uri = new StringBuilder();
		uri.append(propertiesManager.getUserHost()).append(propertiesManager.getUserUpdateEndpoint());
		UserResponse userResponse = null;
		try {
			userResponse = userCall(userRequest,uri);
		}catch(Exception e) {
			log.error("User created failed: ",e);
		}

		return userResponse;
	}
	
	@Override
	public UserResponse getUser(RequestInfo requestInfo, Map<String, Object> userSearchCriteria) {
		StringBuilder uri = new StringBuilder();
		Map<String, Object> userSearchReq = new HashMap<>();
		User userInfoCopy = requestInfo.getUserInfo();

		if(propertiesManager.getIsDecryptionEnable()){
			User enrichedUserInfo = getEncrichedandCopiedUserInfo(String.valueOf(userSearchCriteria.get("tenantId")));
			requestInfo.setUserInfo(enrichedUserInfo);
		}

		userSearchReq.put("RequestInfo", requestInfo);
		userSearchReq.put(HRMSConstants.HRMS_USER_SERACH_CRITERIA_USERTYPE_CODE,HRMSConstants.HRMS_USER_SERACH_CRITERIA_USERTYPE);
		for( String key: userSearchCriteria.keySet())
			userSearchReq.put(key, userSearchCriteria.get(key));
		uri.append(propertiesManager.getUserHost()).append(propertiesManager.getUserSearchEndpoint());
		UserResponse userResponse = new UserResponse();
		try {
			userResponse = userCall(userSearchReq,uri);
		}catch(Exception e) {
			log.error("User search failed: ",e);
		}
		if(propertiesManager.getIsDecryptionEnable())
			requestInfo.setUserInfo(userInfoCopy);

		return userResponse;
	}

	@Override
	public UserResponse searchByUsernames(RequestInfo requestInfo, List<String> usernames, String tenantId) {
		UserResponse aggregated = new UserResponse();
		List<org.egov.hrms.web.contract.User> matchedUsers = new ArrayList<>();
		aggregated.setUser(matchedUsers);
		aggregated.setTotalCount(0L);
		if (usernames == null || usernames.isEmpty())
			return aggregated;

		// De-duplicate to avoid redundant lookups while preserving order
		List<String> distinctUsernames = new ArrayList<>(new LinkedHashSet<>(usernames));

		// egov-user caps external search results at a default page size, so chunk the usernames:
		// each batch stays within that cap and no existing user is missed. userType (EMPLOYEE) is
		// applied by getUser(...) itself.
		int batchSize = HRMSConstants.HRMS_USER_BULK_SEARCH_BATCH_SIZE;
		for (int start = 0; start < distinctUsernames.size(); start += batchSize) {
			List<String> batch = new ArrayList<>(
					distinctUsernames.subList(start, Math.min(start + batchSize, distinctUsernames.size())));
			Map<String, Object> userSearchCriteria = new HashMap<>();
			userSearchCriteria.put(HRMSConstants.HRMS_USER_SEARCH_CRITERA_TENANTID, tenantId);
			userSearchCriteria.put(HRMSConstants.HRMS_USER_SEARCH_CRITERA_USERNAMES, batch);
			userSearchCriteria.put("pageSize", batch.size());
			UserResponse batchResponse = getUser(requestInfo, userSearchCriteria);
			if (batchResponse != null && !CollectionUtils.isEmpty(batchResponse.getUser()))
				matchedUsers.addAll(batchResponse.getUser());
		}
		aggregated.setTotalCount((long) matchedUsers.size());
		return aggregated;
	}

	@Override
	@SuppressWarnings("unchecked")
	public UserResponse createUsers(RequestInfo requestInfo, List<Employee> employees) {
		UserResponse aggregated = new UserResponse();
		List<org.egov.hrms.web.contract.User> createdUsers = new ArrayList<>();
		aggregated.setUser(createdUsers);
		aggregated.setTotalCount(0L);
		if (CollectionUtils.isEmpty(employees))
			return aggregated;

		StringBuilder uri = new StringBuilder();
		uri.append(propertiesManager.getUserHost()).append(propertiesManager.getUserCreateBulkEndpoint());

		// egov-user's bulk create (/users/v2/_create) caps the batch at egov.user.bulk.max (default 100),
		// so chunk the employees to stay within that limit.
		int batchSize = HRMSConstants.HRMS_USER_BULK_CREATE_BATCH_SIZE;
		for (int start = 0; start < employees.size(); start += batchSize) {
			List<Employee> batch = employees.subList(start, Math.min(start + batchSize, employees.size()));

			List<Map<String, Object>> userMaps = new ArrayList<>();
			for (Employee employee : batch)
				userMaps.add(toBulkUserMap(employee.getUser()));

			Map<String, Object> bulkRequest = new HashMap<>();
			bulkRequest.put("RequestInfo", requestInfo);
			// v2 bulk endpoint reads the list under the lowercase key "users"
			bulkRequest.put("users", userMaps);

			LinkedHashMap<String, Object> responseMap =
					(LinkedHashMap<String, Object>) restCallRepository.fetchResult(uri, bulkRequest);
			List<LinkedHashMap<String, Object>> responseUsers =
					(List<LinkedHashMap<String, Object>>) responseMap.get("users");
			if (responseUsers != null) {
				for (LinkedHashMap<String, Object> responseUser : responseUsers) {
					Object uuid = responseUser.get("uuid");
					// a null uuid/id means egov-user skipped this row (duplicate); leave it unmapped
					if (uuid == null)
						continue;
					Object id = responseUser.get("id");
					createdUsers.add(org.egov.hrms.web.contract.User.builder()
							// v2 serializes the domain field verbatim as lowercase "username"
							.userName((String) responseUser.get("username"))
							.uuid((String) uuid)
							.id(id != null ? Long.valueOf(String.valueOf(id)) : null)
							.build());
				}
			}
		}
		aggregated.setTotalCount((long) createdUsers.size());
		return aggregated;
	}

	/**
	 * Builds a single user entry for the egov-user v2 bulk create request. The v2 endpoint binds the
	 * request to the domain User with the default mapper, so the JSON keys are the domain field names
	 * verbatim - notably the login id is lowercase "username" (not "userName" as in v1).
	 */
	private Map<String, Object> toBulkUserMap(org.egov.hrms.web.contract.User user) {
		Map<String, Object> userMap = new HashMap<>();
		userMap.put("username", user.getUserName());
		userMap.put("name", user.getName());
		userMap.put("mobileNumber", user.getMobileNumber());
		userMap.put("tenantId", user.getTenantId());
		userMap.put("type", user.getType());
		userMap.put("password", user.getPassword());
		userMap.put("active", user.getActive());
		userMap.put("gender", user.getGender());
		userMap.put("emailId", user.getEmailId());
		userMap.put("dob", user.getDob());
		if (user.getRoles() != null) {
			List<Map<String, Object>> roleMaps = new ArrayList<>();
			user.getRoles().forEach(role -> {
				Map<String, Object> roleMap = new HashMap<>();
				roleMap.put("code", role.getCode());
				roleMap.put("name", role.getName());
				roleMap.put("tenantId", role.getTenantId() != null ? role.getTenantId() : user.getTenantId());
				roleMaps.add(roleMap);
			});
			userMap.put("roles", roleMaps);
		}
		return userMap;
	}

	private User getEncrichedandCopiedUserInfo(String tenantId){
		//Creating role with INTERNAL_MICROSERVICE_ROLE
		Role role = Role.builder()
				.name(INTERNALMICROSERVICEROLE_NAME).code(INTERNALMICROSERVICEROLE_CODE)
				.tenantId(centralInstanceUtil.getStateLevelTenant(tenantId)).build();

		//Creating userinfo with uuid and role of internal micro service role
		User userInfo = User.builder()
				.uuid(internalMicroserviceRoleUuid)
				.type(INTERNALMICROSERVICEUSER_TYPE)
				.roles(Collections.singletonList(role)).id(0L).build();

		return userInfo;
	}


	/**
	 * Returns UserDetailResponse by calling user service with given uri and object
	 * @param userRequest Request object for user service
	 * @param uri The address of the endpoint
	 * @return Response from user service as parsed as userDetailResponse
	 */
	@SuppressWarnings("all")
	private UserResponse userCall(Object userRequest, StringBuilder uri) {
		String dobFormat = null;
		if(uri.toString().contains(userSearchEndpoint) || uri.toString().contains(userUpdateEndpoint))
			dobFormat="yyyy-MM-dd";
		else if(uri.toString().contains(userCreateEndpoint))
			dobFormat = "dd/MM/yyyy";
		try{
			LinkedHashMap responseMap = (LinkedHashMap) restCallRepository.fetchResult(uri, userRequest);
			parseResponse(responseMap,dobFormat);
			UserResponse userDetailResponse = objectMapper.convertValue(responseMap,UserResponse.class);
			return userDetailResponse;
		}
		catch(IllegalArgumentException  e) {
			throw new CustomException("HRMS_USER_CALL_RESPONSE_MAP_ERROR",
					String.format("ObjectMapper failed to convert egov-user response to UserResponse. "
									+ "endpoint=%s downstreamExceptionMessage=%s. "
									+ "The response body did not match the expected shape.",
							uri.toString(), e.getMessage() != null ? e.getMessage() : "(no message)"));
		}
	}


	/**
	 * Parses date formats to long for all users in responseMap
	 * @param responeMap LinkedHashMap got from user api response
	 * @param dobFormat dob format (required because dob is returned in different format's in search and create response in user service)
	 */
	@SuppressWarnings("all")
	private void parseResponse(LinkedHashMap responeMap,String dobFormat){
		List<LinkedHashMap> users = (List<LinkedHashMap>)responeMap.get("user");
		String format1 = "dd-MM-yyyy HH:mm:ss";
		if(users!=null){
			users.forEach( map -> {
						map.put("createdDate",dateTolong((String)map.get("createdDate"),format1));
						if((String)map.get("lastModifiedDate")!=null)
							map.put("lastModifiedDate",dateTolong((String)map.get("lastModifiedDate"),format1));
						if((String)map.get("dob")!=null)
							map.put("dob",dateTolong((String)map.get("dob"),dobFormat));
						if((String)map.get("pwdExpiryDate")!=null)
							map.put("pwdExpiryDate",dateTolong((String)map.get("pwdExpiryDate"),format1));
					}
			);
		}
	}

	/**
	 * Converts date to long
	 * @param date date to be parsed
	 * @param format Format of the date
	 * @return Long value of date
	 */
	private Long dateTolong(String date,String format){
		SimpleDateFormat f = new SimpleDateFormat(format);
		Date d = null;
		try {
			d = f.parse(date);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return  d.getTime();
	}


}