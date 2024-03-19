package org.egov.enrichment;

import digit.models.coremodels.AuditDetails;
import digit.models.coremodels.IdResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.Individual;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.repository.IdGenRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.AttendanceServiceUtil;
import org.egov.util.IndividualServiceUtil;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterRequest;
import org.egov.web.models.AttendanceRegisterSearchCriteria;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.StaffPermissionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RegisterEnrichment {

    @Autowired
    private AttendanceServiceUtil attendanceServiceUtil;
    @Autowired
    private IdGenRepository idGenRepository;
    @Autowired
    private AttendanceServiceConfiguration config;
    @Autowired
    private IndividualServiceUtil individualServiceUtil;
    @Autowired
    private MultiStateInstanceUtil multiStateInstanceUtil;

    /* Enrich Attendance Register on Create Request */
    public void enrichRegisterOnCreate(AttendanceRegisterRequest attendanceRegisterRequest) {
        RequestInfo requestInfo = attendanceRegisterRequest.getRequestInfo();
        List<AttendanceRegister> attendanceRegisters = attendanceRegisterRequest.getAttendanceRegister();

        String rootTenantId = attendanceRegisters.get(0).getTenantId().split("\\.")[0];

        //Get Register Numbers from IdGen Service for number of registers present in AttendanceRegisters
        List<String> registerNumbers = getIdList(requestInfo, rootTenantId
                , config.getIdgenAttendanceRegisterNumberName(), "", attendanceRegisters.size()); //idFormat will be fetched by idGen service

        for (int i = 0; i < attendanceRegisters.size(); i++) {

            if (registerNumbers != null && !registerNumbers.isEmpty()) {
                attendanceRegisters.get(i).setRegisterNumber(registerNumbers.get(i));
                log.info("Register number " + registerNumbers.get(i) + " assigned to register " + attendanceRegisters.get(i));
            } else {
                log.error("Error occurred while generating attendance register numbers from IdGen service");
                throw new CustomException("ATTENDANCE_REGISTER_NUMBER_NOT_GENERATED","Error occurred while generating attendance register numbers from IdGen service");
            }

            //Enrich attendance register id and audit details
            attendanceRegisters.get(i).setId(UUID.randomUUID().toString());
            log.info("Attendance register assigned with register Id " + attendanceRegisters.get(i).getId());
            AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), null, true);
            attendanceRegisters.get(i).setAuditDetails(auditDetails);
            log.info("Enriched register " + attendanceRegisters.get(i).getId() + " with Audit details");
            // User who creates the register, by default gets enrolled as the first staff for that register.
            enrichRegisterFirstStaff(attendanceRegisters.get(i), requestInfo, auditDetails);
        }
    }

    /* Enrich first staff details while creating register*/
    private void enrichRegisterFirstStaff(AttendanceRegister attendanceRegister, RequestInfo requestInfo, AuditDetails auditDetails) {
        String tenantId = attendanceRegister.getTenantId();
        Long userid = requestInfo.getUserInfo().getId();
        List<Individual> individualList = individualServiceUtil.getIndividualDetailsFromUserId(userid, requestInfo, multiStateInstanceUtil.getStateLevelTenant(tenantId));
        String individualId = individualList.get(0).getId();

        StaffPermission staffPermission = StaffPermission.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(attendanceRegister.getTenantId())
                .registerId(attendanceRegister.getId())
                .userId(individualId)
                .enrollmentDate(new BigDecimal(System.currentTimeMillis()))
                .auditDetails(auditDetails)
                .build();
        attendanceRegister.setStaff(Collections.singletonList(staffPermission));
        log.info("First staff for attendance register is added in attendance register");
        log.info("The user " + requestInfo.getUserInfo().getUuid() + " is addedd as staff for the attendance register + " + staffPermission.getRegisterId());
    }

    /* Enrich Attendance Register on Update Request */
    public void enrichRegisterOnUpdate(AttendanceRegisterRequest attendanceRegisterRequest, List<AttendanceRegister> attendanceRegistersListFromDB) {
        RequestInfo requestInfo = attendanceRegisterRequest.getRequestInfo();
        List<AttendanceRegister> attendanceRegistersListInUpdateReq = attendanceRegisterRequest.getAttendanceRegister();

        for (AttendanceRegister attendanceRegisterInUpdateReq : attendanceRegistersListInUpdateReq) {
            log.info("Enriching register " + attendanceRegisterInUpdateReq.getId());
            String registerId = String.valueOf(attendanceRegisterInUpdateReq.getId());
            AttendanceRegister attendanceRegisterFromDB = attendanceRegistersListFromDB.stream().filter(ar -> registerId.equals(String.valueOf(ar.getId()))).findFirst().orElse(null);

            // Set read only values i.e register number, attendees, staff to the attendance register update request as in attendance register from DB
            attendanceRegisterInUpdateReq.setRegisterNumber(attendanceRegisterFromDB.getRegisterNumber());
            attendanceRegisterInUpdateReq.setAttendees(attendanceRegisterFromDB.getAttendees());
            attendanceRegisterInUpdateReq.setStaff(attendanceRegisterFromDB.getStaff());
            log.info("Update attendance register request for register " + attendanceRegisterInUpdateReq.getId() + " enriched with register number, attendees and staff");

            // Set audit details for register update request
            attendanceRegisterInUpdateReq.setAuditDetails(attendanceRegisterFromDB.getAuditDetails());
            AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), attendanceRegisterFromDB.getAuditDetails(), false);
            attendanceRegisterInUpdateReq.setAuditDetails(auditDetails);
            log.info("Update attendance register request for register " + attendanceRegisterInUpdateReq.getId() + " enriched with audit details");
        }
    }

    /* Adds staff details to the associated attendance register */
    public void enrichStaffInRegister(List<AttendanceRegister> attendanceRegisters, StaffPermissionRequest staffPermissionResponse) {
        for (AttendanceRegister attendanceRegister : attendanceRegisters) {
            String registerId = String.valueOf(attendanceRegister.getId());
            List<StaffPermission> staff = staffPermissionResponse.getStaff().stream().filter(st -> registerId.equals(st.getRegisterId())).collect(Collectors.toList());
            attendanceRegister.setStaff(staff);
            log.info("Created staff details associated with attendance register " + attendanceRegister.getId() + " in create request");
        }
    }

    /* Get id list from IdGen service */
    private List<String> getIdList(RequestInfo requestInfo, String tenantId, String idKey,
                                   String idformat, int count) {
        List<IdResponse> idResponses = idGenRepository.getId(requestInfo, tenantId, idKey, idformat, count).getIdResponses();

        if (CollectionUtils.isEmpty(idResponses)) {
            log.error("No ids returned from idgen Service");
            throw new CustomException("IDGEN ERROR", "No ids returned from idgen Service");
        }

        return idResponses.stream()
                .map(IdResponse::getId).collect(Collectors.toList());
    }


    public void enrichSearchRegisterRequest(RequestInfo requestInfo, AttendanceRegisterSearchCriteria searchCriteria) {

        if (searchCriteria.getLimit() == null)
            searchCriteria.setLimit(config.getAttendanceRegisterDefaultLimit());

        if (searchCriteria.getOffset() == null)
            searchCriteria.setOffset(config.getAttendanceRegisterDefaultOffset());

        if (searchCriteria.getLimit() != null && searchCriteria.getLimit() > config.getAttendanceRegisterMaxLimit())
            searchCriteria.setLimit(config.getAttendanceRegisterMaxLimit());

    }
}
