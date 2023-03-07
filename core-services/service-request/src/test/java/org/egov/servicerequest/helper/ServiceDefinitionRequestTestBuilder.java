package org.egov.servicerequest.helper;

import org.egov.common.contract.request.RequestInfo;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;

public class ServiceDefinitionRequestTestBuilder {

    private ServiceDefinitionRequest.ServiceDefinitionRequestBuilder builder;
    public ServiceDefinitionRequestTestBuilder() {
        this.builder=ServiceDefinitionRequest.builder();
    }
    public static ServiceDefinitionRequestTestBuilder builder(){
        return new ServiceDefinitionRequestTestBuilder();
    }
    public ServiceDefinitionRequest build() {
        return this.builder.build();
    }
    public ServiceDefinitionRequestTestBuilder withServiceDefinition() {
        this.builder.serviceDefinition(ServiceDefinitionTestBuilder.builder().withServiceDefinition().build());
        return this;
    }

    public ServiceDefinitionRequestTestBuilder withRequestInfo(RequestInfo... args) {
        this.builder.requestInfo(args.length > 0 ? args[0] :
                RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
