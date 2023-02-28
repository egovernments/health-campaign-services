package org.egov.servicerequest.helper;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceRequest;

import java.security.Provider;

public class ServiceRequestTestBuilder {

    private ServiceRequest.ServiceRequestBuilder builder;

    public ServiceRequestTestBuilder() {
        this.builder = ServiceRequest.builder();
    }

    public static ServiceRequestTestBuilder builder() {
        return new ServiceRequestTestBuilder();
    }

    public ServiceRequest build() {
        return this.builder.build();
    }

    public ServiceRequestTestBuilder withServices() {
        this.builder.service(ServiceTestBuilder.builder().withService().build());
        return this;
    }

    public ServiceRequestTestBuilder withRequestInfo(RequestInfo... args) {
        this.builder.requestInfo(args.length > 0 ? args[0] :
                RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
