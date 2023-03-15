package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskResource;

import java.util.Arrays;

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
                .hasErrors(Boolean.FALSE)
                .tenantId("default")
                .plannedStartDate(100L).plannedEndDate(101L)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .address(AddressTestBuilder.builder().withAddress().build())
                .resources(Arrays.asList(TaskResource.builder().tenantId("default").isDelivered(false)
                        .quantity(100L).productVariantId("v101").build(),
                        TaskResource.builder().tenantId("default").isDelivered(false)
                                .quantity(100L).productVariantId("v101").build()))
                .isDeleted(false).rowVersion(0).projectBeneficiaryId("some-id")
                .projectId("some-id").createdBy("some-id")
                .createdDate(100L).status("status").build();
        return this;
    }
}
