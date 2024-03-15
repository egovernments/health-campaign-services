package org.egov.enrichment;

import digit.models.coremodels.IdGenerationResponse;
import digit.models.coremodels.IdResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.helper.AttendanceRegisterRequestBuilderTest;
import org.egov.repository.IdGenRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.AttendanceServiceUtil;
import org.egov.util.IndividualServiceUtil;
import org.egov.web.models.AttendanceRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class RegisterEnrichmentTest {

    @InjectMocks
    private RegisterEnrichment registerEnrichment;

    @Mock
    private AttendanceServiceConfiguration config;

    @Mock
    private IdGenRepository idGenRepository;

    @Mock
    private AttendanceServiceUtil attendanceServiceUtil;
    @Mock
    private IndividualServiceUtil individualServiceUtil;

    @BeforeEach
    void setupBeforeEach() {
        when(config.getIdgenAttendanceRegisterNumberName()).thenReturn("attendance.register.number");
       // when(config.getIdgenAttendanceRegisterNumberFormat()).thenReturn("WR/[fy:yyyy-yy]/[cy:MM]/[cy:dd]/[SEQ_ATTENDANCE_REGISTER_NUM]");
    }


    @DisplayName("Method enrichCreateAttendanceRegister: With IDGEN ERROR code")
    @Test
    public void enrichCreateAttendanceRegisterTest_1(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().withRequestInfo().addGoodRegister().build();

        List<IdResponse> idResponses = new ArrayList<>();
        IdGenerationResponse idGenerationResponse = IdGenerationResponse.builder().idResponses(idResponses).build();

        lenient().when(idGenRepository.getId(eq(attendanceRegisterRequest.getRequestInfo()), eq("pb"), eq("attendance.register.number"), eq(""), eq(1)))
                .thenReturn(idGenerationResponse);

        CustomException exception = assertThrows(CustomException.class,()->registerEnrichment.enrichRegisterOnCreate(attendanceRegisterRequest));
        assertTrue(exception.getCode().contentEquals("IDGEN ERROR"));
    }


//    @DisplayName("Method enrichCreateAttendanceRegister: With IDGEN ERROR code")
//    @Test
//    public void enrichCreateAttendanceRegisterTest_2(){
//        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().withRequestInfo().attendanceRegistersWithoutIdAuditDetailsAndNumber().build();
//
//        IdResponse idResponse = IdResponse.builder().id("WR/2022-23/01/05/01").build();
//        List<IdResponse> idResponses = new ArrayList<>();
//        idResponses.add(idResponse);
//        IdGenerationResponse idGenerationResponse = IdGenerationResponse.builder().idResponses(idResponses).build();
//
//        lenient().when(idGenRepository.getId(eq(attendanceRegisterRequest.getRequestInfo()), eq("pb"), eq("attendance.register.number"), eq(""), eq(1)))
//                .thenReturn(idGenerationResponse);
//
//        AuditDetails auditDetails = AuditDetailsTestBuilder.builder().withAuditDetails().build();
//        when(attendanceServiceUtil.getAuditDetails(attendanceRegisterRequest.getRequestInfo().getUserInfo().getUuid(),null,true)).thenReturn(auditDetails);
//        Individual dummyIndividual = Individual.builder().individualId(UUID.randomUUID().toString()).build();
//        when(individualServiceUtil.getIndividualDetails(any(), any(), any())).thenReturn(Collections.singletonList(dummyIndividual));
//
//        registerEnrichment.enrichRegisterOnCreate(attendanceRegisterRequest);
//
//        assertTrue(attendanceRegisterRequest.getAttendanceRegister().get(0).getId()!=null);
//
//    }

}
