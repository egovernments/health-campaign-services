package org.egov.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.StaffPermissionRequest;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class StaffRequestBuilderTest {

    public static StaffPermissionRequest getStaffPermissionRequest() {
        StaffPermissionRequest staffPermissionRequest = StaffPermissionRequest.builder().requestInfo(getRequestInfo())
                .staff(getStaff()).build();
        return staffPermissionRequest;
    }


    public static List<StaffPermission> getStaff() {
        StaffPermission staffOne = StaffPermission.builder().id("03901adb-07c3-4539-9346-4ee5c87e5e1c").userId("8ybdd-3rdhd")
                .registerId("97ed7da3-753e-426a-b0b0-95dd61029785").tenantId("pb.amritsar").enrollmentDate(new BigDecimal("1670421853937"))
                .denrollmentDate(null).auditDetails(getAuditDetails()).build();

        StaffPermission staffTwo = StaffPermission.builder().id("156d07fd-2c0c-4882-be03-6b68b98fb15e").userId("8ybdd-3rdhe")
                .registerId("97ed7da3-753e-426a-b0b0-95dd61029785").tenantId("pb.amritsar").enrollmentDate(new BigDecimal("1670424209106"))
                .denrollmentDate(null).auditDetails(getAuditDetails()).build();

        List<StaffPermission> staffList = new ArrayList<>(Arrays.asList(staffOne, staffTwo));
        return staffList;
    }


    public static AuditDetails getAuditDetails() {
        AuditDetails auditDetails = AuditDetails.builder().createdBy("11b0e02b-0145-4de2-bc42-c97b96264807")
                .lastModifiedBy("11b0e02b-0145-4de2-bc42-c97b96264807").createdTime(Long.valueOf("1672129633890"))
                .lastModifiedTime(Long.valueOf("1672129855564")).build();
        return auditDetails;
    }

    public static RequestInfo getRequestInfo() {
        Role role = new Role(1L, "Organization staff", "ORG_STAFF", "pb.amritsar");
        List<Role> roles = new ArrayList<>();
        roles.add(role);
        User userInfo = User.builder().id(172L).uuid("5ce80dd3-b1c0-42fd-b8f6-a2be456db31c").userName("8070102021").name("test3").mobileNumber("8070102021")
                .emailId("xyz@egovernments.org").type("EMPLOYEE").roles(roles).build();
        RequestInfo requestInfo = RequestInfo.builder().apiId("attendance-services").msgId("search with from and to values").userInfo(userInfo).build();
        return requestInfo;
    }

    public static Object getMdmsResponseForValidTenant() {

        Object mdmsResponse = null;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            File file = new File("src/test/resources/TenantMDMSData.json");
            String exampleRequest = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            mdmsResponse = objectMapper.readValue(exampleRequest, Object.class);
        } catch (Exception exception) {
            log.error("AttendeeRequestBuilderTest::getMdmsResponse::Exception while parsing mdms json");
        }
        return mdmsResponse;
    }

    public static Object getMdmsResponseForInvalidTenant() {

        Object mdmsResponse = null;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            File file = new File("src/test/resources/InvalidTenantMDMSData.json");
            String exampleRequest = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            mdmsResponse = objectMapper.readValue(exampleRequest, Object.class);
        } catch (Exception exception) {
            log.error("AttendeeRequestBuilderTest::getMdmsResponse::Exception while parsing mdms json");
        }
        return mdmsResponse;
    }

    public static ResponseInfo getResponseInfo_Success() {
        ResponseInfo responseInfo = ResponseInfo.builder().apiId("attendance-services").ver(null).ts(null).resMsgId(null).msgId("search with from and to values")
                .status("successful").build();
        return responseInfo;
    }
}
