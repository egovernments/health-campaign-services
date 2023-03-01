package org.egov.servicerequest.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.servicerequest.web.models.AttributeDefinition;
import org.egov.servicerequest.web.models.AttributeValue;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceDefinition;

import java.util.Arrays;

public class ServiceDefinitionTestBuilder {
    private final ServiceDefinition.ServiceDefinitionBuilder builder;

    public ServiceDefinitionTestBuilder() {
        this.builder = ServiceDefinition.builder();
    }

    public static ServiceDefinitionTestBuilder builder() {
        return new ServiceDefinitionTestBuilder();
    }

    public ServiceDefinition build() {
        return this.builder.build();
    }

    public ServiceDefinitionTestBuilder withServiceDefinition() {
        this.builder.id("some-id")
                .tenantId("default")
                .code("code")
                .isActive(true)
                .attributes(Arrays.asList(AttributeDefinition.builder()
                        .id("id")
                        .referenceId("reference-id")
                        .tenantId("default")
                        .code("code")
                        .values(Arrays.asList("value"))
                        .isActive(true)
                        .dataType(AttributeDefinition.DataTypeEnum.STRING)
                        .required(true)
                        .regex("regex")
                        .order("order")
                        .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                        .additionalDetails("additional-value").build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .additionalDetails("value")
                .clientId("client-id").build();

        return this;
    }

    public ServiceDefinitionTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }
}
