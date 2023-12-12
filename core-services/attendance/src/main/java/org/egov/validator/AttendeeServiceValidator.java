package org.egov.validator;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.util.IndividualServiceUtil;
import org.egov.util.MDMSUtils;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendeeCreateRequest;
import org.egov.web.models.AttendeeDeleteRequest;
import org.egov.web.models.IndividualEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.util.AttendanceServiceConstants.MASTER_TENANTS;
import static org.egov.util.AttendanceServiceConstants.MDMS_TENANT_MODULE_NAME;

@Component
@Slf4j
public class AttendeeServiceValidator {

    @Autowired
    private MDMSUtils mdmsUtils;

    @Autowired
    private IndividualServiceUtil individualServiceUtil;

    public void validateAttendeeCreateRequestParameters(AttendeeCreateRequest attendeeCreateRequest) {
        List<IndividualEntry> attendeeList = attendeeCreateRequest.getAttendees();
        Map<String, String> errorMap = new HashMap<>();

        if (attendeeList == null || attendeeList.isEmpty()) {
            log.error("ATTENDEE Object is empty in attendee request");
            throw new CustomException("ATTENDEE", "ATTENDEE Object is empty in attendee request");
        }

        String tenantId = attendeeList.get(0).getTenantId();
        for (IndividualEntry attendee : attendeeList) {

            //validate request parameters for each attendee object
            if (StringUtils.isBlank(attendee.getRegisterId())) {
                log.error("register id is empty in attendee request");
                errorMap.put("REGISTER_ID", "Register id is mandatory");
            }

            if (StringUtils.isBlank(attendee.getIndividualId())) {
                log.error("individual id is empty in attendee request");
                errorMap.put("INDIVIDUAL_ID", "Individual id is mandatory");
            }

            if (StringUtils.isBlank(attendee.getTenantId())) {
                log.error("tenant id is empty in attendee request");
                errorMap.put("TENANT_ID", "Tenant id is mandatory");
            }
        }

        if (!errorMap.isEmpty()) {
            log.error("Attendee request validation failed");
            throw new CustomException(errorMap);
        }
        validateTenantIds(attendeeCreateRequest, tenantId);
        validateDuplicateAttendeeObjects(attendeeCreateRequest);

        //validate tenantId with MDMS
        log.info("validating tenant id from MDMS and Request info");
        validateMDMSAndRequestInfoForCreateAttendee(attendeeCreateRequest);

        //validate individualId with Individual Service
        log.info("validating tenant id from MDMS and Request info");
        validateIndividualId(attendeeCreateRequest);
    }

    private void validateIndividualId(AttendeeCreateRequest attendeeCreateRequest) {
        RequestInfo requestInfo=attendeeCreateRequest.getRequestInfo();
        String tenantId=attendeeCreateRequest.getAttendees().get(0).getTenantId();
        List<String> individualIds=attendeeCreateRequest.getAttendees().stream().map(attendee->attendee.getIndividualId()).collect(Collectors.toList());
        List<String> ids= individualServiceUtil.fetchIndividualIds(individualIds,requestInfo,tenantId);

        for(String individualId:individualIds){
            if(!ids.contains(individualId)){
                throw new CustomException("INDIVIDUAL_ID_NOT_FOUND","Individual with id: "+individualId+" not found");
            }
        }
    }

    public void validateTenantIds(AttendeeCreateRequest attendeeCreateRequest, String tenantId) {
        List<IndividualEntry> attendeeList = attendeeCreateRequest.getAttendees();
        //validate if all attendee in the list have the same tenant id
        for (IndividualEntry attendee : attendeeList) {
            if (!attendee.getTenantId().equals(tenantId)) {
                log.error("All attendees dont have the same tenant id in attendee request");
                throw new CustomException("TENANT_ID", "All Attendees to be enrolled or de enrolled must have the same tenant id. Please raise new request for different tenant id");
            }
        }

    }

