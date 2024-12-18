package org.egov.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.kafka.MusterRollProducer;
import org.egov.repository.MusterRollRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.MdmsUtil;
import org.egov.util.MusterRollServiceUtil;
import org.egov.util.ResponseInfoCreator;
import org.egov.validator.MusterRollValidator;

import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterResponse;
import org.egov.web.models.MusterRoll;
import org.egov.web.models.MusterRollRequest;
import org.egov.web.models.MusterRollResponse;
import org.egov.web.models.MusterRollSearchCriteria;
import org.egov.works.services.common.models.musterroll.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.egov.util.MusterRollServiceConstants.STATUS_APPROVED;

@Service
@Slf4j
public class MusterRollService {

    private final MusterRollValidator musterRollValidator;

    private final EnrichmentService enrichmentService;

    private final CalculationService calculationService;

    private final WorkflowService workflowService;

    private final NotificationService notificationService;

    private final MusterRollProducer musterRollProducer;

    private final MusterRollServiceConfiguration serviceConfiguration;

    private final MusterRollRepository musterRollRepository;

    private final ObjectMapper mapper;

    private final MdmsUtil mdmsUtils;

    private final MusterRollServiceUtil musterRollServiceUtil;

    private final MusterRollServiceConfiguration config;

    private final RestTemplate restTemplate;

    private final ResponseInfoCreator responseInfoCreator;

    private static final String COMPUTE_ATTENDENSE = "computeAttendance";

    @Autowired
    public MusterRollService(CalculationService calculationService, MusterRollValidator musterRollValidator, EnrichmentService enrichmentService, WorkflowService workflowService, NotificationService notificationService, MusterRollProducer musterRollProducer, MusterRollServiceConfiguration serviceConfiguration, MusterRollRepository musterRollRepository, ObjectMapper mapper, RestTemplate restTemplate, MdmsUtil mdmsUtils, MusterRollServiceUtil musterRollServiceUtil, MusterRollServiceConfiguration config, ResponseInfoCreator responseInfoCreator) {
        this.calculationService = calculationService;
        this.musterRollValidator = musterRollValidator;
        this.enrichmentService = enrichmentService;
        this.workflowService = workflowService;
        this.notificationService = notificationService;
        this.musterRollProducer = musterRollProducer;
        this.serviceConfiguration = serviceConfiguration;
        this.musterRollRepository = musterRollRepository;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.mdmsUtils = mdmsUtils;
        this.musterRollServiceUtil = musterRollServiceUtil;
        this.config = config;
        this.responseInfoCreator = responseInfoCreator;
    }

    /**
     * Calculates the per day attendance , attendance aggregate from startDate to endDate
     * and provides it as an estimate.
     * Note: This will NOT create muster roll and NOT store the details
     *
     * @param musterRollRequest
     * @return
     */
    public MusterRollRequest estimateMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollService::estimateMusterRoll");

