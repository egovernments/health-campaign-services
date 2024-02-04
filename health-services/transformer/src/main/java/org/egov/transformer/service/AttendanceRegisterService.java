package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.individual.*;
import org.egov.common.models.project.ProjectResponse;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.attendance.AttendanceRegister;
import org.egov.transformer.models.attendance.AttendanceRegisterRequest;
import org.egov.transformer.models.attendance.AttendanceRegisterResponse;
import org.egov.transformer.models.attendance.AttendanceRegisterSearchCriteria;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.transformer.Constants.*;

@Service
@Slf4j
public class AttendanceRegisterService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    private final UserService userService;

    private final CommonUtils commonUtils;

    public AttendanceRegisterService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, UserService userService, CommonUtils commonUtils) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.userService = userService;
        this.commonUtils = commonUtils;
    }


    public AttendanceRegister findAttendanceRegisterById(List registerIds, String tenantId, String createdUserUuid) {
        Long userServiceId = Long.valueOf(userService.getUserInfo(createdUserUuid, tenantId).get(ID));
        AttendanceRegisterSearchCriteria attendanceRegisterSearchCriteria = AttendanceRegisterSearchCriteria.builder()
                .ids(registerIds).tenantId(tenantId).build();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").id(userServiceId).build())
                .build();

        AttendanceRegisterResponse response;
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequest.builder()
                .requestInfo(requestInfo).build();

        StringBuilder uri = new StringBuilder();
        uri.append(properties.getAttendanceHost())
                .append(properties.getAttendanceRegisterSearchUrl())
                .append("?ids=").append(String.join(COMMA,registerIds))
                .append("&tenantId=").append(tenantId);
        log.info("Register Request is : {}, uri is : {}", attendanceRegisterRequest.toString(), uri);
        try {
            response = serviceRequestClient.fetchResult(uri,
                    attendanceRegisterRequest,
                    AttendanceRegisterResponse.class);
            log.info(response.toString());
        } catch (Exception e) {
            log.error("ERROR WHILE FETCHING ATTENDANCE REGISTER WITH REGISTER_ID: {}, ERROR: {}", registerIds, ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }

        return response.getAttendanceRegister().get(0);

    }

}
