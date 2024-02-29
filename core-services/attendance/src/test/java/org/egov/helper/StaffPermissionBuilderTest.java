package org.egov.helper;

import org.egov.web.models.StaffPermission;

import java.math.BigDecimal;

public class StaffPermissionBuilderTest {

    private StaffPermission.StaffPermissionBuilder builder;
    public StaffPermissionBuilderTest() {
        this.builder = StaffPermission.builder();
    }

    public static StaffPermissionBuilderTest builder() {
        return new StaffPermissionBuilderTest();
    }

    public StaffPermission build() {
        return this.builder.build();
    }

    public void addGoodStaffPermission(){
        this.builder.id("some_id")
                .tenantId("some_tenantId")
                .registerId("some_registerId")
                .userId("some_userId")
                .enrollmentDate(BigDecimal.valueOf(1640995200000L))
                .denrollmentDate(BigDecimal.valueOf(1703980800000L))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .additionalDetails(AdditionalFields.builder().build());
    }

}