    public void validateDuplicateAttendeeObjects(AttendeeCreateRequest attendeeCreateRequest) {
        List<IndividualEntry> attendeeList = attendeeCreateRequest.getAttendees();

        Set<String> uniqueIds = new HashSet<>();
        for (IndividualEntry attendee : attendeeList) {
            String uniqueId = attendee.getRegisterId() + attendee.getIndividualId();
            if (uniqueIds.isEmpty()) {
                uniqueIds.add(attendee.getRegisterId() + attendee.getIndividualId());
            } else if (uniqueIds.contains(uniqueId)) {
                log.error("Duplicate Attendee Objects found in request");
                throw new CustomException("ATTENDEE", "Duplicate attendee Objects present in request");
            }
            uniqueIds.add(attendee.getRegisterId() + attendee.getIndividualId());
        }
    }


    public void validateAttendeeDeleteRequestParameters(AttendeeDeleteRequest attendeeDeleteRequest, Map<String, String> errorMap) {

        List<IndividualEntry> attendeeList = attendeeDeleteRequest.getAttendees();

        if (attendeeList == null || attendeeList.isEmpty()) {
            log.error("ATTENDEE Object is empty in attendee request");
            throw new CustomException("ATTENDEES", "ATTENDEE object is mandatory");
        }

        String tenantId = attendeeList.get(0).getTenantId();
        for (IndividualEntry attendee : attendeeList) {

            //validate request parameters for each attendee object
            if (StringUtils.isBlank(attendee.getRegisterId())) {
                log.error("REGISTER_ID is empty in attendee request");
                errorMap.put("REGISTER_ID", "Register id is mandatory");
            }

            if (StringUtils.isBlank(attendee.getIndividualId())) {
                log.error("INDIVIDUAL_ID is empty in attendee request");
                errorMap.put("INDIVIDUAL_ID", "Individual id is mandatory");
            }

            if (StringUtils.isBlank(attendee.getTenantId())) {
                log.error("TENANT_ID is empty in attendee request");
                errorMap.put("TENANT_ID", "Tenant id is mandatory");
            }
        }

        if (!errorMap.isEmpty()) {
            log.error("Attendee request validation failed");
            throw new CustomException(errorMap);
        }

        validateTenantIds(attendeeDeleteRequest, tenantId);
        validateDuplicateAttendeeObjects(attendeeDeleteRequest);

        //validate tenantId with MDMS
        log.info("validating tenant id from MDMS and Request info");
        validateMDMSAndRequestInfoForDeleteAttendee(attendeeDeleteRequest);

        //validate individualId with Individual Service
        log.info("validating tenant id from MDMS and Request info");
        validateIndividualId(attendeeDeleteRequest);
    }

    private void validateIndividualId(AttendeeDeleteRequest attendeeDeleteRequest) {
        RequestInfo requestInfo=attendeeDeleteRequest.getRequestInfo();
        String tenantId=attendeeDeleteRequest.getAttendees().get(0).getTenantId();
        List<String> individualIds=attendeeDeleteRequest.getAttendees().stream().map(attendee->attendee.getIndividualId()).collect(Collectors.toList());
        List<String> ids= individualServiceUtil.fetchIndividualIds(individualIds,requestInfo,tenantId);

        for(String individualId:individualIds){
            if(!ids.contains(individualId)){
                throw new CustomException("INDIVIDUAL_ID_NOT_FOUND","Individual with id: "+individualId+" not found");
            }
        }
    }

    public void validateTenantIds(AttendeeDeleteRequest attendeeDeleteRequest, String tenantId) {
        List<IndividualEntry> attendeeList = attendeeDeleteRequest.getAttendees();
        //validate if all attendee in the list have the same tenant id
        for (IndividualEntry attendee : attendeeList) {
            if (!attendee.getTenantId().equals(tenantId)) {
                log.error("All attendees dont have the same tenant id in attendee request");
                throw new CustomException("TENANT_ID", "All Attendees to be enrolled or de enrolled must have the same tenant id. Please raise new request for different tenant id");
            }
        }

    }

    public void validateDuplicateAttendeeObjects(AttendeeDeleteRequest attendeeDeleteRequest) {
        List<IndividualEntry> attendeeList = attendeeDeleteRequest.getAttendees();

        Set<String> uniqueIds = new HashSet<>();
        for (IndividualEntry attendee : attendeeList) {
            String uniqueId = attendee.getRegisterId() + attendee.getIndividualId();
            if (uniqueIds.isEmpty()) {
                uniqueIds.add(attendee.getRegisterId() + attendee.getIndividualId());
            } else if (uniqueIds.contains(uniqueId)) {
                log.error("Duplicate Attendee Objects found in request");
                throw new CustomException("ATTENDEE", "Duplicate attendee Objects present in request");
            }
            uniqueIds.add(attendee.getRegisterId() + attendee.getIndividualId());
        }
    }

