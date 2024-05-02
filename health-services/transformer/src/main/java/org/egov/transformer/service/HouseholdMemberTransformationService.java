package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.HouseholdMemberIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
public abstract class HouseholdMemberTransformationService implements TransformationService<HouseholdMember> {
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

        private final IndividualService individualService;

        private final HouseholdService householdService;

        private final CommonUtils commonUtils;

        private final UserService userService;

        HouseholdMemberIndexV1Transformer(IndividualService individualService, HouseholdService householdService, CommonUtils commonUtils, UserService userService) {
            this.individualService = individualService;
            this.householdService = householdService;
            this.commonUtils = commonUtils;
            this.userService = userService;
        }

        @Override
        public List<HouseholdMemberIndexV1> transform(HouseholdMember householdMember) {

            Map<String, String> boundaryHierarchy = null;
            List<Double> geoPoint = null;
            String individualClientReferenceId = householdMember.getIndividualClientReferenceId();
            Map<String, Object> individualDetails = individualService.findIndividualByClientReferenceId(individualClientReferenceId, householdMember.getTenantId());

            List<Household> households = householdService.searchHousehold(householdMember.getHouseholdClientReferenceId(), householdMember.getTenantId());
            String localityCode =null;
            if (!CollectionUtils.isEmpty(households) && households.get(0).getAddress() != null
                    && households.get(0).getAddress().getLocality() != null
                    && households.get(0).getAddress().getLocality().getCode() != null) {
                localityCode = households.get(0).getAddress().getLocality().getCode();
                geoPoint = commonUtils.getGeoPoint(households.get(0).getAddress());
            }
            if (localityCode != null) {
                boundaryHierarchy =commonUtils.getBoundaryHierarchyWithLocalityCode(localityCode, householdMember.getTenantId());
            }

            Map<String, String> userInfoMap = userService.
                    getUserInfo(householdMember.getTenantId(), householdMember.getClientAuditDetails().getLastModifiedBy());

            return Collections.singletonList(HouseholdMemberIndexV1.builder()
                    .householdMember(householdMember)
                    .boundaryHierarchy(boundaryHierarchy)
                    .userName(userInfoMap.get(USERNAME))
                    .role(userInfoMap.get(ROLE))
                    .nameOfUser(userInfoMap.get(NAME))
                    .userAddress(userInfoMap.get(CITY))
                    .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                    .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                    .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                    .geoPoint(geoPoint)
                    .localityCode(localityCode)
                    .taskDates(commonUtils.getDateFromEpoch(householdMember.getClientAuditDetails().getLastModifiedTime()))
                    .syncedDate(commonUtils.getDateFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                    .syncedTimeStamp(commonUtils.getTimeStampFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                    .build());
        }


    }
}
