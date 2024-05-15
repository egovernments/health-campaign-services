package org.egov.service;

import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.util.IndividualServiceUtil;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterSearchCriteria;
import org.egov.web.models.Organisation.ContactDetails;
import org.egov.web.models.Organisation.OrgContactUpdateDiff;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.StaffPermissionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class OrganisationContactDetailsStaffUpdateService {

    @Autowired
    private AttendanceRegisterService attendanceRegisterService;
    @Autowired
    private StaffService staffService;
    @Autowired
    private IndividualServiceUtil individualServiceUtil;

    @Autowired
    private AttendanceServiceConfiguration configuration;

    public void updateStaffPermissionsForContactDetails(OrgContactUpdateDiff orgContactUpdateDiff) {
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(orgContactUpdateDiff.getRequestInfo()).build();
        String tenantId = orgContactUpdateDiff.getTenantId();
        List<ContactDetails> oldContacts = orgContactUpdateDiff.getOldContacts();

        for(ContactDetails oldContact : oldContacts) {
            AttendanceRegisterSearchCriteria attendanceRegisterSearchCriteria =
                    AttendanceRegisterSearchCriteria.builder().tenantId(tenantId).staffId(oldContact.getIndividualId()).limit(configuration.getAttendanceRegisterMaxLimit()).build();
            List<AttendanceRegister> attendanceRegisterList = attendanceRegisterService.searchAttendanceRegister(requestInfoWrapper, attendanceRegisterSearchCriteria);
            if(CollectionUtils.isEmpty(attendanceRegisterList)) {
                try {
                    String userUuid = individualServiceUtil.getIndividualDetails(Collections.singletonList(oldContact.getIndividualId()), requestInfoWrapper.getRequestInfo(), tenantId).get(0).getUserUuid();
                    attendanceRegisterSearchCriteria = AttendanceRegisterSearchCriteria.builder().tenantId(tenantId).staffId(userUuid).limit(configuration.getAttendanceRegisterMaxLimit()).build();
                    attendanceRegisterList = attendanceRegisterService.searchAttendanceRegister(requestInfoWrapper, attendanceRegisterSearchCriteria);
                }catch (Exception e){
                    log.error(e.toString());
                }
            }
            List<ContactDetails> newContacts = orgContactUpdateDiff.getNewContacts();
            grantPermission(attendanceRegisterList, newContacts, orgContactUpdateDiff.getRequestInfo());
            revokePermission(attendanceRegisterList, oldContact.getIndividualId(), orgContactUpdateDiff.getRequestInfo());
        }
    }

    private void revokePermission(List<AttendanceRegister> attendanceRegisters, String individualOrUserId, RequestInfo requestInfo) {
        if(attendanceRegisters.isEmpty()) {
            log.info("No attendance registers to revoke permissions on");
            return;
        }
        List<StaffPermission> staffPermissionList = new ArrayList<>();
        for(AttendanceRegister attendanceRegister : attendanceRegisters) {
            String tenantId = attendanceRegister.getTenantId();
            StaffPermission staffPermission = StaffPermission.builder()
                    .tenantId(tenantId).registerId(attendanceRegister.getId()).userId(individualOrUserId).build();
            staffPermissionList.add(staffPermission);
        }
        StaffPermissionRequest staffPermissionRequest = StaffPermissionRequest.builder()
                .requestInfo(requestInfo).staff(staffPermissionList).build();
        staffService.deleteAttendanceStaff(staffPermissionRequest);
        log.info("Revoked permission for: " + individualOrUserId + " on " + attendanceRegisters.size() + " registers.");
    }

    private void grantPermission(List<AttendanceRegister> attendanceRegisters, List<ContactDetails> newContacts, RequestInfo requestInfo) {
        if(attendanceRegisters.isEmpty()) {
            log.info("No attendance registers to grant permission on");
            return;
        }
        List<StaffPermission> staffPermissionList = new ArrayList<>();
        for(AttendanceRegister attendanceRegister : attendanceRegisters) {
            String tenantId = attendanceRegister.getTenantId();
            for(ContactDetails newContact : newContacts) {
                StaffPermission staffPermission = StaffPermission.builder().tenantId(tenantId)
                        .registerId(attendanceRegister.getId()).userId(newContact.getIndividualId()).build();
                staffPermissionList.add(staffPermission);
            }
        }
        StaffPermissionRequest staffPermissionRequest = StaffPermissionRequest.builder()
                .requestInfo(requestInfo).staff(staffPermissionList).build();
        staffService.createAttendanceStaff(staffPermissionRequest, true);
        log.info("Granted permission on " + attendanceRegisters.size() + " registers for " + newContacts.size() + " new contacts.");
    }

}
