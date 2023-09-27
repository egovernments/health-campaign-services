package org.egov.referralmanagement.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
<<<<<<<< HEAD:health-services/referralmanagement/src/test/java/org/egov/referralmanagement/helper/SideEffectTestBuilder.java
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
========
import org.egov.common.models.referralmanagement.adverseevent.AdverseEvent;
>>>>>>>> 51cd6f6468 (HLM-3069: changed module name to referral management):health-services/referralmanagement/src/test/java/org/egov/referralmanagement/helper/AdverseEventTestBuilder.java

import java.util.ArrayList;
import java.util.Arrays;

public class SideEffectTestBuilder {

    private SideEffect.SideEffectBuilder builder;

    public SideEffectTestBuilder() {
        this.builder = SideEffect.builder();
    }

    public static SideEffectTestBuilder builder() {
        return new SideEffectTestBuilder();
    }

    public SideEffect build() {
        return this.builder.hasErrors(false).build();
    }

    public SideEffectTestBuilder withIdNull() {
        this.builder.taskId("some-task-id")
                .clientReferenceId("sideEffectClientReferenceId")
                .id(null)
                .taskClientReferenceId("null")
                .symptoms(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id")
                .rowVersion(1);
        return this;
    }

    public SideEffectTestBuilder withId() {
        withIdNull().builder.id("some-id").taskId("some-task-id")
                .clientReferenceId("sideEffectClientReferenceId")
                .taskClientReferenceId("null")
                .symptoms(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id");
        return this;
    }

    public SideEffectTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public SideEffectTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public SideEffectTestBuilder goodSideEffect() {
        this.builder.id("some-id").taskId("some-task-id")
                .clientReferenceId("sideEffectClientReferenceId")
                .taskClientReferenceId("null")
                .symptoms(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id")
                .rowVersion(1)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .clientAuditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public SideEffectTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public SideEffectTestBuilder withDeleted() {
        this.builder.isDeleted(true);
        return this;
    }
}
