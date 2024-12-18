package org.egov.validator;

import com.jayway.jsonpath.JsonPath;
import org.egov.common.contract.models.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.RequestInfo;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.tracer.model.CustomException;
import org.egov.util.MdmsUtil;
import org.egov.web.models.*;
import org.egov.works.services.common.models.musterroll.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.util.MusterRollServiceConstants.*;

@Component
@Slf4j
public class MusterRollValidator {

    private final MusterRollServiceConfiguration serviceConfiguration;

    private final MdmsUtil mdmsUtils;

    private final RestTemplate restTemplate;

    private static final String TENANT_ID = "TENANT_ID";
    private static final String MUSTER_ROLL = "MUSTER_ROLL";
    private static final String MUSTER_ROLL_IS_MANADATORY = "Muster roll is mandatory";
    private static final String TENANT_ID_IS_MANADATORY = "TenantId is mandatory";

    @Autowired
    public MusterRollValidator(MusterRollServiceConfiguration serviceConfiguration, MdmsUtil mdmsUtils, RestTemplate restTemplate) {
        this.serviceConfiguration = serviceConfiguration;
        this.mdmsUtils = mdmsUtils;
        this.restTemplate = restTemplate;
    }

    /**
     * Validate muster roll in estimate service
     * @param musterRollRequest
     */
    public void validateEstimateMusterRoll(MusterRollRequest musterRollRequest){
        log.info("MusterRollValidator::validateEstimateMusterRoll");

        Map<String, String> errorMap = new HashMap<>();
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();
        RequestInfo requestInfo = musterRollRequest.getRequestInfo();

        validateRequestInfo(requestInfo);
        validateEstimateMusterRollRequest(musterRoll);

        //split the tenantId and validate tenantId
        String tenantId = musterRoll.getTenantId();
        Object mdmsData = mdmsUtils.mDMSCall(musterRollRequest, tenantId);
        validateMDMSData(musterRoll, mdmsData, errorMap);

        if (!errorMap.isEmpty()){
            throw new CustomException(errorMap);
        }

    }

    /**
     * Validate muster roll in create service
     * @param musterRollRequest
     */
    public void validateCreateMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollValidator::validateCreateMusterRoll");

        Map<String, String> errorMap = new HashMap<>();
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();
        RequestInfo requestInfo = musterRollRequest.getRequestInfo();
        Workflow workflow = musterRollRequest.getWorkflow();

        validateRequestInfo(requestInfo);
        validateCreateMusterRollRequest(musterRoll);
        validateWorkFlow(workflow, errorMap);

        //split the tenantId and validate tenantId
        String tenantId = musterRoll.getTenantId();
        Object mdmsData = mdmsUtils.mDMSCall(musterRollRequest, tenantId);
        validateMDMSData(musterRoll, mdmsData, errorMap);

        //check if the user is enrolled in the attendance register
        isValidUser(musterRoll, requestInfo);

