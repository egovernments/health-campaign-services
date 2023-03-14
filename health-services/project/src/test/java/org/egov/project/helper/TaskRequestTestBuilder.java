package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.TaskRequest;


public class TaskRequestTestBuilder {

    private final TaskRequest.TaskRequestBuilder builder;

    public TaskRequestTestBuilder() {
        this.builder = TaskRequest.builder();
    }

    public static TaskRequestTestBuilder builder() {
        return new TaskRequestTestBuilder();
    }

    public TaskRequest build() {
        return this.builder.build();
    }

    public TaskRequestTestBuilder withTask() {
        this.builder.task(TaskTestBuilder.builder().withTask().build());
        return this;
    }

    public TaskRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
