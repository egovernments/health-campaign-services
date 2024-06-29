package org.egov.validator;

import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.repository.RegisterRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.IndividualServiceUtil;
import org.egov.util.MDMSUtils;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterRequest;
import org.egov.web.models.AttendanceRegisterSearchCriteria;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.Status;
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
public class AttendanceServiceValidator {

    @Autowired
    private MDMSUtils mdmsUtils;

    @Autowired
    private RegisterRepository registerRepository;
    @Autowired
    private IndividualServiceUtil individualServiceUtil;

    /* Validates create Attendance Register request body */
    public void validateCreateAttendanceRegister(AttendanceRegisterRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        List<AttendanceRegister> attendanceRegisters = request.getAttendanceRegister();
        RequestInfo requestInfo = request.getRequestInfo();

        //Verify if RequestInfo and UserInfo is present
        validateRequestInfo(requestInfo, errorMap);
        log.info("Request Info validated for attendance create request");

        //Verify if attendance register request and mandatory fields are present
        validateAttendanceRegisterRequest(attendanceRegisters, errorMap);
        log.info("Attendance registers validated for create request");

        //Verify referenceId and ServiceCode are present
        validateReferenceIdAndServiceCodeParams(attendanceRegisters, errorMap);
        log.info("Attendance registers referenceId and ServiceCode are validated");

        String tenantId = attendanceRegisters.get(0).getTenantId();
        String rootTenantId = tenantId.split("\\.")[0];

        //Get MDMS data using create attendance register request and tenantId
        Object mdmsData = mdmsUtils.mDMSCall(requestInfo, rootTenantId);
        validateMDMSData(attendanceRegisters, mdmsData, errorMap);
        log.info("Request data validated with MDMS");

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);

