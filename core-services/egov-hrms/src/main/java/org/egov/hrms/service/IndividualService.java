package org.egov.hrms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

import digit.models.coremodels.AuditDetails;
import digit.models.coremodels.user.enums.UserType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.Role;
import org.egov.common.models.individual.*;
import org.egov.hrms.config.PropertiesManager;
import org.egov.hrms.repository.RestCallRepository;
import org.egov.hrms.utils.HRMSConstants;
import org.egov.hrms.web.contract.BankDetails;
import org.egov.hrms.web.contract.User;
import org.egov.hrms.web.contract.UserRequest;
import org.egov.hrms.web.contract.UserResponse;
import org.egov.hrms.web.models.IndividualSearch;
import org.egov.hrms.web.models.IndividualSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.egov.hrms.utils.HRMSConstants.HRMS_BANK_DETAILS_ACCOUNT_NUMBER;
import static org.egov.hrms.utils.HRMSConstants.HRMS_BANK_DETAILS_BANK_NAME;
import static org.egov.hrms.utils.HRMSConstants.HRMS_BANK_DETAILS_CBN_CODE;
import static org.egov.hrms.utils.HRMSConstants.SYSTEM_GENERATED;

@Slf4j
public class IndividualService implements UserService {

    private final PropertiesManager propertiesManager;

    private final RestCallRepository restCallRepository;

    @Autowired
    public IndividualService(PropertiesManager propertiesManager,
                             RestCallRepository restCallRepository) {
        this.propertiesManager = propertiesManager;
        this.restCallRepository = restCallRepository;
    }


    @Override
    public UserResponse createUser(UserRequest userRequest) {
        IndividualRequest request = mapToIndividualRequest(userRequest, null);
        StringBuilder uri = new StringBuilder();
        uri.append(propertiesManager.getIndividualHost());
        uri.append(propertiesManager.getIndividualCreateEndpoint());
        IndividualResponse response = restCallRepository
                .fetchResult(uri, request, IndividualResponse.class);
        UserResponse userResponse = null;
        if (response != null && response.getIndividual() != null) {
            log.info("response received from individual service");
            userResponse = mapToUserResponse(response);
        }
        return userResponse;
    }
    public UserResponse createUserByLocality(UserRequest userRequest, String localityCode) {
        IndividualRequest request = mapToIndividualRequest(userRequest,localityCode);
        StringBuilder uri = new StringBuilder();
        uri.append(propertiesManager.getIndividualHost());
        uri.append(propertiesManager.getIndividualCreateEndpoint());
        IndividualResponse response = restCallRepository
                .fetchResult(uri, request, IndividualResponse.class);
        UserResponse userResponse = null;
        if (response != null && response.getIndividual() != null) {
            log.info("response received from individual service");
            userResponse = mapToUserResponse(response);
        }
        return userResponse;
    }

    /**
     * Updates a user by searching for the corresponding individual and updating their details.
     *
     * Steps:
     * 1. Map UserRequest to IndividualSearchRequest.
     * 2. Fetch individual data using tenant ID and search request.
     * 3. Return null if no individual is found.
     * 4. Prepare an IndividualRequest for update.
     * 5. Construct the update endpoint URI.
     * 6. Perform REST call to update individual.
     * 7. Map response to UserResponse if successful.
     * 8. Return UserResponse.
     * TODO FIXME
     * @param userRequest The request object containing user details to be updated.
     * @return UserResponse containing updated user information, or null if no individual was found.
     */
    @Override
    public UserResponse updateUser(UserRequest userRequest) {
        // Map the UserRequest to an IndividualSearchRequest
        IndividualSearchRequest individualSearchRequest = mapToIndividualSearchRequest(userRequest);

        // Fetch the individual response from the individual service
        IndividualBulkResponse individualSearchResponse =
                getIndividualResponse(userRequest.getUser().getTenantId(), individualSearchRequest);

        UserResponse userResponse = null;

        // Check if the individual search response is null or contains no individuals
        if (individualSearchResponse == null || individualSearchResponse.getIndividual() == null || individualSearchResponse.getIndividual().isEmpty()) {
            return userResponse;  // Return null if no individual is found
        }

        // Get the first individual from the search response
        Individual individual = individualSearchResponse.getIndividual().get(0);

        // Map the found individual and the user request to an IndividualRequest for update
        IndividualRequest updateRequest = mapToIndividualUpdateRequest(individual, userRequest);

        // Build the URI for the update endpoint
        StringBuilder uri = new StringBuilder();
        uri.append(propertiesManager.getIndividualHost());
        uri.append(propertiesManager.getIndividualUpdateEndpoint());

        // Make a REST call to update the individual
        IndividualResponse response = restCallRepository
                .fetchResult(uri, updateRequest, IndividualResponse.class);

        // If the response is not null and contains an updated individual, map it to UserResponse
        if (response != null && response.getIndividual() != null) {
            log.info("Response received from individual service");
            userResponse = mapToUserResponse(response);
        }

        // Return the UserResponse
        return userResponse;
    }