    public void validateMDMSAndRequestInfoForCreateAttendee(AttendeeCreateRequest attendeeCreateRequest) {

        RequestInfo requestInfo = attendeeCreateRequest.getRequestInfo();
        List<IndividualEntry> attendeeListFromRequest = attendeeCreateRequest.getAttendees();
        Map<String, String> errorMap = new HashMap<>();

        String tenantId = attendeeListFromRequest.get(0).getTenantId();
        //split the tenantId
        String rootTenantId = tenantId.split("\\.")[0];

        Object mdmsData = mdmsUtils.mDMSCall(requestInfo, rootTenantId);

        //check tenant Id
        log.info("validate tenantId with MDMS");
        validateMDMSData(tenantId, mdmsData, errorMap);


        //validate request-info
        log.info("validate request info coming from api request");
        validateRequestInfo(requestInfo, errorMap);

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    public void validateMDMSAndRequestInfoForDeleteAttendee(AttendeeDeleteRequest attendeeDeleteRequest) {

        RequestInfo requestInfo = attendeeDeleteRequest.getRequestInfo();
        List<IndividualEntry> attendeeListFromRequest = attendeeDeleteRequest.getAttendees();
        Map<String, String> errorMap = new HashMap<>();

        String tenantId = attendeeListFromRequest.get(0).getTenantId();
        //split the tenantId
        String rootTenantId = tenantId.split("\\.")[0];

        Object mdmsData = mdmsUtils.mDMSCall(requestInfo, rootTenantId);

        //check tenant Id
        log.info("validate tenantId with MDMS");
        validateMDMSData(tenantId, mdmsData, errorMap);


        //validate request-info
        log.info("validate request info coming from api request");
        validateRequestInfo(requestInfo, errorMap);

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }


    public void validateAttendeeOnCreate(AttendeeCreateRequest attendeeCreateRequest
            , List<IndividualEntry> attendeeListFromDB, List<AttendanceRegister> attendanceRegisterListFromDB) {

        List<IndividualEntry> attendeeListFromRequest = attendeeCreateRequest.getAttendees();


        // attendee cannot be added to register if register's end date has passed
        log.info("verifying that attendee cannot be added to register if register's end date has passed");
        BigDecimal currentDate = new BigDecimal(System.currentTimeMillis());
        for (AttendanceRegister attendanceRegister : attendanceRegisterListFromDB) {
            int dateComparisonResult = attendanceRegister.getEndDate().compareTo(currentDate);
            if (dateComparisonResult < 0) {
                throw new CustomException("END_DATE", "Attendee cannot be enrolled as END_DATE of register id " + attendanceRegister.getId() + " has already passed.");
            }
        }

        //attendee enrollment date, if present in request should be after start date and before end date of register
        log.info("checking attendee enrollment date should be after start date and before end date of register");
        for (AttendanceRegister attendanceRegister : attendanceRegisterListFromDB) {
            for (IndividualEntry attendeeFromRequest : attendeeListFromRequest) {
                if (attendanceRegister.getId().equals(attendeeFromRequest.getRegisterId())) {
                    if (attendeeFromRequest.getEnrollmentDate() != null) {
                        int startDateCompare = attendeeFromRequest.getEnrollmentDate().compareTo(attendanceRegister.getStartDate());
                        int endDateCompare = attendanceRegister.getEndDate().compareTo(attendeeFromRequest.getEnrollmentDate());
                        if (startDateCompare < 0 || endDateCompare < 0) {
                            throw new CustomException("ENROLLMENT_DATE"
                                    , "Enrollment date for attendee : " + attendeeFromRequest.getIndividualId() + " must be within start and end date of the register");
                        }
                    }
                }
            }
        }

        //check if attendee is already enrolled to the register
        log.info("checking if attendee is already enrolled to the register");
        for (IndividualEntry attendeeFromRequest : attendeeListFromRequest) {
            for (IndividualEntry attendeeFromDB : attendeeListFromDB) {
                if (attendeeFromRequest.getRegisterId().equals(attendeeFromDB.getRegisterId())
                        && attendeeFromRequest.getIndividualId().equals(attendeeFromDB.getIndividualId())) {//attendee present in db
                    if (attendeeFromDB.getDenrollmentDate() == null) { // already enrolled to the register
                        throw new CustomException("INDIVIDUAL_ID", "Attendee " + attendeeFromRequest.getIndividualId() + " is already enrolled in the register " + attendeeFromRequest.getRegisterId());

                    }
                }
            }
        }
    }

    public void validateAttendeeOnDelete(AttendeeDeleteRequest attendeeDeleteRequest,
                                         List<IndividualEntry> attendeeListFromDB, List<AttendanceRegister> attendanceRegisterListFromDB) {

        List<IndividualEntry> attendeeListFromRequest = attendeeDeleteRequest.getAttendees();


        //attendee de-enrollment date, if present in request should be before end date and after start date of register
        log.info("verifying attendee de-enrollment date, if present in request should be before end date and after start date of register");
        for (AttendanceRegister attendanceRegister : attendanceRegisterListFromDB) {
            for (IndividualEntry attendeeFromRequest : attendeeListFromRequest) {
                if (attendeeFromRequest.getDenrollmentDate() != null) {
                    int startDateCompare = attendeeFromRequest.getDenrollmentDate().compareTo(attendanceRegister.getStartDate());
                    int endDateCompare = attendanceRegister.getEndDate().compareTo(attendeeFromRequest.getDenrollmentDate());
                    if (startDateCompare < 0 || endDateCompare < 0) {
                        throw new CustomException("DE ENROLLMENT_DATE"
                                , "De enrollment date for attendee : " + attendeeFromRequest.getIndividualId() + " must be between start date and end date of the register");
                    }
                }
            }
        }

        //check if attendee is already de-enrolled from the register
        log.info("checking if attendee is already de-enrolled from the register");
        boolean attendeeDeEnrolled = true;
        for (IndividualEntry attendeeFromRequest : attendeeListFromRequest) {
            for (IndividualEntry attendeeFromDB : attendeeListFromDB) {
                if (attendeeFromRequest.getRegisterId().equals(attendeeFromDB.getRegisterId()) && attendeeFromDB.getIndividualId().equals(attendeeFromRequest.getIndividualId())) { //attendee present in db
                    if (attendeeFromDB.getDenrollmentDate() == null) {
                        attendeeDeEnrolled = false;
                        break;
                    }
                }
            }
            if (attendeeDeEnrolled) {
                throw new CustomException("INDIVIDUAL_ID", "Attendee " + attendeeFromRequest.getIndividualId() + " is already de enrolled from the register " + attendeeFromRequest.getRegisterId());
            }
        }

    }

    private void validateRequestInfo(RequestInfo requestInfo, Map<String, String> errorMap) {
        if (requestInfo == null) {
            log.error("Request info is null");
            throw new CustomException("REQUEST_INFO", "Request info is mandatory");
        }
        if (requestInfo.getUserInfo() == null) {
            log.error("User info is null");
            throw new CustomException("USERINFO", "UserInfo is mandatory");
        }
        if (requestInfo.getUserInfo() != null && StringUtils.isBlank(requestInfo.getUserInfo().getUuid())) {
            log.error("User's UUID field is empty");
            throw new CustomException("USERINFO_UUID", "User's UUID is mandatory");
        }
    }

    private void validateMDMSData(String tenantId, Object mdmsData, Map<String, String> errorMap) {
        final String jsonPathForTenants = "$.MdmsRes." + MDMS_TENANT_MODULE_NAME + "." + MASTER_TENANTS + ".*";

        List<Object> tenantRes = null;
        try {
            tenantRes = JsonPath.read(mdmsData, jsonPathForTenants);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
        }

        if (CollectionUtils.isEmpty(tenantRes))
            errorMap.put("INVALID_TENANT", "The tenant: " + tenantId + " is not present in MDMS");
    }
}