        //Verify that active attendance register is not already present for provided tenantId, referenceId and serviceCode
        validateAttendanceRegisterAgainstDB(attendanceRegisters);
        log.info("Attendance registers validated against DB");
    }

    private void validateAttendanceRegisterAgainstDB(List<AttendanceRegister> attendanceRegisters) {
        for (AttendanceRegister attendanceRegister: attendanceRegisters) {
             String tenantId = attendanceRegister.getTenantId();
             String referenceId = attendanceRegister.getReferenceId();
             String serviceCode = attendanceRegister.getServiceCode();

             AttendanceRegisterSearchCriteria attendanceRegisterSearchCriteria = AttendanceRegisterSearchCriteria.builder()
                    .tenantId(tenantId)
                    .status(Status.ACTIVE)
                    .referenceId(referenceId)
                    .serviceCode(serviceCode)
                    .build();
             List<AttendanceRegister> registers = registerRepository.getRegister(attendanceRegisterSearchCriteria);
             if(!registers.isEmpty()){
                 log.error("Attendance register exists for provided referenceId ["+referenceId+"] and serviceCode ["+serviceCode+"]");
                 throw new CustomException("REGISTER_ALREADY_EXISTS", "Register exists for provided referenceId ["+referenceId+"] and serviceCode ["+serviceCode+"]");
             }
        }
    }

    /* Validates Update Attendance register request body */
    public void validateUpdateAttendanceRegisterRequest(AttendanceRegisterRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        List<AttendanceRegister> attendanceRegisters = request.getAttendanceRegister();
        RequestInfo requestInfo = request.getRequestInfo();

        //Verify if RequestInfo and UserInfo is present
        validateRequestInfo(requestInfo, errorMap);
        log.info("Request Info validated for attendance update request");
        //Verify attendance register request and if mandatory fields are present
        validateAttendanceRegisterRequest(attendanceRegisters, errorMap);
        log.info("Attendance registers validated for update request");

        for (AttendanceRegister attendanceRegister: attendanceRegisters) {
            if (StringUtils.isBlank(attendanceRegister.getId())) {
                log.error("Attendance register id is mandatory in register update request");
                errorMap.put("ATTENDANCE_REGISTER_ID", "Attendance register id is mandatory");
            }
        }

        String tenantId = attendanceRegisters.get(0).getTenantId();
        String rootTenantId = tenantId.split("\\.")[0];

        //Get MDMS data using create attendance register request and tenantId
        Object mdmsData = mdmsUtils.mDMSCall(requestInfo, rootTenantId);
        validateMDMSData(attendanceRegisters, mdmsData, errorMap);
        log.info("Request data validated with MDMS");

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    /* Validates attendance register data in update request against attendance register data fetched from database */
    public void validateUpdateAgainstDB(AttendanceRegisterRequest attendanceRegisterRequest, List<AttendanceRegister> attendanceRegistersFromDB) {
        if (CollectionUtils.isEmpty(attendanceRegistersFromDB)) {
            log.error("The record that you are trying to update does not exists in the system");
            throw new CustomException("INVALID_REGISTER_MODIFY", "The record that you are trying to update does not exists in the system");
        }

        for (AttendanceRegister registerFromRequest: attendanceRegisterRequest.getAttendanceRegister()) {

            AttendanceRegister registerFromDB = attendanceRegistersFromDB.stream().filter(ar -> ar.getId().equals(registerFromRequest.getId())).findFirst().orElse(null);
            if (registerFromDB == null) {
                log.error("The register Id " + registerFromRequest.getId() + " that you are trying to update does not exists in the system");
                throw new CustomException("INVALID_REGISTER_MODIFY", "The register Id " + registerFromRequest.getId() + " that you are trying to update does not exists in the system");
            }

            // If the user who is trying to update the register is not associated with the register, throw error that the user does not have permission to modify the attendance register
            if (registerFromDB.getStaff() != null) {
                Set<String> staffUserIdsFromDB = registerFromDB.getStaff().stream().map(StaffPermission:: getUserId).collect(Collectors.toSet());
                String individualId = individualServiceUtil.getIndividualDetailsFromUserId(attendanceRegisterRequest.getRequestInfo().getUserInfo().getId(),attendanceRegisterRequest.getRequestInfo(), registerFromRequest.getTenantId()).get(0).getId();
                if (!staffUserIdsFromDB.contains(individualId)) {
                    log.error("The user " + attendanceRegisterRequest.getRequestInfo().getUserInfo().getUuid() + " does not have permission to modify the register " + registerFromDB.getId());
                    throw new CustomException("INVALID_REGISTER_MODIFY", "The user " + attendanceRegisterRequest.getRequestInfo().getUserInfo().getUuid() + " does not have permission to modify the register " + registerFromDB.getId());
                }
            } else {
                log.error("The user " + attendanceRegisterRequest.getRequestInfo().getUserInfo().getUuid() + " does not have permission to modify the register " + registerFromDB.getId());
                throw new CustomException("INVALID_REGISTER_MODIFY", "The user " + attendanceRegisterRequest.getRequestInfo().getUserInfo().getUuid() + " does not have permission to modify the register " + registerFromDB.getId());
            }
        }
    }

    /* Validates Request Info and User Info */
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
    }

    private void validateReferenceIdAndServiceCodeParams(List<AttendanceRegister> attendanceRegisters, Map<String, String> errorMap) {
        for (int i = 0; i < attendanceRegisters.size(); i++) {
            if (StringUtils.isBlank(attendanceRegisters.get(i).getReferenceId())) {
                log.error("ReferenceId is mandatory");
                errorMap.put("REFERENCE_ID", "ReferenceId is mandatory");
            }

            if (StringUtils.isBlank(attendanceRegisters.get(i).getServiceCode())) {
                log.error("ServiceCode is mandatory");
                errorMap.put("SERVICE_CODE", "ServiceCode is mandatory");
            }
        }
    }

    /* Validates Attendance register request body for create and update apis */
    private void validateAttendanceRegisterRequest(List<AttendanceRegister> attendanceRegisters, Map<String, String> errorMap) {
        if (attendanceRegisters == null || attendanceRegisters.size() == 0) {
            log.error("Attendance Register is mandatory");
            throw new CustomException("ATTENDANCE_REGISTER", "Attendance Register is mandatory");
        }

        for (int i = 0; i < attendanceRegisters.size(); i++) {
            if (attendanceRegisters.get(i) == null) {
                log.error("Attendance Register is mandatory");
                throw new CustomException("ATTENDANCE_REGISTER", "Attendance Register is mandatory");
            }
            if (StringUtils.isBlank(attendanceRegisters.get(i).getTenantId())) {
                log.error("Tenant is mandatory");
                throw new CustomException("TENANT_ID", "Tenant is mandatory");
            }
            if (StringUtils.isBlank(attendanceRegisters.get(i).getName())) {
                log.error("Name is mandatory");
                errorMap.put("NAME", "Name is mandatory");
            }

            if (attendanceRegisters.get(i).getStartDate() == null ||
                    (attendanceRegisters.get(i).getStartDate() != null && attendanceRegisters.get(i).getStartDate().compareTo(BigDecimal.ZERO) == 0)) {
                log.error("Start date is mandatory for attendance register " + attendanceRegisters.get(i).getName());
                throw new CustomException("START_DATE", "Start date is mandatory");
            }
            if (attendanceRegisters.get(i).getStartDate().compareTo(BigDecimal.ZERO) < 0) {
                log.error("Start date is less than zero " + attendanceRegisters.get(i).getName());
                throw new CustomException("START_DATE", "Start date should be valid");
            }
            if (attendanceRegisters.get(i).getEndDate() != null && attendanceRegisters.get(i).getStartDate().compareTo(attendanceRegisters.get(i).getEndDate()) > 0) {
                log.error("Start date should be less than end date for attendance register " + attendanceRegisters.get(i).getName());
                errorMap.put("DATE", "Start date should be less than end date");
            }
            if (!attendanceRegisters.get(i).getTenantId().equals(attendanceRegisters.get(0).getTenantId())) {
                log.error("All registers must have same tenant Id. Please create new request for different tenant id");
                throw new CustomException("MULTIPLE_TENANTS", "All registers must have same tenant Id. Please create new request for different tenant id");
            }
        }
    }

    private void validateMDMSData(List<AttendanceRegister> attendanceRegisters, Object mdmsData, Map<String, String> errorMap) {

        final String jsonPathForTenants = "$.MdmsRes." + MDMS_TENANT_MODULE_NAME + "." + MASTER_TENANTS + ".*";

        List<Object> tenantRes = null;
        try {
            tenantRes = JsonPath.read(mdmsData, jsonPathForTenants);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
        }

        if (CollectionUtils.isEmpty(tenantRes)){
            log.error("The tenant: " + attendanceRegisters.get(0).getTenantId() + " is not present in MDMS");
            errorMap.put("INVALID_TENANT", "The tenant: " + attendanceRegisters.get(0).getTenantId() + " is not present in MDMS");
        }
    }

    public void validateSearchRegisterRequest(RequestInfoWrapper requestInfoWrapper, AttendanceRegisterSearchCriteria searchCriteria) {
        if (searchCriteria == null || requestInfoWrapper == null) {
            log.error("Register search criteria request is mandatory");
            throw new CustomException("REGISTER_SEARCH_CRITERIA_REQUEST", "Register search criteria request is mandatory");
        }

        Map<String, String> errorMap = new HashMap<>();

        validateRequestInfo(requestInfoWrapper.getRequestInfo(),errorMap);

        if (StringUtils.isBlank(searchCriteria.getTenantId())) {
            log.error("Tenant is mandatory");
            throw new CustomException("TENANT_ID", "Tenant is mandatory");
        }

        // Throw exception if required parameters are missing
        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    public void validateRegisterAgainstDB(List<String> registerIds, List<AttendanceRegister> attendanceRegisterListFromDB, String tenantId) {

        Set<String> uniqueRegisterIdsFromRequest = new HashSet<>(registerIds);

        Set<String> uniqueRegisterIdsFromDB = attendanceRegisterListFromDB.stream()
                .map(register -> register.getId()).collect(Collectors.toSet());

        //check if all register ids from request exist in db
        for (String idFromRequest : uniqueRegisterIdsFromRequest) {
            if (!uniqueRegisterIdsFromDB.contains(idFromRequest)) {
                log.error("Attendance Registers with register id : " + idFromRequest + " does not exist for tenantId");
                throw new CustomException("REGISTER_ID", "Attendance Registers with register id : " + idFromRequest + " does not exist for tenantId");
            }
        }

    }
}
