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


    private static Map<String, AttendanceRegister> attendanceRegisterMapCache = new ConcurrentHashMap<>();

    private static Map<String, Name> attendeesIdNameCache = new ConcurrentHashMap<>();

    public AttendanceRegisterService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, UserService userService, IndividualService individualService) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.userService = userService;
        this.individualService = individualService;
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
            }
            return attendanceRegister;
        }
        return null;
    }

    public Map<String, Name> fetchAttendeesInfo(List<String> individualIds, String tenantId) {
        return individualIds.stream().collect(Collectors.toMap(id -> id, id -> {
            if (attendeesIdNameCache.containsKey(id)) {
                return attendeesIdNameCache.get(id);
            } else {
                Individual individual = individualService.getIndividualById(id, tenantId);
                Name attendeeName = (individual != null) ? individual.getName() : null;
                if (attendeeName != null) {
                    attendeesIdNameCache.put(id, attendeeName);
                }
                return attendeeName;
            }
        }));

    }

}
