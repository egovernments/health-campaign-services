package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskResource;

import java.util.Collections;

public class TaskTestBuilder {

    private final Task.TaskBuilder builder;

    public TaskTestBuilder() {
        this.builder = Task.builder();
    }

    public static TaskTestBuilder builder() {
        return new TaskTestBuilder();
    }

    public Task build() {
        return this.builder.build();
    }

    public TaskTestBuilder withTask() {
        this.builder.id("some-id").actualEndDate(100L).actualStartDate(100L)
                .tenantId("default")
                .plannedStartDate(100L).plannedEndDate(101L)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .address(AddressTestBuilder.builder().withAddress().build())
                .resources(Collections.singletonList(TaskResource.builder()
                        .id("id101").tenantId("default").isDelivered(false)
                        .quantity("100").productVariantId("v101").build()))
                .isDeleted(false).rowVersion(0).projectBeneficiaryId("some-id")
                .projectId("some-id").createdBy("some-id")
                .createdDate(100L).status("status").build();
        return this;
    }
}
