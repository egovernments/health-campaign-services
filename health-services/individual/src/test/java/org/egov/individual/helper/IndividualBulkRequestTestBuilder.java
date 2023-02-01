package org.egov.individual.helper;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;

import java.util.Arrays;

public class IndividualBulkRequestTestBuilder {
    private IndividualBulkRequest.IndividualBulkRequestBuilder builder;

    public IndividualBulkRequestTestBuilder() {
        this.builder = IndividualBulkRequest.builder();
    }

    public static IndividualBulkRequestTestBuilder builder() {
        return new IndividualBulkRequestTestBuilder();
    }

    public IndividualBulkRequest build() {
        return this.builder.build();
    }

    public IndividualBulkRequestTestBuilder withIndividuals(Individual... args) {
        this.builder.individuals(Arrays.asList(args));
        return this;
    }

    public IndividualBulkRequestTestBuilder withRequestInfo(RequestInfo... args) {
        this.builder.requestInfo(args.length > 0 ? args[0] :
                RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

//    public IndividualBulkRequestTestBuilder withApiOperation(ApiOperation apiOperation) {
//        this.builder.apiOperation(apiOperation);
//        return this;
//    }
}
