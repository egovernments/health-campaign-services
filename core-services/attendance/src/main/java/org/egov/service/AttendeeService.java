package org.egov.service;

import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.enrichment.AttendeeEnrichmentService;
import org.egov.kafka.Producer;
import org.egov.repository.AttendeeRepository;
import org.egov.util.ResponseInfoFactory;
import org.egov.validator.AttendanceServiceValidator;
import org.egov.validator.AttendeeServiceValidator;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendeeCreateRequest;
import org.egov.web.models.AttendeeDeleteRequest;
import org.egov.web.models.AttendeeSearchCriteria;
import org.egov.web.models.IndividualEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AttendeeService {
    @Autowired
    private AttendeeServiceValidator attendeeServiceValidator;

    @Autowired
    private ResponseInfoFactory responseInfoFactory;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private AttendanceRegisterService attendanceRegisterService;

    @Autowired
    private AttendanceServiceValidator attendanceServiceValidator;

    @Autowired
    private AttendeeEnrichmentService attendeeEnrichmentService;

    @Autowired
    private AttendanceServiceConfiguration attendanceServiceConfiguration;

    @Autowired
    private Producer producer;


    /**
     * Create Attendee
     *
     * @param attendeeCreateRequest
     * @return
     */
    public AttendeeCreateRequest createAttendee(AttendeeCreateRequest attendeeCreateRequest) {
        //incoming createRequest validation
        log.info("validating create attendee request parameters");
        attendeeServiceValidator.validateAttendeeCreateRequestParameters(attendeeCreateRequest);

        //extract registerIds and attendee IndividualIds from client request
        String tenantId = attendeeCreateRequest.getAttendees().get(0).getTenantId();
        List<String> attendeeIds = extractAttendeeIdsFromCreateRequest(attendeeCreateRequest);
        List<String> registerIds = extractRegisterIdsFromCreateRequest(attendeeCreateRequest);

        //db call to get the attendeeList data
        List<IndividualEntry> attendeeListFromDB = getAttendees(registerIds,attendeeIds);

        //db call to get registers from db
        List<AttendanceRegister> attendanceRegisterListFromDB = getAttendanceRegisters(attendeeCreateRequest,registerIds,tenantId);

        //validate registers from request with registers from DB
        log.info("validating register ids from request against DB");
        attendanceServiceValidator.validateRegisterAgainstDB(registerIds, attendanceRegisterListFromDB, tenantId);


        //validator call by passing attendee request and the data from db call
        log.info("attendeeServiceValidator called to validate Create attendee request");
        attendeeServiceValidator.validateAttendeeOnCreate(attendeeCreateRequest, attendeeListFromDB, attendanceRegisterListFromDB);

        //enrichment call by passing attendee request and data from db call
        log.info("attendeeServiceValidator called to enrich Create attendee request");
        attendeeEnrichmentService.enrichAttendeeOnCreate(attendeeCreateRequest);

        //push to producer
        log.info("attendee objects pushed via producer");
        producer.push(attendanceServiceConfiguration.getSaveAttendeeTopic(), attendeeCreateRequest);
        log.info("attendees present in Create attendee request are enrolled to the registers");
        return attendeeCreateRequest;
    }

    public List<IndividualEntry> getAttendees(List<String> registerIds,List<String> attendeeIds){
        AttendeeSearchCriteria attendeeSearchCriteria = AttendeeSearchCriteria.builder().registerIds(registerIds).individualIds(attendeeIds).build();
        List<IndividualEntry> attendeeListFromDB = attendeeRepository.getAttendees(attendeeSearchCriteria);
        log.info("attendee List received From DB : " + attendeeListFromDB.size());
        return attendeeListFromDB;
    }

    public List<AttendanceRegister> getAttendanceRegisters(AttendeeCreateRequest attendeeCreateRequest,List<String> registerIds,String tenantId){
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(attendeeCreateRequest.getRequestInfo()).build();
        List<AttendanceRegister> attendanceRegisterListFromDB = attendanceRegisterService.getAttendanceRegisters(requestInfoWrapper, registerIds, tenantId);
        log.info("attendance register List received From DB : " + attendanceRegisterListFromDB.size());
        return attendanceRegisterListFromDB;
    }

    public List<AttendanceRegister> getAttendanceRegisters(AttendeeDeleteRequest attendeeDeleteRequest,List<String> registerIds,String tenantId){
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(attendeeDeleteRequest.getRequestInfo()).build();
        List<AttendanceRegister> attendanceRegisterListFromDB = attendanceRegisterService.getAttendanceRegisters(requestInfoWrapper, registerIds, tenantId);
        log.info("attendance register List received From DB : " + attendanceRegisterListFromDB.size());
        return attendanceRegisterListFromDB;
    }

    /**
     * Update(Soft Delete) the given attendee
     *
     * @param
     * @return
     */
    public AttendeeDeleteRequest deleteAttendee(AttendeeDeleteRequest attendeeDeleteRequest) {
        //incoming deleteRequest validation
        log.info("validating delete attendee request parameters");
        Map<String, String> errorMap = new HashMap<>();
        attendeeServiceValidator.validateAttendeeDeleteRequestParameters(attendeeDeleteRequest, errorMap);

        //extract registerIds and attendee IndividualIds from client request
        String tenantId = attendeeDeleteRequest.getAttendees().get(0).getTenantId();
        List<String> attendeeIds = extractAttendeeIdsFromDeleteRequest(attendeeDeleteRequest);
        List<String> registerIds = extractRegisterIdsFromDeleteRequest(attendeeDeleteRequest);

        //db call to get the attendeeList data
        List<IndividualEntry> attendeeListFromDB = getAttendees(registerIds,attendeeIds);

        //db call to get registers from db
        List<AttendanceRegister> attendanceRegisterListFromDB = getAttendanceRegisters(attendeeDeleteRequest,registerIds,tenantId);

        //validate request registers with registers from DB
        log.info("validating register ids from request against DB");
        attendanceServiceValidator.validateRegisterAgainstDB(registerIds, attendanceRegisterListFromDB, tenantId);


        //validator call by passing attendee request and the data from db call
        log.info("validating delete attendee request");
        attendeeServiceValidator.validateAttendeeOnDelete(attendeeDeleteRequest, attendeeListFromDB, attendanceRegisterListFromDB);

        //enrichment call by passing attendee request and data from db call
        log.info("enriching delete attendee request");
        attendeeEnrichmentService.enrichAttendeeOnDelete(attendeeDeleteRequest, attendeeListFromDB);

        //push to producer
        log.info("attendee objects updated via producer");
        producer.push(attendanceServiceConfiguration.getUpdateAttendeeTopic(), attendeeDeleteRequest);
        log.info("attendees present in delete attendee request are deenrolled from the registers");
        return attendeeDeleteRequest;
    }


    private List<String> extractRegisterIdsFromCreateRequest(AttendeeCreateRequest attendeeCreateRequest) {
        List<IndividualEntry> attendeeListFromRequest = attendeeCreateRequest.getAttendees();
        List<String> registerIds = new ArrayList<>();
        for (IndividualEntry attendee : attendeeListFromRequest) {
            registerIds.add(attendee.getRegisterId());
        }
        return registerIds;
    }

    private List<String> extractAttendeeIdsFromCreateRequest(AttendeeCreateRequest attendeeCreateRequest) {
        List<IndividualEntry> attendeeListFromRequest = attendeeCreateRequest.getAttendees();
        List<String> attendeeIds = new ArrayList<>();
        for (IndividualEntry attendee : attendeeListFromRequest) {
            attendeeIds.add(attendee.getIndividualId());
        }
        return attendeeIds;
    }

    private List<String> extractRegisterIdsFromDeleteRequest(AttendeeDeleteRequest attendeeDeleteRequest) {
        List<IndividualEntry> attendeeListFromRequest = attendeeDeleteRequest.getAttendees();
        List<String> registerIds = new ArrayList<>();
        for (IndividualEntry attendee : attendeeListFromRequest) {
            registerIds.add(attendee.getRegisterId());
        }
        return registerIds;
    }

    private List<String> extractAttendeeIdsFromDeleteRequest(AttendeeDeleteRequest attendeeDeleteRequest) {
        List<IndividualEntry> attendeeListFromRequest = attendeeDeleteRequest.getAttendees();
        List<String> attendeeIds = new ArrayList<>();
        for (IndividualEntry attendee : attendeeListFromRequest) {
            attendeeIds.add(attendee.getIndividualId());
        }
        return attendeeIds;
    }
}