        musterRollValidator.validateEstimateMusterRoll(musterRollRequest);
        enrichmentService.enrichMusterRollOnEstimate(musterRollRequest);
        calculationService.createAttendance(musterRollRequest,false);
        return musterRollRequest;
    }


    /**
     * Calculates the per day attendance , attendance aggregate from startDate to endDate for all the
     * individuals of the provided attendance register.
     * Creates muster roll and stores the details.
     *
     * @param musterRollRequest
     * @return
     */
    public MusterRollRequest createMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollService::createMusterRoll");

        musterRollValidator.validateCreateMusterRoll(musterRollRequest);
        checkMusterRollExists(musterRollRequest.getMusterRoll());
        enrichmentService.enrichMusterRollOnCreate(musterRollRequest);
        calculationService.createAttendance(musterRollRequest,true);
        workflowService.updateWorkflowStatus(musterRollRequest);

        musterRollProducer.push(serviceConfiguration.getSaveMusterRollTopic(), musterRollRequest);
        return musterRollRequest;
    }

    /**
     * Search muster roll based on the given search criteria - tenantId, musterId, musterRollNumber, startDate, endDate, status
     * and musterRollStatus
     *
     * @param requestInfoWrapper
     * @param searchCriteria
     * @return
     */
    public MusterRollResponse searchMusterRolls(RequestInfoWrapper requestInfoWrapper, MusterRollSearchCriteria searchCriteria) {
        log.info("MusterRollService::searchMusterRolls");

        musterRollValidator.validateSearchMuster(requestInfoWrapper,searchCriteria);
        enrichmentService.enrichSearchRequest(searchCriteria);

        List<Role> roles = requestInfoWrapper.getRequestInfo().getUserInfo().getRoles();
        boolean isFilterRequired = false;
        if (config.getRestrictedSearchRoles() != null && !config.getRestrictedSearchRoles().isEmpty()) {
            List<String> restrictedRoles = Arrays.asList(config.getRestrictedSearchRoles().split(","));
            isFilterRequired = roles.stream()
                    .anyMatch(role -> restrictedRoles.contains(role.getCode()));
        }

        //Fetch the attendance registers that belong to the user and then fetch the musters that belongs to the user
        List<String> registerIds = new ArrayList<>();
        if (isFilterRequired) {
            registerIds = fetchAttendanceRegistersOfUser(requestInfoWrapper.getRequestInfo(),searchCriteria);
        }

        List<MusterRoll> musterRollList = musterRollRepository.getMusterRoll(searchCriteria,registerIds);
        int count = musterRollRepository.getMusterRollCount(searchCriteria,registerIds);
        List<MusterRoll> filteredMusterRollList = musterRollList;

        //apply the limit and offset
        if (filteredMusterRollList != null && !musterRollServiceUtil.isTenantBasedSearch(searchCriteria)) {
            filteredMusterRollList = applyLimitAndOffset(searchCriteria,filteredMusterRollList);
        }

        //populate response
        ResponseInfo responseInfo = responseInfoCreator.createResponseInfoFromRequestInfo(requestInfoWrapper.getRequestInfo(), true);
        return MusterRollResponse.builder().responseInfo(responseInfo).musterRolls(filteredMusterRollList)
                .count(count).build();
    }

    /**
     * Updates the totalAttendance, skill details (if modified) and re-calculates the attendance (if 'computeAttendance' is true)
     *
     * @param musterRollRequest
     * @return
     */
    public MusterRollRequest updateMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollService::updateMusterRoll");

        musterRollValidator.validateUpdateMusterRoll(musterRollRequest);
        //If 'computeAttendance' flag is true, re-calculate the attendance from attendanceLogs and update
        boolean isComputeAttendance = isComputeAttendance(musterRollRequest.getMusterRoll());

        //check if the user is enrolled in the attendance register for resubmit
        MusterRoll existingMusterRoll = fetchExistingMusterRoll(musterRollRequest.getMusterRoll());
        log.info("MusterRollService::updateMusterRoll::update request for musterRollNumber::"+existingMusterRoll.getMusterRollNumber());

        //fetch MDMS data for muster - skill level
        String tenantId = existingMusterRoll.getTenantId();
        Object mdmsData = mdmsUtils.mDMSCallMuster(musterRollRequest, tenantId);
        Object mdmsV2Data = mdmsUtils.mDMSV2CallMuster(musterRollRequest, tenantId);


        //fetch the update additionalDetails from the request and persist it for verification
        if (!isComputeAttendance) {
            Object additionalDetails = musterRollRequest.getMusterRoll().getAdditionalDetails();
            existingMusterRoll.setAdditionalDetails(additionalDetails);
        }

        enrichmentService.enrichMusterRollOnUpdate(musterRollRequest,existingMusterRoll,mdmsV2Data);
        if (isComputeAttendance) {
            RequestInfo requestInfo = musterRollRequest.getRequestInfo();
            musterRollValidator.isValidUser(existingMusterRoll, requestInfo);
            calculationService.updateAttendance(musterRollRequest,mdmsData);
        }
        workflowService.updateWorkflowStatus(musterRollRequest);
        musterRollProducer.push(serviceConfiguration.getUpdateMusterRollTopic(), musterRollRequest);

        try {
            notificationService.sendNotificationToCBO(musterRollRequest);
        }catch (Exception e){
            log.error("Exception while sending notification: " + e);
        }

        //If the musterroll is in 'APPROVED' status, push the musterRoll to calculate topic to be processed by expense-calculator service
        if (StringUtils.isNotBlank(musterRollRequest.getMusterRoll().getMusterRollStatus()) && STATUS_APPROVED.equalsIgnoreCase(musterRollRequest.getMusterRoll().getMusterRollStatus())) {
            musterRollProducer.push(serviceConfiguration.getCalculateMusterRollTopic(), musterRollRequest);
        }

        return musterRollRequest;
    }

    /**
     * Check if the muster roll already exists for the same registerId, startDate and endDate to avoid duplicate muster creation
     * @param musterRoll
     */
    private void checkMusterRollExists(MusterRoll musterRoll) {
        MusterRollSearchCriteria searchCriteria = MusterRollSearchCriteria.builder().tenantId(musterRoll.getTenantId())
                .registerId(musterRoll.getRegisterId()).fromDate(musterRoll.getStartDate())
                .toDate(musterRoll.getEndDate()).build();
        List<MusterRoll> musterRolls = musterRollRepository.getMusterRoll(searchCriteria,null);
        if (!CollectionUtils.isEmpty(musterRolls)) {
            StringBuilder exceptionMessage = new StringBuilder();
            exceptionMessage.append("Muster roll already exists for the register - ");
            exceptionMessage.append(musterRoll.getRegisterId());
            exceptionMessage.append(" with startDate - ");
            exceptionMessage.append(musterRoll.getStartDate());
            exceptionMessage.append(" and endDate - ");
            exceptionMessage.append(musterRoll.getEndDate());
            throw new CustomException("DUPLICATE_MUSTER_ROLL",exceptionMessage.toString());
        }
    }

    /**
     * Fetch the existing muster roll from DB else throw error
     * @param musterRoll
     * @return
     */
    private MusterRoll fetchExistingMusterRoll(MusterRoll musterRoll) {
        List<String> ids = new ArrayList<>();
        ids.add(musterRoll.getId());
        MusterRollSearchCriteria searchCriteria = MusterRollSearchCriteria.builder().ids(ids).tenantId(musterRoll.getTenantId()).build();
        List<MusterRoll> musterRolls = musterRollRepository.getMusterRoll(searchCriteria,null);
        if (CollectionUtils.isEmpty(musterRolls)) {
            throw new CustomException("NO_MATCH_FOUND","Invalid Muster roll id - "+musterRoll.getId());
        }
        return musterRolls.get(0);
    }

    /**
     * Check if the 'computeAttendance' flag is true
     * @param musterRoll
     * @return
     */
    private boolean isComputeAttendance (MusterRoll musterRoll) {
       if (musterRoll.getAdditionalDetails() != null) {
           try {
               JsonNode node = mapper.readTree(mapper.writeValueAsString(musterRoll.getAdditionalDetails()));
               if (node.findValue(COMPUTE_ATTENDENSE) != null && StringUtils.isNotBlank(node.findValue(COMPUTE_ATTENDENSE).textValue())) {
                   String value = node.findValue(COMPUTE_ATTENDENSE).textValue();
                   return BooleanUtils.toBoolean(value);
               }
           } catch (IOException e) {
               log.info("MusterRollService::isComputeAttendance::Failed to parse additionalDetail object from request"+e);
               throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object from request on update");
           }
       }
       return false;
    }

    /**
     * Fetch the registerIds that the user belongs to ( if role is org_staff or org_admin)
     * @param requestInfo
     * @return
     */
    private List<String>  fetchAttendanceRegistersOfUser(RequestInfo requestInfo, MusterRollSearchCriteria searchCriteria) {
        String id = requestInfo.getUserInfo().getUuid();

        StringBuilder uri = new StringBuilder();
        uri.append(config.getAttendanceLogHost()).append(config.getAttendanceRegisterEndpoint());
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri.toString())
                .queryParam("tenantId",searchCriteria.getTenantId())
                .queryParam("status", Status.ACTIVE)
                .queryParam("limit", config.getAttendanceRegisterSearchLimit());
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();

        AttendanceRegisterResponse attendanceRegisterResponse = null;
        log.info("MusterRollService::fetchAttendanceRegistersOfUser::call attendance register search with tenantId::"+searchCriteria.getTenantId()
                +"::for user::"+id);

        try {
            attendanceRegisterResponse  = restTemplate.postForObject(uriBuilder.toUriString(),requestInfoWrapper,AttendanceRegisterResponse.class);
        }  catch (HttpClientErrorException | HttpServerErrorException httpClientOrServerExc) {
            log.error("MusterRollService::fetchAttendanceRegistersOfUser::Error thrown from attendance register service::"+httpClientOrServerExc.getStatusCode());
            throw new CustomException("ATTENDANCE_REGISTER_SERVICE_EXCEPTION","Error thrown from attendance register service::"+httpClientOrServerExc.getStatusCode());
        }

        if (attendanceRegisterResponse == null || CollectionUtils.isEmpty(attendanceRegisterResponse.getAttendanceRegister())) {
            throw new CustomException("NO_DATA_FOUND","No Attendance registers found for the user. So no muster created for the register");
        }

        List<AttendanceRegister> attendanceRegisters = attendanceRegisterResponse.getAttendanceRegister();
        return attendanceRegisters.stream()
                .map(AttendanceRegister::getId)
                .collect(Collectors.toList());
    }

    /**
     * Applies the limit and offset
     * @param searchCriteria
     * @param musterRollList
     * @return
     */
    private List<MusterRoll> applyLimitAndOffset(MusterRollSearchCriteria searchCriteria, List<MusterRoll> musterRollList) {
        return musterRollList.stream()
                        .skip(searchCriteria.getOffset())  // offset
                        .limit(searchCriteria.getLimit()) // limit
                        .collect(Collectors.toList());
    }
}
