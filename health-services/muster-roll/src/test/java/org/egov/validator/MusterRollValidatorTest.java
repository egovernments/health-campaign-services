package org.egov.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.helper.MusterRollRequestBuilderTest;
import org.egov.repository.MusterRollRepository;
import org.egov.service.CalculationService;
import org.egov.tracer.model.CustomException;
import org.egov.util.MdmsUtil;
import org.egov.util.MusterRollServiceUtil;
import org.egov.web.models.AttendanceLogResponse;
import org.egov.web.models.AttendanceRegisterResponse;
import org.egov.web.models.MusterRollRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@Slf4j
class MusterRollValidatorTest {

    @InjectMocks
    private MusterRollValidator musterRollValidator;
    @Mock
    private MusterRollServiceConfiguration serviceConfiguration;
    @Mock
    private MdmsUtil mdmsUtils;
    @Mock
    private RestTemplate restTemplate;


    @BeforeEach
    void setUp() throws Exception {
        //MOCK MDMS Response
        Object mdmsResponse = MusterRollRequestBuilderTest.getTenantMdmsResponse();
        lenient().when(mdmsUtils.mDMSCall(any(MusterRollRequest.class),
                any(String.class))).thenReturn(mdmsResponse);
        lenient().when(serviceConfiguration.getTimeZone()).thenReturn("Asia/Kolkata");

    }

    @Test
    void shouldNotThrowException_IfCreateValidationSuccess() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        getMockAttendanceRegisterSuccess();
        assertDoesNotThrow(() -> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
    }

    @Test
    void shouldNotThrowException_IfEstimateValidationSuccess() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        assertDoesNotThrow(() -> musterRollValidator.validateEstimateMusterRoll(musterRollRequest));
    }

    @Test
    void shouldThrowException_IfRequestInfoIsNull() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.setRequestInfo(null);
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("REQUEST_INFO"));
    }

    @Test
    void shouldThrowException_IfUserInfoIsNull() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.getRequestInfo().setUserInfo(null);
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("USERINFO"));
    }

    @Test
    void shouldThrowException_IfUUIDIsBlank() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.getRequestInfo().getUserInfo().setUuid("");
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("USERINFO_UUID"));
    }

    @Test
    void shouldThrowException_IfMusterRollIsNull() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.setMusterRoll(null);
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("MUSTER_ROLL"));
    }

    @Test
    void shouldThrowException_IfTenantIdIsNull() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.getMusterRoll().setTenantId(null);
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("TENANT_ID"));
    }

    @Test
    void shouldThrowException_IfRegisterIdIsNull() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.getMusterRoll().setRegisterId(null);
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("REGISTER_ID"));
    }

    @Test
    void shouldThrowException_IfStartDateIsNull() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.getMusterRoll().setStartDate(null);
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("START_DATE_EMPTY"));
    }

    @Test
    void shouldThrowException_IfStartDateIsNotMonday() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.getMusterRoll().setStartDate(new BigDecimal("1669919400000"));
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("START_DATE_MONDAY"));
    }

    @Test
    void shouldThrowException_IfWorkflowIsNull() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        musterRollRequest.setWorkflow(null);
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertTrue(exception.getCode().contentEquals("WORK_FLOW"));
    }

    @Test
    void shouldThrowException_IfWorkflowActionIsBlank() {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateValidationSuccess();
        getMockAttendanceRegisterSuccess();
        musterRollRequest.getWorkflow().setAction("");
        CustomException exception = assertThrows(CustomException.class, ()-> musterRollValidator.validateCreateMusterRoll(musterRollRequest));
        assertNotNull(exception.getErrors().get("WORK_FLOW.ACTION"));
    }

    void getMockAttendanceRegisterSuccess() {
        //MOCK Attendance log search service response
        lenient().when(serviceConfiguration.getAttendanceLogHost()).thenReturn("http://localhost:8023");
        lenient().when(serviceConfiguration.getAttendanceRegisterEndpoint()).thenReturn("/attendance/v1/_search");
        AttendanceRegisterResponse response = MusterRollRequestBuilderTest.getAttendanceRegisterResponse();
        lenient().when(restTemplate.postForObject(any(String.class),any(Object.class),any())).
                thenReturn(response);
    }

}
