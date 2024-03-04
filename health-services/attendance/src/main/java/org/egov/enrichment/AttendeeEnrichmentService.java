package org.egov.enrichment;

import digit.models.coremodels.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.util.AttendanceServiceUtil;
import org.egov.web.models.AttendeeCreateRequest;
import org.egov.web.models.AttendeeDeleteRequest;
import org.egov.web.models.IndividualEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AttendeeEnrichmentService {

    @Autowired
    private AttendanceServiceUtil attendanceServiceUtil;

    public void enrichAttendeeOnCreate(AttendeeCreateRequest attendeeCreateRequest) {
        RequestInfo requestInfo = attendeeCreateRequest.getRequestInfo();
        List<IndividualEntry> attendeeListFromRequest = attendeeCreateRequest.getAttendees();

        for (IndividualEntry attendee : attendeeListFromRequest) {
            AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), attendee.getAuditDetails(), true);
            attendee.setAuditDetails(auditDetails);
            attendee.setId(UUID.randomUUID().toString());
            attendee.setDenrollmentDate(null);
            if (attendee.getEnrollmentDate() == null) {
                BigDecimal enrollmentDate = new BigDecimal(System.currentTimeMillis());
                attendee.setEnrollmentDate(enrollmentDate);
            }

        }
    }

    public void enrichAttendeeOnDelete(AttendeeDeleteRequest attendeeDeleteRequest, List<IndividualEntry> attendeesFromDB) {
        RequestInfo requestInfo = attendeeDeleteRequest.getRequestInfo();
        List<IndividualEntry> attendeesListFromRequest = attendeeDeleteRequest.getAttendees();

        for (IndividualEntry attendee : attendeesListFromRequest) {
            for (IndividualEntry attendeeFromDB : attendeesFromDB) {
                if (attendeeFromDB.getIndividualId().equals(attendee.getIndividualId())
                        && attendeeFromDB.getRegisterId().equals(attendee.getRegisterId())) {

                    attendee.setId(attendeeFromDB.getId());
                    attendee.setEnrollmentDate(attendeeFromDB.getEnrollmentDate());

                    AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), attendeeFromDB.getAuditDetails(), false);
                    attendee.setAuditDetails(auditDetails);


                    if (attendee.getDenrollmentDate() == null) {
                        BigDecimal deEnrollmentDate = new BigDecimal(System.currentTimeMillis());
                        attendee.setDenrollmentDate(deEnrollmentDate);
                    }
                }
            }
        }


    }
}
