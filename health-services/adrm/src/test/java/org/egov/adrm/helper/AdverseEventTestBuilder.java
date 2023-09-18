package org.egov.adrm.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.adrm.adverseevent.AdverseEvent;

import java.util.ArrayList;
import java.util.Arrays;

public class AdverseEventTestBuilder {

    private AdverseEvent.AdverseEventBuilder builder;

    public AdverseEventTestBuilder() {
        this.builder = AdverseEvent.builder();
    }

    public static AdverseEventTestBuilder builder() {
        return new AdverseEventTestBuilder();
    }

    public AdverseEvent build() {
        return this.builder.hasErrors(false).build();
    }

    public AdverseEventTestBuilder withIdNull() {
        this.builder.taskId("some-task-id")
                .clientReferenceId("adverseEventClientReferenceId")
                .id(null)
                .taskClientReferenceId("null")
                .symptoms(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id")
                .rowVersion(1);
        return this;
    }

    public AdverseEventTestBuilder withId() {
        withIdNull().builder.id("some-id").taskId("some-task-id")
                .clientReferenceId("adverseEventClientReferenceId")
                .taskClientReferenceId("null")
                .symptoms(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id");
        return this;
    }

    public AdverseEventTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public AdverseEventTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public AdverseEventTestBuilder goodAdverseEvent() {
        this.builder.id("some-id").taskId("some-task-id")
                .clientReferenceId("adverseEventClientReferenceId")
                .taskClientReferenceId("null")
                .symptoms(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id")
                .rowVersion(1)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .clientAuditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public AdverseEventTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public AdverseEventTestBuilder withDeleted() {
        this.builder.isDeleted(true);
        return this;
    }
}