    private IndividualRequest mapToIndividualUpdateRequest(Individual individual, UserRequest userRequest) {
        Individual updatedIndividual = Individual.builder()
                .id(individual.getId())
                .userId(individual.getUserId())
                .userUuid(individual.getUserUuid())
                .isSystemUser(true)
                .isSystemUserActive(userRequest.getUser().getActive())
                .name(Name.builder()
                        .givenName(userRequest.getUser().getName())
                        .build())
                .gender(Gender.fromValue(userRequest.getUser().getGender()))
                .email(userRequest.getUser().getEmailId())
                .mobileNumber(userRequest.getUser().getMobileNumber())
                .dateOfBirth(convertMillisecondsToDate(userRequest.getUser().getDob()))
                .tenantId(userRequest.getUser().getTenantId())
                .additionalFields(buildAdditionalFields(userRequest))
                .address(Collections.singletonList(Address.builder()
                        .type(AddressType.CORRESPONDENCE)
                        .addressLine1(userRequest.getUser().getCorrespondenceAddress())
                        .clientReferenceId(String.valueOf(UUID.randomUUID()))
                        .isDeleted(Boolean.FALSE)
                        .build()))
                /*
                 * FIXME (HCM specific change) clientReferenceId is the primary key in the individual table of the FrontEnd Worker Application's local database.
                 */
                // Generating a unique client reference ID using UUID
                .clientReferenceId(individual.getClientReferenceId())
                .userDetails(UserDetails.builder()
                        .username(userRequest.getUser().getUserName())
                        .password(userRequest.getUser().getPassword())
                        .tenantId(userRequest.getUser().getTenantId())
                        .roles(userRequest.getUser().getRoles().stream().map(role -> Role.builder()
                                .code(role.getCode())
                                .name(role.getName())
                                .tenantId(userRequest.getUser().getTenantId())
                                .description(role.getDescription())
                                .build()).collect(Collectors.toList()))
                        .userType(individual.getUserDetails().getUserType())
                        .build())
                .isDeleted(Boolean.FALSE)
                .clientAuditDetails(AuditDetails.builder()
                        .createdBy(individual.getAuditDetails().getCreatedBy())
                        .createdTime(individual.getAuditDetails().getCreatedTime())
                        .lastModifiedBy(userRequest.getRequestInfo().getUserInfo().getUuid()).build())
                .rowVersion(userRequest.getUser().getRowVersion())
                .build();
        return IndividualRequest.builder()
                .requestInfo(userRequest.getRequestInfo())
                .individual(updatedIndividual)
                .build();
    }

    private IndividualSearchRequest mapToIndividualSearchRequest(UserRequest userRequest) {
        return IndividualSearchRequest.builder()
                .requestInfo(userRequest.getRequestInfo())
                .individual(
                        IndividualSearch.builder()
                        .id(Collections.singletonList(userRequest.getUser().getUuid()))
                        .userUuid(userRequest.getUser().getUserServiceUuid() != null ? Collections.singletonList(userRequest.getUser().getUserServiceUuid()) : null)
                        .build()
                )
                .build();
    }

    @Override
    public UserResponse getUser(RequestInfo requestInfo, Map<String, Object> userSearchCriteria ) {
        String mobileNumber = (String) userSearchCriteria.get("mobileNumber");
        String username = (String) userSearchCriteria.get(HRMSConstants.HRMS_USER_SEARCH_CRITERA_USERNAME);
        List<String> mobileNumberList = null;
        List<String> usernameList = null;
        if(!StringUtils.isEmpty(mobileNumber)) {
            mobileNumberList = Collections.singletonList(mobileNumber);
        }
        if(!StringUtils.isEmpty(username)) {
            usernameList = Collections.singletonList(username);
        }
        IndividualSearchRequest request = IndividualSearchRequest.builder()
                .requestInfo(requestInfo)
                .individual(IndividualSearch.builder()
                        .mobileNumber(
                                mobileNumberList
                        )
                        .id((List<String>) userSearchCriteria.get("uuid"))
                        .roleCodes((List<String>) userSearchCriteria.get("roleCodes"))
                        .username(usernameList)
                        // given name
                        .individualName((String) userSearchCriteria
                                .get(HRMSConstants.HRMS_USER_SEARCH_CRITERA_NAME))
                        .type((String) userSearchCriteria.get(HRMSConstants.HRMS_USER_SERACH_CRITERIA_USERTYPE_CODE))
                .build())
                .build();
        IndividualBulkResponse response = getIndividualResponse((String) userSearchCriteria
                .get(HRMSConstants.HRMS_USER_SEARCH_CRITERA_TENANTID),
                request);
        UserResponse userResponse = new UserResponse();
        if (response != null && response.getIndividual() != null && !response.getIndividual().isEmpty()) {
            log.info("response received from individual service");
            userResponse = mapToUserResponse(response);
        }
        return userResponse;
    }

