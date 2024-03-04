package org.egov.enrichment;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.util.AttendanceServiceUtil;
import org.egov.web.models.AttendanceLog;
import org.egov.web.models.AttendanceLogRequest;
import org.egov.web.models.AttendanceLogSearchCriteria;
import org.egov.web.models.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class AttendanceLogEnrichment {
    @Autowired
    private AttendanceServiceUtil attendanceServiceUtil;
    @Autowired
    private AttendanceServiceConfiguration config;

    public void enrichAttendanceLogCreateRequest(AttendanceLogRequest attendanceLogRequest) {
        String registerId = attendanceLogRequest.getAttendance().get(0).getRegisterId();
        log.info("Enriching attendance log create request for register ["+registerId+"]");
        List<AttendanceLog> attendanceLogs = attendanceLogRequest.getAttendance();
        String byUser = attendanceLogRequest.getRequestInfo().getUserInfo().getUuid();
        AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(byUser, null, true);
        for (AttendanceLog attendanceLog : attendanceLogs) {
            attendanceLog.setAuditDetails(auditDetails);
            String attendanceLogId = String.valueOf(UUID.randomUUID());
            attendanceLog.setId(attendanceLogId);
            List<Document> documentIds = attendanceLog.getDocumentIds();
            for (Document documentId : documentIds) {
                documentId.setId(String.valueOf(UUID.randomUUID()));
            }
        }
        log.info("Enriched attendance log create request for register ["+registerId+"]");
    }

    public void enrichAttendanceLogUpdateRequest(AttendanceLogRequest attendanceLogRequest) {
        String registerId = attendanceLogRequest.getAttendance().get(0).getRegisterId();
        log.info("Enriching attendance log update request for register ["+registerId+"]");
        List<AttendanceLog> attendanceLogs = attendanceLogRequest.getAttendance();
        String byUser = attendanceLogRequest.getRequestInfo().getUserInfo().getUuid();
        for (AttendanceLog attendanceLog : attendanceLogs) {
            AuditDetails auditDetails = attendanceServiceUtil.getAuditDetails(byUser, attendanceLog.getAuditDetails(), false);
            attendanceLog.setAuditDetails(auditDetails);
            // enrich the documentId if not present
            List<Document> documentIds = attendanceLog.getDocumentIds();
            for (Document documentId : documentIds) {
                if (documentId.getId() == null) {
                    documentId.setId(String.valueOf(UUID.randomUUID()));
                }
            }
        }

        log.info("Enriched attendance log update request for register ["+registerId+"]");
    }

    public void enrichAttendanceLogSearchRequest(RequestInfo requestInfo, AttendanceLogSearchCriteria searchCriteria) {

//        if (searchCriteria.getLimit() == null)
//            searchCriteria.setLimit(config.getAttendanceLogDefaultLimit());
//
//        if (searchCriteria.getOffset() == null)
//            searchCriteria.setOffset(config.getAttendanceLogDefaultOffset());
//
//        if (searchCriteria.getLimit() != null && searchCriteria.getLimit() > config.getAttendanceLogMaxLimit())
//            searchCriteria.setLimit(config.getAttendanceLogMaxLimit());

    }
}
