package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskResource;

import java.util.Arrays;

public class TaskTestBuilder {

    private final Task.TaskBuilder<Task, ?> builder;

    public TaskTestBuilder() {
        this.builder = (Task.TaskBuilder<Task, ?>) Task.builder();
    }

    public static TaskTestBuilder builder() {
        return new TaskTestBuilder();
    }

    public Task build() {
        return this.builder.build();
    }

    public TaskTestBuilder withTask() {
        this.builder.actualEndDate(100L).actualStartDate(100L)
                .plannedStartDate(100L).plannedEndDate(101L)
                .address(AddressTestBuilder.builder().withAddress().build())
                .resources(Arrays.asList(TaskResource.builder().tenantId("default").isDelivered(false)
                        .quantity(100.0).productVariantId("v101").build(),
                        TaskResource.builder().tenantId("default").isDelivered(false)
                                .quantity(100.0).productVariantId("v101").build()))
                .projectId("some-id").createdBy("some-id")
                .createdDate(100L).status("status")
                .isDeleted(false).projectBeneficiaryId("some-id")
                .rowVersion(0)
                .hasErrors(Boolean.FALSE)
                .tenantId("default")
                .id("some-id")
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .build();
        return this;
    }
}
