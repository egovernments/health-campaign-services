package org.egov.servicerequest.helper;

import org.egov.servicerequest.web.models.AdditionalFields;
import org.egov.servicerequest.web.models.AttributeValue;
import org.egov.servicerequest.web.models.Service;

import java.util.Arrays;

public class ServiceTestBuilder {

    private final Service.ServiceBuilder builder;

    public ServiceTestBuilder() {
        this.builder = Service.builder();
    }

    public static ServiceTestBuilder builder() {
        return new ServiceTestBuilder();
    }

    public Service build() {
        return this.builder.build();
    }

    public ServiceTestBuilder withService() {
        this.builder.id("some-id")
                .tenantId("default")
                .serviceDefId("service-def-id")
                .referenceId("reference-id")
                .attributes(Arrays.asList(AttributeValue.builder()
                        .id("id")
                        .referenceId("reference-id")
                        .attributeCode("code")
                        .value("value")
                        .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                        .additionalDetails("additional-value").build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .additionalFields(AdditionalFields.builder().build())
                .accountId("account-id")
                .clientId("client-id").build();

        return this;
    }

    public ServiceTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }
}
