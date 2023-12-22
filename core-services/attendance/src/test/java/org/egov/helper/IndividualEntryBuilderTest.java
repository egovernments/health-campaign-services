package org.egov.helper;



import org.egov.web.models.IndividualEntry;

import java.math.BigDecimal;

public class IndividualEntryBuilderTest {
    private IndividualEntry.IndividualEntryBuilder builder;
    public IndividualEntryBuilderTest() {
        this.builder = IndividualEntry.builder();
    }

    public static StaffPermissionBuilderTest builder() {
        return new StaffPermissionBuilderTest();
    }

    public IndividualEntry build() {
        return this.builder.build();
    }

    public void addGoodStaffPermission(){
        this.builder.id("some_id")
                .tenantId("some_tenantId")
                .registerId("some_registerId")
                .individualId("some_individualId")
                .enrollmentDate(BigDecimal.valueOf(1640995200000L))
                .denrollmentDate(BigDecimal.valueOf(1703980800000L))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .additionalDetails(AdditionalFields.builder().build());
    }

}
