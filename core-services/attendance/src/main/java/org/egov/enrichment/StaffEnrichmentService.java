package org.egov.enrichment;

import digit.models.coremodels.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.util.AttendanceServiceUtil;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.StaffPermissionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class StaffEnrichmentService {

    @Autowired
    private AttendanceServiceUtil attendanceServiceUtil;

    public void enrichStaffPermissionOnCreate(StaffPermissionRequest request) {
        RequestInfo requestInfo = request.getRequestInfo();
        List<StaffPermission> staffPermissionListFromRequest = request.getStaff();

        for (StaffPermission staffPermissionFromRequest : staffPermissionListFromRequest) {
            AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), staffPermissionFromRequest.getAuditDetails(), true);
            staffPermissionFromRequest.setAuditDetails(auditDetails);
            staffPermissionFromRequest.setId(UUID.randomUUID().toString());
            BigDecimal enrollmentDate = new BigDecimal(System.currentTimeMillis());
            staffPermissionFromRequest.setEnrollmentDate(enrollmentDate);
        }
    }

    public void enrichStaffPermissionOnDelete(StaffPermissionRequest request, List<StaffPermission> staffPermissionListFromDB) {
        RequestInfo requestInfo = request.getRequestInfo();
        List<StaffPermission> staffPermissionListFromRequest = request.getStaff();

        for (StaffPermission staffPermissionFromRequest : staffPermissionListFromRequest) {
            for (StaffPermission staffPermissionFromDB : staffPermissionListFromDB) {
                if (staffPermissionFromDB.getUserId().equals(staffPermissionFromRequest.getUserId())
                        && staffPermissionFromDB.getRegisterId().equals(staffPermissionFromRequest.getRegisterId())
                        && staffPermissionFromDB.getDenrollmentDate() == null) {
                    staffPermissionFromRequest.setId(staffPermissionFromDB.getId());
                    staffPermissionFromRequest.setEnrollmentDate(staffPermissionFromDB.getEnrollmentDate());

                    AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), staffPermissionFromDB.getAuditDetails(), false);

                    staffPermissionFromRequest.setAuditDetails(auditDetails);

                    BigDecimal deenrollmentDate = new BigDecimal(System.currentTimeMillis());
                    staffPermissionFromRequest.setDenrollmentDate(deenrollmentDate);
                }
            }
        }


    }


}
