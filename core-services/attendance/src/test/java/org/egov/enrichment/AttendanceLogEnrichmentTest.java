package org.egov.enrichment;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.helper.AttendanceLogRequestTestBuilder;
import org.egov.util.AttendanceServiceUtil;
import org.egov.web.models.AttendanceLogRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class AttendanceLogEnrichmentTest {

    @InjectMocks
    private AttendanceLogEnrichment attendanceLogEnrichment;

    @Mock
    private AttendanceServiceUtil attendanceServiceUtil;

    @DisplayName("Method validateAttendanceLogRequest: With good request")
    @Test
    public void enrichAttendanceLogCreateRequestTest_1(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addAttendanceLogWithoutIdAndAuditDetails().build();
        String byUser = attendanceLogRequest.getRequestInfo().getUserInfo().getUuid();
        Long time = System.currentTimeMillis();
        AuditDetails auditDetails = AuditDetails.builder().createdBy(byUser).lastModifiedBy(byUser).createdTime(time).lastModifiedTime(time).build();
        lenient().when(attendanceServiceUtil.getAuditDetails(any(String.class),eq(null),eq(true))).thenReturn(auditDetails);
        attendanceLogEnrichment.enrichAttendanceLogCreateRequest(attendanceLogRequest);
        assertNotNull(attendanceLogRequest.getAttendance().get(0).getId());
        assertNotNull(attendanceLogRequest.getAttendance().get(0).getAuditDetails());
        assertNotNull(attendanceLogRequest.getAttendance().get(0).getDocumentIds().get(0).getId());
    }
}
