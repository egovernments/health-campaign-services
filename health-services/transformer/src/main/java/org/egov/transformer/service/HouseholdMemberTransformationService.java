package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.HouseholdMemberIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
public abstract class HouseholdMemberTransformationService implements TransformationService<HouseholdMember>{
    protected final HouseholdMemberTransformationService.HouseholdMemberIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;
    protected final CommonUtils commonUtils;
    protected HouseholdMemberTransformationService(HouseholdMemberIndexV1Transformer transformer,
                                         Producer producer,
                                         TransformerProperties properties, CommonUtils commonUtils) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
        this.commonUtils = commonUtils;
    }

    @Override
    public void transform(List<HouseholdMember> payloadList) {
        log.info("transforming for ids {}", payloadList.stream()
                .map(HouseholdMember::getId).collect(Collectors.toList()));
        List<HouseholdMemberIndexV1> transformedPayloadList = payloadList.stream()
                .map(transformer::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(getTopic(),
                transformedPayloadList);
    }

    @Override
    public Operation getOperation() {
        return Operation.HOUSEHOLD_MEMBER;
    }

    public abstract String getTopic();

    @Component
    static class HouseholdMemberIndexV1Transformer implements
            Transformer<HouseholdMember, HouseholdMemberIndexV1> {

        private final ProjectService projectService;

        private final FacilityService facilityService;
        private final TransformerProperties properties;
        private final CommonUtils commonUtils;
        private UserService userService;
        HouseholdMemberIndexV1Transformer(ProjectService projectService, FacilityService facilityService,
                                TransformerProperties properties, CommonUtils commonUtils, UserService userService) {
            this.projectService = projectService;
            this.facilityService = facilityService;
            this.properties = properties;
            this.commonUtils = commonUtils;
            this.userService = userService;
        }

        @Override
        public List<HouseholdMemberIndexV1> transform(HouseholdMember householdMember) {

            String individualClientReferenceId = householdMember.getIndividualClientReferenceId();


            return Collections.singletonList(HouseholdMemberIndexV1.builder()
                    .householdMember(householdMember)
//                  .gender("male")
//                  .dateOfBirth("")
//                  .age()
                    .build());
        }


    }
}
