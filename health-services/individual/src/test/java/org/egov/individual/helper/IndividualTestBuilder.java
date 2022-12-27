package org.egov.individual.helper;

import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.Name;

public class IndividualTestBuilder {
    private Individual.IndividualBuilder builder;

    public IndividualTestBuilder() {
        this.builder = Individual.builder();
    }

    public static IndividualTestBuilder builder() {
        return new IndividualTestBuilder();
    }

    public Individual build() {
        return this.builder.build();
    }

    public IndividualTestBuilder withName(String... args) {
        this.builder.name(Name.builder()
                .familyName(args.length > 0 && args[0] != null ? args[0] : "some-family-name")
                .givenName(args.length > 1 && args[1] != null ? args[1] : "some-given-name")
                .build());
        return this;
    }

    public IndividualTestBuilder withId(String... args) {
        this.builder.id(args.length > 0 ? args[0] : "some-id");
        return this;
    }

    public IndividualTestBuilder withNoPropertiesSet(String... args) {
        this.builder.build();
        return this;
    }
}
