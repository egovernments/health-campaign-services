package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.project.web.models.ApiOperation;
import org.egov.project.web.models.TaskRequest;

import java.util.Collections;

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
        this.builder.task(Collections.singletonList(TaskTestBuilder.builder().withTask().build()));
        return this;
    }

    public TaskRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public TaskRequestTestBuilder withApiOperationCreate() {
        this.builder.apiOperation(ApiOperation.CREATE);
        return this;
    }

    public TaskRequestTestBuilder withApiOperationDelete() {
        this.builder.apiOperation(ApiOperation.DELETE);
        return this;
    }

    public TaskRequestTestBuilder withApiOperationUpdate() {
        this.builder.apiOperation(ApiOperation.UPDATE);
        return this;
    }
}
