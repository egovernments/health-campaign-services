package org.egov.validator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.repository.AttendanceLogRepository;
import org.egov.repository.AttendeeRepository;
import org.egov.repository.RegisterRepository;
import org.egov.repository.StaffRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.IndividualServiceUtil;
import org.egov.web.models.AttendanceLog;
import org.egov.web.models.AttendanceLogRequest;
import org.egov.web.models.AttendanceLogSearchCriteria;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterSearchCriteria;
import org.egov.web.models.AttendeeSearchCriteria;
import org.egov.web.models.IndividualEntry;
import org.egov.web.models.RequestInfoWrapper;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.StaffSearchCriteria;
import org.egov.web.models.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AttendanceLogServiceValidator {

    @Autowired
    private StaffRepository attendanceStaffRepository;

    @Autowired
    private RegisterRepository attendanceRegisterRepository;

    @Autowired
    private AttendeeRepository attendanceAttendeeRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private AttendanceServiceConfiguration config;

    @Autowired
    private IndividualServiceUtil individualServiceUtil;

    public void validateCreateAttendanceLogRequest(AttendanceLogRequest attendanceLogRequest) {
        log.info("Validate attendance log create request");
        validateAttendanceLogRequest(attendanceLogRequest);

        // Verify all the attendance logs should below to same registerId
        validateMultipleRegisterIds(attendanceLogRequest);

        // Verify all the attendance logs should below to same tenantId
        validateMultipleTenantIds(attendanceLogRequest);

        // Verify the Logged-in user is associated to the given register.
        validateLoggedInUser(attendanceLogRequest);

        // Verify given attendance log against register params
        validateAttendanceLogsAgainstRegisterParams(attendanceLogRequest);

        // Verify if individuals are part of the given register and individuals were active during given attendance log time.
        validateAttendees(attendanceLogRequest);

        // Verify provided documentIds are valid.
        validateDocumentIds(attendanceLogRequest);
        log.info("Attendance log create request validation done");
    }

    private void validateMultipleTenantIds(AttendanceLogRequest attendanceLogRequest) {
        List<AttendanceLog> attendanceLogs = attendanceLogRequest.getAttendance();
        Set<String> tenantIds = new HashSet<>();
        for(AttendanceLog attendanceLog : attendanceLogs){
            String tenantId = attendanceLog.getTenantId();
            if(tenantIds.isEmpty()){
                tenantIds.add(tenantId);
            }else{
                if(!tenantIds.contains(tenantId)){
                    log.error("Attendance logs should below to same tenantId");
                    throw new CustomException("MULTIPLE_TENANTIDS","Attendance logs should belong to same tenantId");
                }
            }
        }
    }

    private void validateMultipleRegisterIds(AttendanceLogRequest attendanceLogRequest) {
        List<AttendanceLog> attendanceLogs = attendanceLogRequest.getAttendance();
        Set<String> registerIds = new HashSet<>();
        for(AttendanceLog attendanceLog : attendanceLogs){
            String registerId = attendanceLog.getRegisterId();
            if(registerIds.isEmpty()){
                registerIds.add(registerId);
            }else{
                if(!registerIds.contains(registerId)){
                    log.error("Attendance logs should below to same registerId");
                    throw new CustomException("MULTIPLE_REGISTERIDS","Attendance logs should belong to same registerId");
                }
            }
        }
    }

    public void validateUpdateAttendanceLogRequest(AttendanceLogRequest attendanceLogRequest) {
        log.info("Validate attendance log update request");
        validateAttendanceLogRequest(attendanceLogRequest);

        // Verify all the attendance logs should below to same registerId
        validateMultipleRegisterIds(attendanceLogRequest);

        // Verify all the attendance logs should below to same tenantId
        validateMultipleTenantIds(attendanceLogRequest);

        // Verify the Logged-in user is associated to the given register.
        validateLoggedInUser(attendanceLogRequest);

        // Verify provided log ids are present
        validateAttendanceLogIds(attendanceLogRequest);

        // Verify given attendance log against register params
        validateAttendanceLogsAgainstRegisterParams(attendanceLogRequest);

        // Verify if individuals are part of the given register and individuals were active during given attendance log time.
        validateAttendees(attendanceLogRequest);

        // Verify provided documentIds are valid.
        validateDocumentIds(attendanceLogRequest);

        log.info("Attendance log update request validation done");
    }

    private void validateAttendanceLogsAgainstRegisterParams(AttendanceLogRequest attendanceLogRequest){
        String registerId = attendanceLogRequest.getAttendance().get(0).getRegisterId();
        String tenantId = attendanceLogRequest.getAttendance().get(0).getTenantId();

        // Fetch register for given registerId
        List<AttendanceRegister> attendanceRegisters = fetchRegisterWithId(registerId);

        // Check existence of register
        checkRegisterExistence(attendanceRegisters,registerId);

        AttendanceRegister attendanceRegister = attendanceRegisters.get(0);

        // Check register is active ?
        checkRegisterStatus(attendanceRegister);

        // Check register association with tenantId
        validateTenantIdAssociationWithRegisterId(attendanceRegister,tenantId);

        // Check attendance log time against register start and end date
        validateAttendanceLogTimeWithRegisterStartEndDate(attendanceRegister,attendanceLogRequest);

        log.info("Attendance log verification against register params are done. RegisterId ["+registerId+"]");
    }

    private void checkRegisterStatus(AttendanceRegister attendanceRegister) {
        if(Status.INACTIVE.equals(attendanceRegister.getStatus())){
            String registerId = attendanceRegister.getId();
            log.error("Register ["+registerId+"] is inactive");
            throw new CustomException("INACTIVE_REGISTER", "Given RegisterId ["+registerId+"] is inactive");
        }
    }

    private void checkRegisterExistence(List<AttendanceRegister> attendanceRegisters,String registerId) {
        if (attendanceRegisters == null || attendanceRegisters.isEmpty()) {
            log.error("Register ["+registerId+"] does not exists");
            throw new CustomException("REGISTER_NOT_FOUND", "Given RegisterId ["+registerId+"] does not exists");
        }
    }

    private void validateTenantIdAssociationWithRegisterId(AttendanceRegister attendanceRegister,String tenantId) {
        if(!tenantId.equals(attendanceRegister.getTenantId())){
            log.error("TenantId ["+tenantId+"] is not associated with register ["+attendanceRegister.getId()+"]");
            throw new CustomException("INVALID_TENANTID", "TenantId ["+tenantId+"] is not associated with register ["+attendanceRegister.getId()+"]");
        }
    }

    private List<AttendanceRegister> fetchRegisterWithId(String registerId) {
        AttendanceRegisterSearchCriteria searchCriteria = AttendanceRegisterSearchCriteria
                .builder()
                .ids(Collections.singletonList(registerId))
                .build();
        return attendanceRegisterRepository.getRegister(searchCriteria);
    }

    private void validateAttendanceLogTimeWithRegisterStartEndDate(AttendanceRegister attendanceRegister,AttendanceLogRequest attendanceLogRequest) {
        Instant registerStartTime = Instant.ofEpochMilli(attendanceRegister.getStartDate().longValue());

        Instant registerEndTime = null;
        if(attendanceRegister.getEndDate() != null)
            registerEndTime = Instant.ofEpochMilli(attendanceRegister.getEndDate().longValue());

        List<AttendanceLog> attendanceLogs = attendanceLogRequest.getAttendance();

        if(registerEndTime != null){
            for(AttendanceLog attendanceLog : attendanceLogs){
                Instant instantAttendanceAttendeeLogTime = Instant.ofEpochMilli(attendanceLog.getTime().longValue());
                if(!(instantAttendanceAttendeeLogTime.compareTo(registerStartTime) >=0 && instantAttendanceAttendeeLogTime.compareTo(registerEndTime) <=0)){
                    log.error("Attendance time ["+instantAttendanceAttendeeLogTime+"] is invalid for register ["+attendanceRegister.getId()+"]");
                    throw new CustomException("INVALID_ATTENDANCE_TIME", "Attendance time ["+instantAttendanceAttendeeLogTime+"] is invalid for register ["+attendanceRegister.getId()+"]");
                }
            }
        }else{
            for(AttendanceLog attendanceLog : attendanceLogs){
                Instant instantAttendanceAttendeeLogTime = Instant.ofEpochMilli(attendanceLog.getTime().longValue());
                if(!(instantAttendanceAttendeeLogTime.compareTo(registerStartTime) >=0)){
                    log.error("Attendance time ["+instantAttendanceAttendeeLogTime+"] is invalid for register ["+attendanceRegister.getId()+"]");
                    throw new CustomException("INVALID_ATTENDANCE_TIME", "Attendance time ["+instantAttendanceAttendeeLogTime+"] is invalid for register ["+attendanceRegister.getId()+"]");
                }
            }
        }
    }
    private void validateDocumentIds(AttendanceLogRequest attendanceLogRequest) {
        if ("TRUE".equalsIgnoreCase(config.getDocumentIdVerificationRequired())) {
            //TODO
            // For now throwing exception. Later implementation will be done
            log.error("Document service not integrated yet");
            throw new CustomException("SERVICE_UNAVAILABLE", "Service not integrated yet");
        }
    }

    private void validateAttendanceLogIds(AttendanceLogRequest attendanceLogRequest) {
        String registerId = attendanceLogRequest.getAttendance().get(0).getRegisterId();
        List<AttendanceLog> attendance = attendanceLogRequest.getAttendance();
        List<String> providedAttendanceLogIds = attendance.stream().map(e -> String.valueOf(e.getId())).collect(Collectors.toList());
        List<AttendanceLog> fetchedAttendanceLogList = fetchAttendanceLogsByIds(providedAttendanceLogIds);
        Set<String> fetchedAttendanceLogIds = fetchedAttendanceLogList.stream().map(e -> String.valueOf(e.getId())).collect(Collectors.toSet());
        for (String providedAttendanceLogId : providedAttendanceLogIds) {
            if (!fetchedAttendanceLogIds.contains(providedAttendanceLogId)) {
                log.error("Provided attendance id ["+providedAttendanceLogId+"] is invalid for register ["+registerId+"]");
                throw new CustomException("ATTENDANCE_LOG", "Provided attendance id ["+providedAttendanceLogId+"] is invalid for register ["+registerId+"]");
            }
        }

        log.info("Attendance Log Ids are validated successfully for register ["+registerId+"]");
    }

    private List<AttendanceLog> fetchAttendanceLogsByIds(List<String> ids) {
        //AttendanceLogSearchCriteria searchCriteria = AttendanceLogSearchCriteria.builder().ids(ids).status(Status.ACTIVE).build();
        AttendanceLogSearchCriteria searchCriteria = AttendanceLogSearchCriteria.builder().ids(ids).build();
        return attendanceLogRepository.getAttendanceLogs(searchCriteria);
    }

    private void validateAttendees(AttendanceLogRequest attendanceLogRequest) {

        /*
            For now, we are validating attendees on below basis.
            1. Verify that each attendee is associated to given register.
            2. Make the entry in attendance log table only if attendee was active during the given attendance time.

            Future:
            Once Individual service will be available will integrate it for further validation.
         */


        if ("TRUE".equalsIgnoreCase(config.getIndividualServiceIntegrationRequired())) {
            //TODO
            // For now throwing exception. Since individual service is under discussion.
            log.error("Individual service integration is under development");
            throw new CustomException("INTEGRATION_UNDERDEVELOPMENT", "Individual service integration is under development");
        }

        // Fetch all attendees for given register_id.
        String registerId = attendanceLogRequest.getAttendance().get(0).getRegisterId();
        List<IndividualEntry> fetchAttendanceAttendeeLst = fetchAllAttendeesEnrolledInARegister(registerId);

        log.info("All attendees are fetched successfully for register ["+registerId+"]");

        // Convert the fetched Attendee List into a Map with individualId as key and corresponding Attendee list as value.
        Map<String, List<IndividualEntry>> attendanceAttendeeListMap = fetchAttendanceAttendeeLst
                .stream()
                .collect(Collectors.groupingBy(IndividualEntry::getIndividualId));

        // Identify unassociated(Attendees not associated with given register) and ineligible attendees
        identifyUnassociatedAndIneligibleAttendees(attendanceLogRequest, attendanceAttendeeListMap);
        log.info("Attendee validation is done for register ["+registerId+"]");
    }

    private void identifyUnassociatedAndIneligibleAttendees(AttendanceLogRequest attendanceLogRequest, Map<String, List<IndividualEntry>> attendanceAttendeeListMap) {
        List<String> unassociatedAttendees = new ArrayList<>();
        Set<String> eligibleAttendanceAttendeeIdSet = new HashSet<>();

        List<AttendanceLog> attendanceLogs = attendanceLogRequest.getAttendance();
        for (AttendanceLog attendanceLog : attendanceLogs) {
            String givenIndividualId = attendanceLog.getIndividualId();
            if (attendanceAttendeeListMap.containsKey(givenIndividualId)) {
                List<IndividualEntry> lst = attendanceAttendeeListMap.get(givenIndividualId);
                for (IndividualEntry attendee : lst) {
                    Instant instantAttendanceAttendeeLogTime = Instant.ofEpochMilli(attendanceLog.getTime().longValue());
                    Instant instantEnrollmentDate = Instant.ofEpochMilli(attendee.getEnrollmentDate().longValue());
                    if (attendee.getDenrollmentDate()==null) {
                        if (instantAttendanceAttendeeLogTime.compareTo(instantEnrollmentDate) >= 0) {
                            eligibleAttendanceAttendeeIdSet.add(attendee.getIndividualId());
                        }
                    } else {
                        Instant instantDenrollmentDate = Instant.ofEpochMilli(attendee.getDenrollmentDate().longValue());
                        if (instantAttendanceAttendeeLogTime.compareTo(instantEnrollmentDate) >= 0 && instantAttendanceAttendeeLogTime.compareTo(instantDenrollmentDate) <= 0) {
                            eligibleAttendanceAttendeeIdSet.add(attendee.getIndividualId());
                        }
                    }
                }
            } else {
                unassociatedAttendees.add(givenIndividualId);
            }

        }

        String registerId = attendanceLogRequest.getAttendance().get(0).getRegisterId();

        if (!unassociatedAttendees.isEmpty()) {
            log.error("Attendees are not enrolled against register ["+registerId+"]");
            throw new CustomException("UNENROLLED_ATTENDEES", "Attendees are not enrolled against register ["+registerId+"]");
        }

        //find ineligible list
        Set<String> inEligibleAttendanceAttendeeIdSet = new HashSet<>();
        for (AttendanceLog attendanceLog : attendanceLogs) {
            if (!eligibleAttendanceAttendeeIdSet.contains(attendanceLog.getIndividualId())) {
                inEligibleAttendanceAttendeeIdSet.add(attendanceLog.getIndividualId());
            }
        }

        if (!inEligibleAttendanceAttendeeIdSet.isEmpty()) {
            log.error("Attendees are ineligible for given date range for register ["+registerId+"]");
            throw new CustomException("INELIGIBLE_ATTENDEES", "Attendees are ineligible for given date range for register ["+registerId+"]");
        }
    }

    private List<IndividualEntry> fetchAllAttendeesEnrolledInARegister(String registerId) {
        AttendeeSearchCriteria searchCriteria = AttendeeSearchCriteria
                .builder()
                .registerIds(Collections.singletonList(registerId))
                .build();

        return attendanceAttendeeRepository.getAttendees(searchCriteria);
    }

    private void validateLoggedInUser(AttendanceLogRequest attendanceLogRequest) {
        /*
            For now, we are validating logged-in user on below basis.
            1. Logged-in user should be active user for provided register_id. Query eg_wms_attendance_staff table for same.

            Future
            Once Staff service will be available will integrate it for further validation.
         */

        if ("TRUE".equalsIgnoreCase(config.getStaffServiceIntegrationRequired())) {
            //TODO
            // For now throwing exception. Since Staff service is under development.
            log.error("Staff service integration is under development");
            throw new CustomException("INTEGRATION_UNDERDEVELOPMENT", "Staff service integration is under development");
        }

        String userUUID = attendanceLogRequest.getRequestInfo().getUserInfo().getUuid();
        String registerId = attendanceLogRequest.getAttendance().get(0).getRegisterId();
        String individualId = individualServiceUtil.getIndividualDetailsFromUserId(attendanceLogRequest.getRequestInfo().getUserInfo().getId(), attendanceLogRequest.getRequestInfo(), attendanceLogRequest.getAttendance().get(0).getTenantId()).get(0).getId();
        validateLoggedInUser(individualId, registerId);
        log.info("User ["+userUUID+"] validation is done for register ["+registerId+"]");
    }

    public void validateSearchAttendanceLogRequest(RequestInfoWrapper requestInfoWrapper, AttendanceLogSearchCriteria searchCriteria) {

        log.info("Validate attendance log search request");

        // Verify given parameters
        validateSearchAttendanceLogParameters(requestInfoWrapper, searchCriteria);

        // Fetch register for given Id
        List<AttendanceRegister> attendanceRegisters = fetchRegisterWithId(searchCriteria.getRegisterId());

        if (attendanceRegisters == null || attendanceRegisters.isEmpty()) {
            throw new CustomException("INVALID_REGISTERID", "Register Not found ");
        }

        // Verify TenantId association with register
        validateTenantIdAssociationWithRegisterId(attendanceRegisters.get(0), searchCriteria.getTenantId());

        // Verify the Logged-in user is associated to the given register.
        String individualId = individualServiceUtil.getIndividualDetailsFromUserId(requestInfoWrapper.getRequestInfo().getUserInfo().getId(), requestInfoWrapper.getRequestInfo(), searchCriteria.getTenantId()).get(0).getId();
        validateLoggedInUser(individualId, searchCriteria.getRegisterId());

        log.info("Attendance log search request validated successfully");
    }

    private void validateSearchAttendanceLogParameters(RequestInfoWrapper requestInfoWrapper, AttendanceLogSearchCriteria searchCriteria) {
        if (searchCriteria == null || requestInfoWrapper == null) {
            log.error("Attendance log search criteria and request info is mandatory");
            throw new CustomException("ATTENDANCE_LOG_SEARCH_REQUEST", "Attendance log search criteria and request info is mandatory");
        }

        Map<String, String> errorMap = new HashMap<>();

        validateRequestInfo(requestInfoWrapper.getRequestInfo(), errorMap);

        if (StringUtils.isBlank(searchCriteria.getTenantId())) {
            log.error("Attendance log search, Tenant is mandatory");
            throw new CustomException("TENANT_ID", "Tenant is mandatory");
        }
        if (StringUtils.isBlank(searchCriteria.getRegisterId())) {
            log.error("Attendance log search, RegisterId is mandatory");
            throw new CustomException("REGISTER_ID", "RegisterId is mandatory");
        }

        // Throw exception if required parameters are missing
        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);

        if (searchCriteria.getIndividualIds() != null && !searchCriteria.getIndividualIds().isEmpty() && searchCriteria.getIndividualIds().size() > 10) {
            log.error("Attendance log search, only 10 IndividualIds are allowed to search");
            throw new CustomException("INDIVIDUALIDS", "only 10 IndividualIds are allowed to search");
        }
    }



    private void validateLoggedInUser(String userUUID, String registerId) {
        StaffSearchCriteria searchCriteria = StaffSearchCriteria
                .builder()
                .individualIds(Collections.singletonList(userUUID))
                .registerIds(Collections.singletonList(registerId))
                .build();
        List<StaffPermission> attendanceStaff = attendanceStaffRepository.getActiveStaff(searchCriteria);
        if (attendanceStaff == null || attendanceStaff.isEmpty()) {
            log.error("User ["+userUUID+"] is not authorised for register ["+registerId+"]");
            throw new CustomException("UNAUTHORISED_USER", "User ["+userUUID+"] is not authorised for register ["+registerId+"]");
        }
    }

    private void validateRequestInfo(RequestInfo requestInfo, Map<String, String> errorMap) {
        if (requestInfo == null) {
            log.error("Request info is mandatory");
            throw new CustomException("REQUEST_INFO", "Request info is mandatory");
        }
        if (requestInfo.getUserInfo() == null) {
            log.error("UserInfo is mandatory");
            throw new CustomException("USERINFO", "UserInfo is mandatory");
        }
        if (requestInfo.getUserInfo() != null && StringUtils.isBlank(requestInfo.getUserInfo().getUuid())) {
            log.error("UUID is mandatory");
            throw new CustomException("USERINFO_UUID", "UUID is mandatory");
        }

        log.info("Request Info object validation done");
    }

    private void validateAttendanceLogRequest(AttendanceLogRequest attendanceLogRequest) {

        Map<String, String> errorMap = new HashMap<>();
        // Validate the Request Info object
        RequestInfo requestInfo = attendanceLogRequest.getRequestInfo();
        validateRequestInfo(requestInfo, errorMap);

        // Validate the Attendance Log parameters
        validateAttendanceLogParameters(attendanceLogRequest.getAttendance(), errorMap);

        // Throw exception if required parameters are missing
        if (!errorMap.isEmpty()){
            log.error("Attendance log request validation failed");
            throw new CustomException(errorMap);
        }
    }

    private void validateAttendanceLogParameters(List<AttendanceLog> attendance, Map<String, String> errorMap) {
        if (attendance == null || attendance.isEmpty()) {
            log.error("Attendance array is mandatory");
            throw new CustomException("ATTENDANCE", "Attendance array is mandatory");
        }

        for (AttendanceLog attendeeLog : attendance) {
            if (StringUtils.isBlank(attendeeLog.getTenantId())) {
                log.error("TenantId is mandatory");
                errorMap.put("ATTENDANCE.TENANTID", "TenantId is mandatory");
            }
            if (StringUtils.isBlank(attendeeLog.getRegisterId())) {
                log.error("Attendance registerid is mandatory");
                errorMap.put("ATTENDANCE.REGISTERID", "Attendance registerid is mandatory");
            }
            if (StringUtils.isBlank(attendeeLog.getIndividualId() )) {
                log.error("Attendance indidualid is mandatory");
                errorMap.put("ATTENDANCE.INDIVIDUALID", "Attendance indidualid is mandatory");
            }
            if (StringUtils.isBlank(attendeeLog.getType())) {
                log.error("Attendance type is mandatory");
                errorMap.put("ATTENDANCE.TYPE", "Attendance type is mandatory");
            }
            if (attendeeLog.getTime() == null) {
                log.error("Attendance time is mandatory");
                errorMap.put("ATTENDANCE.TIME", "Attendance time is mandatory");
            }
        }
    }
}
