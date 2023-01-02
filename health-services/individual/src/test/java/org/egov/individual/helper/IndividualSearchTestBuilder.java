package org.egov.individual.helper;

import org.egov.individual.web.models.IndividualSearch;

public class IndividualSearchTestBuilder {
    private IndividualSearch.IndividualSearchBuilder builder;

    public IndividualSearchTestBuilder() {
        this.builder = IndividualSearch.builder();
    }

    public static IndividualSearchTestBuilder builder() {
        return new IndividualSearchTestBuilder();
    }

    public IndividualSearch build() {
        return this.builder.build();
    }

    public IndividualSearchTestBuilder byId(String... args) {
        this.builder.id(args != null && args.length > 0 ? args[0] : "some-id");
        return this;
    }

    public IndividualSearchTestBuilder byClientReferenceId(String... args) {
        this.builder.clientReferenceId(args != null && args.length > 0 ? args[0] : "some-client-reference-id");
        return this;
    }

    public IndividualSearchTestBuilder byTenantId(String... args) {
        this.builder.tenantId(args != null && args.length > 0 ? args[0] : "some-tenant-id");
        return this;
    }
}