    private IndividualBulkResponse getIndividualResponse(String tenantId, IndividualSearchRequest individualSearchRequest) {
        return restCallRepository.fetchResult(
                new StringBuilder(propertiesManager.getIndividualHost()
                        + propertiesManager.getIndividualSearchEndpoint()
                        + "?limit=1000&offset=0&tenantId=" + tenantId),
                individualSearchRequest, IndividualBulkResponse.class);
    }


    /**
     * Converts a long value representing milliseconds since the epoch to a Date object in the format dd/MM/yyyy.
     *
     * @param milliseconds the long value representing milliseconds since the epoch.
     * @return a Date object in the format dd/MM/yyyy.
     */
    private static Date convertMillisecondsToDate(long milliseconds) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateString = formatter.format(new Date(milliseconds));
        try {
            return formatter.parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static IndividualRequest mapToIndividualRequest(UserRequest userRequest, String localityCode) {
        Individual individual = Individual.builder()
                .id(userRequest.getUser().getUuid())
                .userId(userRequest.getUser().getId() != null ?
                        String.valueOf(userRequest.getUser().getId()) : null)
                .userUuid(userRequest.getUser().getUserServiceUuid())
                .isSystemUser(true)
                .isSystemUserActive(userRequest.getUser().getActive())
                .name(Name.builder()
                        .givenName(userRequest.getUser().getName())
                        .familyName(userRequest.getUser().getSignature())
                        .build())
                .gender(Gender.fromValue(userRequest.getUser().getGender()))
                .email(userRequest.getUser().getEmailId())
                .mobileNumber(userRequest.getUser().getMobileNumber())
                .dateOfBirth(convertMillisecondsToDate(userRequest.getUser().getDob()))
                .tenantId(userRequest.getUser().getTenantId())
                .additionalFields(buildAdditionalFields(userRequest))
                .address(Collections.singletonList(Address.builder()
                                .type(AddressType.CORRESPONDENCE)
                                .addressLine1(userRequest.getUser().getCorrespondenceAddress())
                                .clientReferenceId(String.valueOf(UUID.randomUUID()))
                                .locality((localityCode!=null) ? Boundary.builder().code(localityCode).build() : null)
                                .isDeleted(Boolean.FALSE)
                        .build()))
                /*
                 * FIXME (HCM specific change) clientReferenceId is the primary key in the individual table of the FrontEnd Worker Application's local database. 
                 */
                // Generating a unique client reference ID using UUID
                .clientReferenceId(String.valueOf(UUID.randomUUID()))
                // Creating a list of identifiers
                .identifiers(Collections.singletonList(
                        // Building a unique identifier
                        Identifier.builder()
                                // Generating a unique client reference ID using UUID for the identifier
                                .clientReferenceId(String.valueOf(UUID.randomUUID()))
                                // Generating a unique identifier ID using UUID
                                .identifierId(String.valueOf(UUID.randomUUID()))
                                // Specifying the type of identifier as SYSTEM_GENERATED
                                .identifierType(SYSTEM_GENERATED)
                                .build()))
                .userDetails(UserDetails.builder()
                        .username(userRequest.getUser().getUserName())
                        .password(userRequest.getUser().getPassword())
                        .tenantId(userRequest.getUser().getTenantId())
                        .roles(userRequest.getUser().getRoles().stream().map(role -> Role.builder()
                                .code(role.getCode())
                                .name(role.getName())
                                .tenantId(userRequest.getUser().getTenantId())
                                .description(role.getDescription())
                                .build()).collect(Collectors.toList()))
                        .userType(UserType.fromValue(userRequest.getUser().getType()))
                        .build())
                .skills(userRequest.getUser().getRoles().stream().map(role -> Skill.builder()
                        .type(role.getCode()).level(role.getCode())
                        .build()).collect(Collectors.toList()))
                .isDeleted(Boolean.FALSE)
                .clientAuditDetails(AuditDetails.builder().createdBy(userRequest.getRequestInfo().getUserInfo().getUuid()).lastModifiedBy(userRequest.getRequestInfo().getUserInfo().getUuid()).build())
                .rowVersion(userRequest.getUser().getRowVersion())
                .build();
        return IndividualRequest.builder()
                .requestInfo(userRequest.getRequestInfo())
                .individual(individual)
                .build();
    }

    private static AdditionalFields buildAdditionalFields(UserRequest userRequest) {
        AdditionalFields additionalFields = AdditionalFields.builder()
                .schema("Individual")
                .version(1)
                .build();

        
        // Add userType if present
        if (userRequest.getUser().getIdentificationMark() != null) {
            additionalFields.addFieldsItem(Field.builder()
                        .key("userType")
                        .value(userRequest.getUser().getIdentificationMark())
                        .build());
        }

        // Add bank details if present
        if (userRequest.getUser().getBankDetails() != null) {
                BankDetails bankDetails = userRequest.getUser().getBankDetails();
                // Add bank details fields only if they are not null
                if (bankDetails.getAccountNumber() != null && bankDetails.getBankName() != null && bankDetails.getCbnCode() != null) {
                        additionalFields.addFieldsItem(Field.builder()
                                .key(HRMS_BANK_DETAILS_ACCOUNT_NUMBER)
                                .value(bankDetails.getAccountNumber())
                                .build());
                        additionalFields.addFieldsItem(Field.builder()
                                .key(HRMS_BANK_DETAILS_BANK_NAME)
                                .value(bankDetails.getBankName())
                                .build());
                        additionalFields.addFieldsItem(Field.builder()
                                .key(HRMS_BANK_DETAILS_CBN_CODE)
                                .value(bankDetails.getCbnCode())
                                .build());
                }
                
        }

        // If no fields were added, return null
        return additionalFields.getFields() == null ? null : additionalFields;
    }

    private static UserResponse mapToUserResponse(IndividualResponse response) {
        UserResponse userResponse;
        userResponse = UserResponse.builder()
                .responseInfo(response.getResponseInfo())
                .user(Collections.singletonList(getUser(response.getIndividual())))
                .build();
        return userResponse;
    }

    private static UserResponse mapToUserResponse(IndividualBulkResponse response) {
        UserResponse userResponse;
        userResponse = UserResponse.builder()
                .responseInfo(response.getResponseInfo())
                .user(response.getIndividual().stream()
                        .map(IndividualService::getUser).collect(Collectors.toList()))
                .build();
        return userResponse;
    }


    private static User getUser(Individual individual) {
        return User.builder()
                .id(individual.getUserId() != null ? Long.parseLong(individual.getUserId()) : null)
                .mobileNumber(individual.getMobileNumber())
                .name(individual.getName().getGivenName())
                .uuid(individual.getId())
                .userServiceUuid(individual.getUserUuid())
                .active(individual.getIsSystemUserActive())
                .gender(individual.getGender() != null ? individual.getGender().name() : null)
                .type(individual.getUserDetails().getUserType().toString())
                .userName(individual.getUserDetails().getUsername())
                .emailId(individual.getEmail())
                .correspondenceAddress(individual.getAddress() != null && !individual.getAddress().isEmpty()
                        ? individual.getAddress().stream().filter(address -> address.getType()
                                .equals(AddressType.CORRESPONDENCE)).findFirst()
                        .orElse(Address.builder().build())
                        .getAddressLine1() : null)
                .dob(individual.getDateOfBirth().getTime())
                .tenantId(individual.getTenantId())
                .createdBy(individual.getAuditDetails().getCreatedBy())
                .createdDate(individual.getAuditDetails().getCreatedTime())
                .lastModifiedBy(individual.getAuditDetails().getLastModifiedBy())
                .lastModifiedDate(individual.getAuditDetails().getLastModifiedTime())
                .rowVersion(individual.getRowVersion())
                .roles(individual.getUserDetails()
                        .getRoles().stream().map(role -> org.egov.hrms.model.Role.builder()
                                .code(role.getCode())
                                .tenantId(role.getTenantId())
                                .name(role.getName())
                                .build()).collect(Collectors.toList()))
                .bankDetails(getBankDetails(individual))
                .build();
    }

    private static BankDetails getBankDetails(Individual individual) {
        BankDetails bankDetails = BankDetails.builder()
                        .accountNumber(getBankDetailFieldbyKey(individual, HRMS_BANK_DETAILS_ACCOUNT_NUMBER))
                        .bankName(getBankDetailFieldbyKey(individual, HRMS_BANK_DETAILS_BANK_NAME))
                        .cbnCode(getBankDetailFieldbyKey(individual, HRMS_BANK_DETAILS_CBN_CODE))
                        .build();
        if (bankDetails.getAccountNumber() != null || bankDetails.getBankName() != null || bankDetails.getCbnCode() != null) {
                return bankDetails;
        }
        return null;
    }

    private static String getBankDetailFieldbyKey(Individual individual, String key) {
        if (individual.getAdditionalFields() != null && individual.getAdditionalFields().getFields() != null) {
            return individual.getAdditionalFields().getFields().stream()
                    .filter(field -> field.getKey().equals(key))
                    .findFirst()
                    .map(Field::getValue)
                    .orElse(null);
        }
        return null;
    }
}
