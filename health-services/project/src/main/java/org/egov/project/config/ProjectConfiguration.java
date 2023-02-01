package org.egov.project.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class ProjectConfiguration {

    @Value("${project.staff.kafka.create.topic}")
    private String createProjectStaffTopic;

    @Value("${project.staff.kafka.update.topic}")
    private String updateProjectStaffTopic;

    @Value("${project.beneficiary.kafka.create.topic}")
    private String createProjectBeneficiaryTopic;

    @Value("${project.beneficiary.kafka.update.topic}")
    private String updateProjectBeneficiaryTopic;

    @Value("${project.beneficiary.kafka.delete.topic}")
    private String deleteProjectBeneficiaryTopic;

    @Value("${project.beneficiary.consumer.bulk.create.topic}")
    private String bulkCreateProjectBeneficiaryTopic;

    @Value("${project.beneficiary.consumer.bulk.update.topic}")
    private String bulkUpdateProjectBeneficiaryTopic;

    @Value("${project.beneficiary.consumer.bulk.delete.topic}")
    private String bulkDeleteProjectBeneficiaryTopic;

    @Value("${idgen.project.beneficiary.id.format}")
    private String projectBeneficiaryIdFormat;

    @Value("${egov.household.host}")
    private String householdServiceHost;

    @Value("${egov.search.household.url}")
    private String householdServiceSearchUrl;

    @Value("${egov.individual.host}")
    private String individualServiceHost;

    @Value("${egov.search.individual.url}")
    private String individualServiceSearchUrl;
}