        if (!errorMap.isEmpty()){
            throw new CustomException(errorMap);
        }

    }

    /**
     * Validate muster roll in update service
     * @param musterRollRequest
     */
    public void validateUpdateMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollValidator::validateUpdateMusterRoll");

        Map<String, String> errorMap = new HashMap<>();
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();
        RequestInfo requestInfo = musterRollRequest.getRequestInfo();
        Workflow workflow = musterRollRequest.getWorkflow();

        validateRequestInfo(requestInfo);
        validateWorkFlow(workflow, errorMap);
        validateUpdateMusterRollRequest(musterRoll);

        //split the tenantId and validate tenantId
        String tenantId = musterRoll.getTenantId();
        Object mdmsData = mdmsUtils.mDMSCall(musterRollRequest, tenantId);
        validateMDMSData(musterRoll, mdmsData, errorMap);

        if (!errorMap.isEmpty()){
            throw new CustomException(errorMap);
        }

    }

    /**
     * Validate muster roll in search service
     * @param requestInfoWrapper
     * @param searchCriteria
     */
    public void validateSearchMuster(RequestInfoWrapper requestInfoWrapper, MusterRollSearchCriteria searchCriteria) {
        log.info("MusterRollValidator::validateSearchMuster");

        if (searchCriteria == null || requestInfoWrapper == null || requestInfoWrapper.getRequestInfo() == null) {
            throw new CustomException("MUSTER_ROLL_SEARCH_CRITERIA_REQUEST", "Muster roll search criteria request is mandatory");
        }
        if (StringUtils.isBlank(searchCriteria.getTenantId())) {
            throw new CustomException(TENANT_ID, "Tenant is mandatory");
        }
    }


    private void validateRequestInfo(RequestInfo requestInfo) {
        if (requestInfo == null) {
            throw new CustomException("REQUEST_INFO", "Request info is mandatory");
        }
        if (requestInfo.getUserInfo() == null) {
            throw new CustomException("USERINFO", "UserInfo is mandatory");
        }
        if (requestInfo.getUserInfo() != null && StringUtils.isBlank(requestInfo.getUserInfo().getUuid())) {
            throw new CustomException("USERINFO_UUID", "UUID is mandatory");
        }
    }

    private void validateCreateMusterRollRequest(MusterRoll musterRoll) {
        if (musterRoll == null) {
            throw new CustomException(MUSTER_ROLL,MUSTER_ROLL_IS_MANADATORY);
        }
        if (musterRoll.getTenantId() == null) {
            throw new CustomException(TENANT_ID,TENANT_ID_IS_MANADATORY);
        }
        if (musterRoll.getRegisterId() == null) {
            throw new CustomException("REGISTER_ID","RegisterId is mandatory");
        }
        if (musterRoll.getStartDate() == null) {
            throw new CustomException("START_DATE_EMPTY","StartDate is mandatory");
        }

        //Check if the startDate is Monday - UI sends the epoch time in IST
        LocalDate startDate = Instant.ofEpochMilli(musterRoll.getStartDate().longValue()).atZone(ZoneId.of(serviceConfiguration.getTimeZone())).toLocalDate();
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new CustomException("START_DATE_MONDAY","StartDate should be Monday");
        }
        musterRoll.setStartDate(new BigDecimal(startDate.atStartOfDay(ZoneId.of(serviceConfiguration.getTimeZone())).toInstant().toEpochMilli()));

        log.info("MusterRollValidator::validateCreateMusterRoll::startDate in epoch::"+musterRoll.getStartDate());
        log.info("MusterRollValidator::validateCreateMusterRoll::startDate::"+startDate);
        log.info("MusterRollValidator::validateCreateMusterRoll::endDate in epoch from request::"+musterRoll.getEndDate());

        //Override the endDate as SUNDAY
        LocalDate endDate = startDate.plusDays(6);
        log.info("MusterRollValidator::validateCreateMusterRoll:: calculated endDate::"+endDate);
        musterRoll.setEndDate(new BigDecimal(endDate.atStartOfDay(ZoneId.of(serviceConfiguration.getTimeZone())).toInstant().toEpochMilli()));

    }

    private void validateUpdateMusterRollRequest(MusterRoll musterRoll) {
        if (musterRoll == null) {
            throw new CustomException(MUSTER_ROLL,MUSTER_ROLL_IS_MANADATORY);
        }
        if (musterRoll.getTenantId() == null) {
            throw new CustomException(TENANT_ID,TENANT_ID_IS_MANADATORY);
        }
        if (musterRoll.getId() == null) {
            throw new CustomException("MUSTER_ROLL_ID","MusterRollId is mandatory");
        }

    }

    private void validateEstimateMusterRollRequest(MusterRoll musterRoll) {
        if (musterRoll == null) {
            throw new CustomException(MUSTER_ROLL,MUSTER_ROLL_IS_MANADATORY);
        }
        if (musterRoll.getTenantId() == null) {
            throw new CustomException(TENANT_ID,TENANT_ID_IS_MANADATORY);
        }
        if (musterRoll.getRegisterId() == null) {
            throw new CustomException("REGISTER_ID","RegisterId is mandatory");
        }
        if (musterRoll.getStartDate() == null) {
            throw new CustomException("START_DATE_EMPTY","StartDate is mandatory");
        }
        //endDate is required for /_estimate musterroll - The estimate will be shown for last few days in the week in view/edit screen
        if (musterRoll.getEndDate() == null) {
            throw new CustomException("END_DATE_EMPTY","EndDate is mandatory");
        }

        log.info("MusterRollValidator::validateEstimateMusterRoll::startDate in epoch::"+musterRoll.getStartDate());
        log.info("MusterRollValidator::validateEstimateMusterRoll::endDate in epoch::"+musterRoll.getEndDate());

    }

    private void validateWorkFlow(Workflow workflow, Map<String, String> errorMap) {
        if (workflow == null) {
            throw new CustomException("WORK_FLOW", "Work flow is mandatory");
        }
        if (StringUtils.isBlank(workflow.getAction())) {
            errorMap.put("WORK_FLOW.ACTION", "Work flow's action is mandatory");
        }
    }

    private void validateMDMSData(MusterRoll musterRoll, Object mdmsData, Map<String, String> errorMap) {

        final String jsonPathForTenants = "$.MdmsRes." + MDMS_TENANT_MODULE_NAME + "." + MASTER_TENANTS + ".*";
        List<Object> tenantRes = null;

        try {
            tenantRes = JsonPath.read(mdmsData, jsonPathForTenants);

        } catch (Exception e) {
            log.error("MusterRollValidator::validateMDMSData::"+e.getMessage());
            throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
        }

        if (CollectionUtils.isEmpty(tenantRes)) {
            log.error("The tenant: " + musterRoll.getTenantId() + " is not present in MDMS");
            errorMap.put("INVALID_TENANT", "The tenant: " + musterRoll.getTenantId() + " is not present in MDMS");
        }

    }

    /**
     * Check if the user is valid. User should be enrolled as a staff in the attendance register
     * @param musterRoll
     * @param requestInfo
     */
    public void isValidUser(MusterRoll musterRoll, RequestInfo requestInfo) {
        String id = requestInfo.getUserInfo().getUuid();

        StringBuilder uri = new StringBuilder();
        uri.append(serviceConfiguration.getAttendanceLogHost()).append(serviceConfiguration.getAttendanceRegisterEndpoint());
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri.toString())
                .queryParam("tenantId",musterRoll.getTenantId())
                .queryParam("ids",musterRoll.getRegisterId())
                .queryParam("status", Status.ACTIVE);
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();

        AttendanceRegisterResponse attendanceRegisterResponse = null;
        log.info("MusterRollValidator::isValidUser::call attendance register search with tenantId::"+musterRoll.getTenantId()
                +"::for user::"+id);

        try {
            attendanceRegisterResponse  = restTemplate.postForObject(uriBuilder.toUriString(),requestInfoWrapper,AttendanceRegisterResponse.class);
        }  catch (HttpClientErrorException | HttpServerErrorException httpClientOrServerExc) {
            log.error("MusterRollValidator::isValidUser::Error thrown from attendance register service::"+httpClientOrServerExc.getStatusCode());
            throw new CustomException("ATTENDANCE_REGISTER_SERVICE_EXCEPTION","Error thrown from attendance register service::"+httpClientOrServerExc.getStatusCode());
        }

        if (attendanceRegisterResponse == null || CollectionUtils.isEmpty(attendanceRegisterResponse.getAttendanceRegister())) {
            log.error("MusterRollValidator::isValidUser::User with id::" + id + " is not enrolled in the attendance register::"+musterRoll.getRegisterId());
            throw new CustomException("INVALID_USER","User is not enrolled in the attendance register");
        }

    }
    
   
}
