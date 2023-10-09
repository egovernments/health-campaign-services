package org.egov.transformer.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class TransformerProperties {

    @Value("${transformer.producer.bulk.project.task.index.v1.topic}")
    private String transformerProducerBulkProjectTaskIndexV1Topic;

    @Value("${transformer.producer.bulk.project.staff.index.v1.topic}")
    private String transformerProducerBulkProjectStaffIndexV1Topic;

    @Value("${transformer.producer.service.task.index.v1.topic}")
    private String transformerProducerServiceTaskIndexV1Topic;

    @Value("${transformer.producer.bulk.project.index.v1.topic}")
    private String transformerProducerBulkProjectIndexV1Topic;

    @Value("${transformer.producer.bulk.stock.index.v1.topic}")
    private String transformerProducerBulkStockIndexV1Topic;

    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.search.project.url}")
    private String projectSearchUrl;

    @Value("${egov.search.project.beneficiary.url}")
    private String projectBeneficiarySearchUrl;

    @Value("${egov.household.host}")
    private String householdHost;

    @Value("${egov.search.household.url}")
    private String householdSearchUrl;

    @Value("${egov.location.host}")
    private String locationHost;

    @Value("${egov.location.endpoint}")
    private String locationSearchUrl;

    @Value("${egov.facility.host}")
    private String facilityHost;

    @Value("${egov.search.facility.url}")
    private String facilitySearchUrl;

    @Value("${egov.search.servicedefinition.url}")
    private String serviceDefinitionSearchUrl;

    @Value("${egov.servicedefinition.host}")
    private String serviceDefinitionHost;

    @Value("${search.api.limit:100}")
    private String searchApiLimit;

    @Value("${project.mdms.module}")
    private String mdmsModule;

    @Value("${boundary.label.name.province}")
    private String province;

    @Value("${boundary.label.name.locality}")
    private String locality;

    @Value("${boundary.label.name.district}")
    private String district;

    @Value("${boundary.label.name.village}")
    private String Village;

    @Value("${boundary.label.name.administrativeProvince}")
    private String administrativeProvince;

    @Value("${egov.program.mandate.limit}")
    private Integer programMandateLimit;

    @Value("${egov.program.mandate.dividing.factor}")
    private Double programMandateDividingFactor;

    @Value("${egov.program.mandate.comment}")
    private String programMandateComment;

    @Value("${egov.timestamp.timeZone}")
    private String timeZone;


}
