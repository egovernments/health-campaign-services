package org.egov.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.models.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.tracer.model.CustomException;
import org.egov.util.MdmsUtil;
import org.egov.util.MusterRollServiceUtil;
import org.egov.web.models.*;
import org.egov.works.services.common.models.bankaccounts.*;
import org.egov.works.services.common.models.musterroll.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.util.MusterRollServiceConstants.*;

@Service
@Slf4j
public class CalculationService {

    private final RestTemplate restTemplate;

    private final MusterRollServiceConfiguration config;

    private final MdmsUtil mdmsUtils;

    private final MusterRollServiceUtil musterRollServiceUtil;

    private final ObjectMapper mapper;

    private int halfDayNumHours;
    private int fullDayNumHours;
    private boolean isRoundOffHours;

    @Autowired
    public CalculationService(RestTemplate restTemplate, MusterRollServiceConfiguration config, MdmsUtil mdmsUtils, MusterRollServiceUtil musterRollServiceUtil, ObjectMapper mapper) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.mdmsUtils = mdmsUtils;
        this.musterRollServiceUtil = musterRollServiceUtil;
        this.mapper = mapper;
    }


    /**
     * Calculate the per day attendance and attendance aggregate for each individual for create muster roll
     * @param musterRollRequest
     * @param isCreate
     *
     */
    public void createAttendance(MusterRollRequest musterRollRequest, boolean isCreate) {

        //fetch MDMS data for muster - attendance hours and skill level
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();
        String tenantId = musterRoll.getTenantId();
        Object mdmsData = mdmsUtils.mDMSCallMuster(musterRollRequest, tenantId);
        Object mdmsV2Data = mdmsUtils.mDMSV2CallMuster(musterRollRequest, tenantId);


        //fetch the log events for all individuals in a muster roll
        List<AttendanceLog> attendanceLogList = fetchAttendanceLogsAndHours(musterRollRequest,mdmsData);
        Map<String,List<LocalDateTime>> individualEntryAttendanceMap = populateAttendanceLogEvents(attendanceLogList,ENTRY_EVENT);
        Map<String,List<LocalDateTime>> individualExitAttendanceMap = populateAttendanceLogEvents(attendanceLogList,EXIT_EVENT);

        log.info("CalculationService::createAttendance::From MDMS::HALF_DAY_NUM_HOURS::"+halfDayNumHours+"::FULL_DAY_NUM_HOURS::"+fullDayNumHours+"::isRoundOffHours::"+isRoundOffHours);
        AuditDetails auditDetails = musterRoll.getAuditDetails();
        LocalDate startDate = Instant.ofEpochMilli(musterRoll.getStartDate().longValue()).atZone(ZoneId.of(config.getTimeZone())).toLocalDate();
        LocalDate endDate = Instant.ofEpochMilli(musterRoll.getEndDate().longValue()).atZone(ZoneId.of(config.getTimeZone())).toLocalDate();

        log.debug("CalculationService::calculateAttendance::startDate::"+startDate+"::endDate::"+endDate);

        //calculate attendance aggregate and per day per individual attendance
        List<IndividualEntry> individualEntries = new ArrayList<>();
        List<IndividualEntry> individualEntriesFromRequest = musterRoll.getIndividualEntries();
        
        //Collect unique individuals from attendance logs
        Set<String> attendeesWithLogs = new HashSet<>();
        for(String individualId: individualExitAttendanceMap.keySet()) {
        	attendeesWithLogs.add(individualId);
        }
        //Fetch Absentees by comparing original enrolment against attendance register - fix for PFM-3184
        List<IndividualEntry> absenteesList = fetchAbsentees(attendeesWithLogs, musterRoll, musterRollRequest.getRequestInfo());
        //Add absentees to the response first. These attendees have 0 as attendance
        individualEntries.addAll(absenteesList);

        // fetch individual details from individual service and account details from bank account service
        List<String> individualIds = new ArrayList<>();
        individualIds.addAll(individualExitAttendanceMap.keySet());
        //Add all absentee individualIds as well
        individualIds.addAll(absenteesList.stream().map(entry-> entry.getIndividualId()).collect(Collectors.toSet()));
        List<Individual> individuals = fetchIndividualDetails(individualIds, musterRollRequest.getRequestInfo(),musterRoll.getTenantId(),musterRoll);
        List<BankAccount> bankAccounts = fetchBankaccountDetails(individualIds, musterRollRequest.getRequestInfo(),musterRoll.getTenantId());

        for (Map.Entry<String,List<LocalDateTime>> entry : individualExitAttendanceMap.entrySet()) {
            IndividualEntry individualEntry = new IndividualEntry();
            if (isCreate) {
                individualEntry.setId(UUID.randomUUID().toString());
            }
            individualEntry.setIndividualId(entry.getKey());
            List<LocalDateTime> exitTimestampList = entry.getValue();
            List<LocalDateTime> entryTimestampList = individualEntryAttendanceMap.get(entry.getKey());
            List<AttendanceEntry> attendanceEntries = new ArrayList<>();
            LocalDate date = startDate;
            BigDecimal totalAttendance = new BigDecimal("0.0");
            while (date.isBefore(endDate) || date.isEqual(endDate)) {
                AttendanceEntry attendanceEntry = new AttendanceEntry();
                String entryAttendanceLogId = null;
                String exitAttendanceLogId = null;
                if (isCreate) {
                    attendanceEntry.setId(UUID.randomUUID().toString());
                }
                attendanceEntry.setTime(new BigDecimal(date.atStartOfDay(ZoneId.of(config.getTimeZone())).toInstant().toEpochMilli()));

                //check if the individual's entry attendance is logged between startDate and endDate
                LocalDateTime entryTimestamp = null;
                for (LocalDateTime dateTime : entryTimestampList) {
                    if (date.isEqual(dateTime.toLocalDate())) {
                        entryTimestamp = dateTime;
                        //populate the attendanceLogId for estimate
                        if (!isCreate) {
                            entryAttendanceLogId = getAttendanceLogId(attendanceLogList,individualEntry.getIndividualId(),entryTimestamp,ENTRY_EVENT);
                        }
                        break;
                    }
                }

                //check if the individual's exit attendance is logged between startDate and endDate
                LocalDateTime exitTimestamp = null;
                for (LocalDateTime dateTime : exitTimestampList) {
                    if (date.isEqual(dateTime.toLocalDate())) {
                        exitTimestamp = dateTime;
                        //populate the attendanceLogId for estimate
                        if (!isCreate) {
                            exitAttendanceLogId = getAttendanceLogId(attendanceLogList,individualEntry.getIndividualId(),exitTimestamp,EXIT_EVENT);
                        }
                        break;
                    }
                }

                totalAttendance = getTotalAttendance(totalAttendance, attendanceEntry, entryTimestamp,exitTimestamp);

                date = date.plusDays(1);
                attendanceEntry.setAuditDetails(auditDetails);
                // set attendanceLogId in additionalDetails for attendanceEntry
                if (!isCreate && StringUtils.isNotBlank(entryAttendanceLogId) && StringUtils.isNotBlank(exitAttendanceLogId)) {
                    musterRollServiceUtil.populateAdditionalDetailsAttendanceEntry(attendanceEntry,entryAttendanceLogId,exitAttendanceLogId);
                }
                attendanceEntries.add(attendanceEntry);
            }

            individualEntry.setAttendanceEntries(attendanceEntries);
            log.debug("CalculationService::createAttendance::Attendance Entry::size::"+attendanceEntries.size());
            individualEntry.setAuditDetails(auditDetails);
            individualEntry.setActualTotalAttendance(totalAttendance);
            //Set individual details in additionalDetails
           /** if (!CollectionUtils.isEmpty(individuals)) {
                Individual individual = individuals.stream()
                                .filter(ind -> ind.getId().equalsIgnoreCase(individualEntry.getIndividualId()))
                                        .findFirst().orElse(null);
                BankAccount bankAccount = bankAccounts.stream()
                                .filter(account -> account.getReferenceId().equalsIgnoreCase(individualEntry.getIndividualId()))
                                        .findFirst().orElse(null);

                if (individual != null) {
                    setAdditionalDetails(individualEntry,individualEntriesFromRequest,mdmsData,individual,bankAccount,isCreate);
                } else {
                    log.info("CalculationService::createAttendance::No match found in individual and bank account service for the individual id from attendance log - "+individualEntry.getIndividualId());
                }

            } **/

            individualEntries.add(individualEntry);
        }

        // Loop through and set individual and bank account details
		for (IndividualEntry entry : individualEntries) {

			// Set individual details in additionalDetails
			if (!CollectionUtils.isEmpty(individuals) /* && !CollectionUtils.isEmpty(bankAccounts) */) {
				Individual individual = individuals.stream()
						.filter(ind -> ind.getId().equalsIgnoreCase(entry.getIndividualId())).findFirst()
						.orElse(null);
				BankAccount bankAccount = bankAccounts.stream()
						.filter(account -> account.getReferenceId().equalsIgnoreCase(entry.getIndividualId()))
						.findFirst().orElse(null);

				if (individual != null /* && bankAccount != null */) {
					setAdditionalDetails(entry, individualEntriesFromRequest, mdmsV2Data, individual,
							bankAccount, isCreate);
				} else {
					log.info(
							"CalculationService::createAttendance::No match found in individual and bank account service for the individual id from attendance log - "
									+ entry.getIndividualId());
				}

			}
		}
       
        musterRoll.setIndividualEntries(individualEntries);
        log.debug("CalculationService::createAttendance::Individuals::size::"+musterRoll.getIndividualEntries().size());

    }
    
    
    /**
     * //Fix for PFM-3184. Attendance register only contains info about people who attended. Absentees are left out. This method
     * fetches all the unique individuals added to a register and identifies wage seekers who never attended a single day of work
     * in a given time period. Adds their entries with attendnce of 0 and returns it to the UI.
     * @param attendeesWithLogs
     * @param musterRoll
     * @param requestInfo
     * @return
     */
    private List<IndividualEntry> fetchAbsentees(Set<String> attendeesWithLogs, MusterRoll musterRoll, RequestInfo requestInfo){
    	List<IndividualEntry> absentees = new ArrayList<>();
    	 // Get all individuals who were originally registered to the register
        AttendanceRegisterResponse response = musterRollServiceUtil.fetchAttendanceRegister(musterRoll, requestInfo);
        List<AttendanceRegister> registers = response.getAttendanceRegister();
        if(registers!=null && !registers.isEmpty()) {
        	AttendanceRegister register = registers.get(0);
        	//Get all attendees of the register
            getAllAttendees(musterRoll,register,attendeesWithLogs,absentees);
            
        }//End of if
        return absentees;
    }

    private void getAllAttendees(MusterRoll musterRoll, AttendanceRegister register, Set<String> attendeesWithLogs, List<IndividualEntry> absentees) {
        List<IndividualEntry> entries = register.getAttendees();
        Set<String> allAttendees = null;

        if(entries!=null && !entries.isEmpty()) {
            allAttendees = entries.stream().map(IndividualEntry::getIndividualId).collect(Collectors.toSet());
            //Remove all attendees who have marked some sort of attendance. This leaves the once who registered but never marked a day's work
            allAttendees.removeAll(attendeesWithLogs);
            //Add these absentees to a list with zero as attendance days
            for(String individual: allAttendees) {
                for(IndividualEntry entry: entries) {
                    if(entry.getIndividualId().equals(individual)) {
                        entry.setActualTotalAttendance(new BigDecimal(0));
                        absentees.add(entry);            		}
                }
            }//End of for
        }
        else {
            log.error("No attendees enrolled in register " + musterRoll.getRegisterId());
        }
    }
    /**
     * Calculate the total attendance for the individual
     * @param totalAttendance
     * @param attendanceEntry
     * @param entryTimestamp
     * @param exitTimestamp
     *
     */
    private BigDecimal getTotalAttendance(BigDecimal totalAttendance, AttendanceEntry attendanceEntry, LocalDateTime entryTimestamp, LocalDateTime exitTimestamp) {
        //set attendance if present else set as 0
        if (entryTimestamp != null && exitTimestamp != null) {
            int workHours = exitTimestamp.getHour() - entryTimestamp.getHour();

            /** IF isRoundOffHours is true,
             * FullDayAttendance is given if the workHours is more than halfDayNumHours (even if it is less than fullDayNumHours).
             *  HalfDayAttendance is given if the workHours is less than halfDayNumHours. eg., 6hrs will be considered as fullDayAttendance, 2hrs as halfDayAttendance
             */
            attendanceEntry.setAttendance(new BigDecimal("0.5"));
            if (isRoundOffHours && workHours > halfDayNumHours) {
                attendanceEntry.setAttendance(new BigDecimal("1.0"));
            }

            if (!isRoundOffHours && workHours >= fullDayNumHours) {
                attendanceEntry.setAttendance(new BigDecimal("1.0"));
            }

            if (workHours == 0) {
                attendanceEntry.setAttendance(new BigDecimal("0.0"));
            }

        } else {
            attendanceEntry.setAttendance(new BigDecimal("0.0"));
        }

        //calculate totalAttendance
        totalAttendance = totalAttendance.add(attendanceEntry.getAttendance());
        return totalAttendance;
    }


    /**
     * Re-calculates the per day attendance and attendance aggregate for each individual on update
     * @param musterRollRequest
     *
     */
    public void updateAttendance(MusterRollRequest musterRollRequest, Object mdmsData) {

        //fetch MDMS data for muster - attendance hours and skill level
        MusterRoll musterRoll = musterRollRequest.getMusterRoll();

        //fetch the log events for all individuals in a muster roll
        List<AttendanceLog> attendanceLogList = fetchAttendanceLogsAndHours(musterRollRequest,mdmsData);
        Map<String,List<LocalDateTime>> individualEntryAttendanceMap = populateAttendanceLogEvents(attendanceLogList,ENTRY_EVENT);
        Map<String,List<LocalDateTime>> individualExitAttendanceMap = populateAttendanceLogEvents(attendanceLogList,EXIT_EVENT);

        log.info("CalculationService::updateAttendance::From MDMS::HALF_DAY_NUM_HOURS::"+halfDayNumHours+"::FULL_DAY_NUM_HOURS::"+fullDayNumHours+"::isRoundOffHours::"+isRoundOffHours);

        //calculate attendance aggregate and per day per individual attendance
        AuditDetails auditDetails = musterRoll.getAuditDetails();
        List<IndividualEntry> individualEntries = musterRoll.getIndividualEntries();
        for (Map.Entry<String,List<LocalDateTime>> entry : individualExitAttendanceMap.entrySet()) {
            IndividualEntry individualEntry = individualEntries.stream()
                                                                .filter(individual -> individual.getIndividualId().equalsIgnoreCase(entry.getKey()))
                                                                .findFirst().get();

            List<LocalDateTime> exitTimestampList = entry.getValue();
            List<LocalDateTime> entryTimestampList = individualEntryAttendanceMap.get(entry.getKey());
            List<AttendanceEntry> attendanceEntries = individualEntry.getAttendanceEntries();
            BigDecimal totalAttendance = new BigDecimal("0.0");
            for (AttendanceEntry attendanceEntry : attendanceEntries) {
                LocalDate attendanceDate = Instant.ofEpochMilli(attendanceEntry.getTime().longValue()).atZone(ZoneId.of(config.getTimeZone())).toLocalDate();
                LocalDateTime entryTimestamp = entryTimestampList.stream()
                                                        .filter(dateTime -> dateTime.toLocalDate().isEqual(attendanceDate))
                                                        .findFirst().orElse(null);
                LocalDateTime exitTimestamp = exitTimestampList.stream()
                        .filter(dateTime -> dateTime.toLocalDate().isEqual(attendanceDate))
                        .findFirst().orElse(null);

                totalAttendance = getTotalAttendance(totalAttendance, attendanceEntry, entryTimestamp,exitTimestamp );
                attendanceEntry.setAuditDetails(auditDetails);

            }
            individualEntry.setAuditDetails(auditDetails);
            individualEntry.setActualTotalAttendance(totalAttendance);
            //During update on RESUBMIT ( if 'computeAttendance' is true), per day attendance and attendance aggregate will be recalculated
            //so modifiedTotalAttendance will be reset as null as the musterRoll will go through verify action again.
            individualEntry.setModifiedTotalAttendance(null);
        }
        log.debug("CalculationService::updateAttendance::Individuals::size::"+musterRoll.getIndividualEntries().size());
    }

    /**
     * Fetch the attendance logs and the halfDayNumHours, fullDayNumHours from MDMS
     * @param musterRollRequest
     * @return List<AttendanceLog>
     */
    private List<AttendanceLog> fetchAttendanceLogsAndHours(MusterRollRequest musterRollRequest, Object mdmsData) {

        //fetch the attendance log
        List<AttendanceLog> attendanceLogList = getAttendanceLogs(musterRollRequest.getMusterRoll(),musterRollRequest.getRequestInfo());

        populateAttendanceHours(mdmsData);

        return attendanceLogList;
    }

    /**
     * The method returns a map with key as individualId and value as list of entry or exit timestamp
     * @param attendanceLogList
     * @param event
     * @return
     */

    private Map<String,List<LocalDateTime>> populateAttendanceLogEvents(List<AttendanceLog> attendanceLogList, String event) {
        //populate the map with key as individualId and value as the corresponding list of exit time
        return attendanceLogList.stream()
                .filter(attendanceLog -> attendanceLog.getType().equalsIgnoreCase(event))
                .collect(Collectors.groupingBy(
                        AttendanceLog::getIndividualId, //key
                        LinkedHashMap::new, // populate the map
                        Collectors.mapping(attendanceLog -> Instant.ofEpochMilli(attendanceLog.getTime().longValue()).atZone(ZoneId.of(config.getTimeZone())).toLocalDateTime(),Collectors.toList()) //value is the list of timestamp
                ));
    }

    /**
     * Fetch the  attendance log events for all individuals in an attendance register between the startDate and endDate
     * This is fetched from the AttendanceLog service
     * @param musterRoll
     * @param requestInfo
     *
     */
    private List<AttendanceLog> getAttendanceLogs(MusterRoll musterRoll, RequestInfo requestInfo){

        /* UI sends the startDate and endDate. Set the toTime for attendanceLog search api as endDate+23h0m to
        * fetch the logs till end of the endDate */
        BigDecimal fromTime = musterRoll.getStartDate();
        LocalDate endDate = Instant.ofEpochMilli(musterRoll.getEndDate().longValue()).atZone(ZoneId.of(config.getTimeZone())).toLocalDate();
        // set the endTime as endDate's date+23h0min
        LocalDateTime endTime = endDate.atTime(23,0);
        BigDecimal toTime = new BigDecimal(endTime.atZone(ZoneId.of(config.getTimeZone())).toInstant().toEpochMilli());

        StringBuilder uri = new StringBuilder();
        uri.append(config.getAttendanceLogHost()).append(config.getAttendanceLogEndpoint());
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri.toString())
                .queryParam("tenantId",musterRoll.getTenantId())
                .queryParam("registerId",musterRoll.getRegisterId())
                .queryParam("fromTime",fromTime)
                .queryParam("toTime",toTime)
                .queryParam("status", Status.ACTIVE);
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
        AttendanceLogResponse attendanceLogResponse = null;

        log.info("CalculationService::getAttendanceLogs::call attendance log search with tenantId::"+musterRoll.getTenantId()
                +"::registerId::"+musterRoll.getRegisterId()+"::fromTime::"+musterRoll.getStartDate()+"::toTime::"+musterRoll.getEndDate());

        try {
            attendanceLogResponse  = restTemplate.postForObject(uriBuilder.toUriString(),requestInfoWrapper,AttendanceLogResponse.class);
        }  catch (HttpClientErrorException | HttpServerErrorException httpClientOrServerExc) {
           log.error("CalculationService::getAttendanceLogs::Error thrown from attendance log service::"+httpClientOrServerExc.getStatusCode());
            throw new CustomException("ATTENDANCE_LOG_SERVICE_EXCEPTION","Error thrown from attendance log service::"+httpClientOrServerExc.getStatusCode());
        }

        if (attendanceLogResponse == null || attendanceLogResponse.getAttendance() == null) {
            StringBuilder exceptionMessage = new StringBuilder();
            exceptionMessage.append("No attendance log found for the register - ");
            exceptionMessage.append(musterRoll.getRegisterId());
            exceptionMessage.append(" with startDate - ");
            exceptionMessage.append(musterRoll.getStartDate());
            exceptionMessage.append(" and endDate - ");
            exceptionMessage.append(musterRoll.getEndDate());
            throw new CustomException("ATTENDANCE_LOG_EMPTY",exceptionMessage.toString());
        }

        log.info("CalculationService::getAttendanceLogs::Attendance logs fetched successfully");
        return attendanceLogResponse.getAttendance();
    }

    /**
     * Fetch the half day , full day hours from MDMS
     * @param mdmsData
     *
     */
    private void populateAttendanceHours(Object mdmsData) {

        final String jsonPathForWorksMuster = "$.MdmsRes." + MDMS_COMMON_MASTERS_MODULE_NAME + "." + MASTER_MUSTER_ROLL + ".*";
        List<LinkedHashMap<String,String>> musterRes = null;

        try {
            musterRes = JsonPath.read(mdmsData, jsonPathForWorksMuster);

        } catch (Exception e) {
            log.error("CalculationService::populateAttendanceHours::Error parsing mdms response::"+e.getMessage());
            throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
        }

        if (!CollectionUtils.isEmpty(musterRes)) {
            for (LinkedHashMap<String,String> codeValueMap : musterRes) {
                String code = codeValueMap.get("code");
                String value = codeValueMap.get("value");
                switch (code) {
                    case HALF_DAY_NUM_HOURS :
                        halfDayNumHours = Integer.parseInt(value);
                        break;
                    case FULL_DAY_NUM_HOURS :
                        fullDayNumHours =Integer.parseInt(value);
                        break;
                    case ROUND_OFF_HOURS :
                        isRoundOffHours = BooleanUtils.toBoolean(value);
                        break;
                    default:
                }

            }
        }

    }

    /**
     * Set the additionalDetails of the individual
     * @param individualEntry
     * @param individualEntriesFromRequest
     * @param mdmsData
     */
    private void setAdditionalDetails(IndividualEntry individualEntry, List<IndividualEntry> individualEntriesFromRequest, Object mdmsData, Individual matchedIndividual, BankAccount bankAccount, boolean isCreate) {

        String skillCode = null;
        if (!CollectionUtils.isEmpty(individualEntriesFromRequest)) {
            IndividualEntry individualEntryRequest = individualEntriesFromRequest.stream()
                                                        .filter(individual -> individual.getIndividualId().equalsIgnoreCase(individualEntry.getIndividualId()))
                                                        .findFirst().orElse(null);
            try {
                if (individualEntryRequest != null && individualEntryRequest.getAdditionalDetails() != null) {
                    JsonNode node = mapper.readTree(mapper.writeValueAsString(individualEntryRequest.getAdditionalDetails()));
                    skillCode = node.findValue("code").textValue();
                }
            } catch (IOException e) {
                log.error("CalculationService::setAdditionalDetails::Failed to parse additionalDetail object from request "+e);
                throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object from request");
            }
        }

        //Update the skill value based on the code from request or set as default skill
        musterRollServiceUtil.populateAdditionalDetails(mdmsData, individualEntry, skillCode, matchedIndividual, bankAccount, isCreate);
    }

    /**
     * Fetch the individual details - Name, Father's name and Aadhar details from individual service
     * @param ids
     *
     */
    private List<Individual> fetchIndividualDetails(List<String> ids,RequestInfo requestInfo, String tenantId, MusterRoll musterRoll){
        // fetch the individual details from individual service
        StringBuilder uri = new StringBuilder();
        uri.append(config.getIndividualHost()).append(config.getIndividualSearchEndpoint());
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri.toString())
                .queryParam("limit",100)
                .queryParam("offset",0)
                .queryParam("tenantId",tenantId);

        IndividualSearch individualSearch = IndividualSearch.builder().id(ids).build();
        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                                    .requestInfo(requestInfo).individual(individualSearch).build();

        IndividualBulkResponse response = null;
        log.info("CalculationService::fetchIndividualDetails::call individual search with tenantId::"+tenantId
                +"::individual ids::"+ids);

        try {
            response  = restTemplate.postForObject(uriBuilder.toUriString(),individualSearchRequest,IndividualBulkResponse.class);
        }  catch (HttpClientErrorException | HttpServerErrorException httpClientOrServerExc) {
            log.error("CalculationService::fetchIndividualDetails::Error thrown from individual search service::"+httpClientOrServerExc.getStatusCode());
            throw new CustomException("INDIVIDUAL_SEARCH_SERVICE_EXCEPTION","Error thrown from individual search service::"+httpClientOrServerExc.getStatusCode());
        }

        if (response == null || CollectionUtils.isEmpty(response.getIndividual())) {
            StringBuilder exceptionMessage = new StringBuilder();
            exceptionMessage.append("Indiviudal search returned empty response for registerId ");
            exceptionMessage.append(musterRoll.getRegisterId());
            exceptionMessage.append(" with startDate - ");
            exceptionMessage.append(musterRoll.getStartDate());
            exceptionMessage.append(" and endDate - ");
            exceptionMessage.append(musterRoll.getEndDate());
            throw new CustomException("INDIVIDUAL_SEARCH_SERVICE_EMPTY",exceptionMessage.toString());
        }

        log.info("CalculationService::fetchIndividualDetails::Individual search fetched successfully");
        return response.getIndividual();

    }

    /**
     * Fetch the bank account details from bankAccount service
     * @param ids
     *
     */
    private List<BankAccount> fetchBankaccountDetails(List<String> ids,RequestInfo requestInfo, String tenantId){
        // fetch the bank account details from bank account service
        StringBuilder uri = new StringBuilder();
        uri.append(config.getBankaccountsHost()).append(config.getBankaccountsSearchEndpoint());
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri.toString());

        BankAccountSearchCriteria bankAccountSearchCriteria = BankAccountSearchCriteria.builder().tenantId(tenantId)
                .serviceCode("IND").referenceId(ids).build();
        Pagination pagination = Pagination.builder().limit(100d).build();
        BankAccountSearchRequest bankAccountSearchRequest = BankAccountSearchRequest.builder().
                requestInfo(requestInfo).bankAccountDetails(bankAccountSearchCriteria).pagination(pagination).build();

        BankAccountResponse response = null;
        log.info("CalculationService::fetchBankaccountDetails::call bankaccounts search with tenantId::"+tenantId
                +"::individual ids::"+ids);

        try {
            response  = restTemplate.postForObject(uriBuilder.toUriString(),bankAccountSearchRequest,BankAccountResponse.class);
        }  catch (HttpClientErrorException | HttpServerErrorException httpClientOrServerExc) {
            log.error("CalculationService::fetchBankaccountDetails::Error thrown from bankaccounts search service::"+httpClientOrServerExc.getStatusCode());
            throw new CustomException("BANKACCOUNTS_SEARCH_SERVICE_EXCEPTION","Error thrown from bankaccounts search service::"+httpClientOrServerExc.getStatusCode());
        }

        if (response == null || CollectionUtils.isEmpty(response.getBankAccounts())) {
            return new ArrayList<>();
        }

        log.info("CalculationService::fetchBankaccountDetails::Individual search fetched successfully");
        return response.getBankAccounts();

    }

    /**
     * Fetches the id of the attendance log
     * @return
     */
    private String getAttendanceLogId(List<AttendanceLog> attendanceLogList, String individualId, LocalDateTime timestamp, String type) {
         AttendanceLog attendanceLog = null;
         BigDecimal time = new BigDecimal(timestamp.atZone(ZoneId.of(config.getTimeZone())).toInstant().toEpochMilli());
         attendanceLog = attendanceLogList.stream()
                                            .filter(attnLog -> attnLog.getIndividualId().equalsIgnoreCase(individualId)
                                                        && attnLog.getTime().compareTo(time) == 0 && attnLog.getType().equalsIgnoreCase(type))
                                            .findFirst().orElse(null);
         return attendanceLog != null ? attendanceLog.getId() : "";
    }

}