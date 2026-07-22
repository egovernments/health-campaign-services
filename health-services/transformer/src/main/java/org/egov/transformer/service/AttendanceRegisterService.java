package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.individual.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.attendance.AttendanceRegister;
import org.egov.transformer.models.attendance.AttendanceRegisterRequest;
import org.egov.transformer.models.attendance.AttendanceRegisterResponse;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AttendanceRegisterService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    private final UserService userService;

    private final IndividualService individualService;

    private final TransformerErrorProducer errorProducer;


    private static Map<String, AttendanceRegister> attendanceRegisterMapCache = new ConcurrentHashMap<>();

    private static Map<String, String> attendeesIdUserIdCache = new ConcurrentHashMap<>();

    public AttendanceRegisterService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, UserService userService, IndividualService individualService, TransformerErrorProducer errorProducer) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.userService = userService;
        this.individualService = individualService;
        this.errorProducer = errorProducer;
    }


    public AttendanceRegister findAttendanceRegisterById(String registerId, String tenantId, String createdUserUuid) {
        Long userServiceId = userService.getUserServiceId(tenantId, createdUserUuid);
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("transformer-uuid").id(userServiceId).build()).build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getAttendanceHost())
                    .append(properties.getAttendanceRegisterSearchUrl())
                    .append("?ids=").append(registerId)
                    .append("&tenantId=").append(tenantId);
            AttendanceRegisterResponse response = serviceRequestClient.fetchResult(uri, AttendanceRegisterRequest.builder()
                    .requestInfo(requestInfo)
                    .build(), AttendanceRegisterResponse.class);
            if (response.getAttendanceRegister() != null && !CollectionUtils.isEmpty(response.getAttendanceRegister())) {
                AttendanceRegister attendanceRegister = response.getAttendanceRegister().get(0);
                attendanceRegisterMapCache.put(registerId, attendanceRegister);
                return attendanceRegister;
            }
        } catch (Exception e) {
            log.info("Error while fetching attendance register with registerId: {}", registerId);
            log.error("ERROR: {}", ExceptionUtils.getStackTrace(e));

            AttendanceRegister attendanceRegister = attendanceRegisterMapCache.getOrDefault(registerId, null);

            if (attendanceRegister != null) {
                log.info("ATTENDANCE_REGISTER with registerId {} FETCHED_FROM_CACHE.", registerId);
            } else {
                log.warn("UNABLE_TO_FETCH_ATTENDANCE_REGISTER with registerId {} from both source and cache.", registerId);
                // NOTE: unlike BoundaryService/ProjectService (which re-throw so the consumer records
                // the error once), this method deliberately SWALLOWS the exception and returns null/cached
                // so transformation continues. Because it does not propagate, the consumer never sees it,
                // so this is the only place the failure can be recorded -> we emit here on purpose.
                // Do not remove this to "match" the rethrow sites. topic is null on purpose and resolved
                // from the thread-local source topic set by the consumer.
                errorProducer.sendToErrorTopic(registerId, null, e);
            }
            return attendanceRegister;
        }
        return null;
    }

    public Map<String, String> fetchAttendeesInfo(List<String> individualIds, String tenantId) {

        Map<String, String> attendeesIdUserId = new HashMap<>();
        for (String id : individualIds) {
            if (attendeesIdUserIdCache.containsKey(id)) {
                 attendeesIdUserId.put(id, attendeesIdUserIdCache.get(id));
            } else {
                Individual individual = individualService.getIndividualById(id, tenantId);
                if (individual != null) {
                    attendeesIdUserId.put(id, individual.getUserUuid());
                    attendeesIdUserIdCache.put(id, individual.getUserUuid());
                }
            }
        }
        return attendeesIdUserId;
    }

}
