package org.egov.transformer.transformationservice;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.HouseholdMemberIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.HouseholdService;
import org.egov.transformer.service.IndividualService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.GENDER;

@Slf4j
@Component
public class HouseholdMemberTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private final IndividualService individualService;
    private final UserService userService;
    private final HouseholdService householdService;

    public HouseholdMemberTransformationService(TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, IndividualService individualService, UserService userService, HouseholdService householdService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.individualService = individualService;
        this.userService = userService;
        this.householdService = householdService;
    }

    public void transform(List<HouseholdMember> householdMemberList) {
        log.info("transforming for HHM id's {}", householdMemberList.stream()
                .map(HouseholdMember::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerHouseholdMemberIndexV1Topic();
        List<HouseholdMemberIndexV1> householdMemberIndexV1List = householdMemberList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for HHM id's {}", householdMemberIndexV1List.stream()
                .map(HouseholdMemberIndexV1::getHouseholdMember)
                .map(HouseholdMember::getId)
                .collect(Collectors.toList()));
        producer.push(topic, householdMemberIndexV1List);
    }

    private HouseholdMemberIndexV1 transform(HouseholdMember householdMember) {
        Map<String, String> boundaryHierarchy = null;
        List<Double> geoPoint = null;
        String individualClientReferenceId = householdMember.getIndividualClientReferenceId();
        Map<String, Object> individualDetails = individualService.findIndividualByClientReferenceId(individualClientReferenceId, householdMember.getTenantId());

        List<Household> households = householdService.searchHousehold(householdMember.getHouseholdClientReferenceId(), householdMember.getTenantId());
        String localityCode = null;
        if (!CollectionUtils.isEmpty(households) && households.get(0).getAddress() != null
                && households.get(0).getAddress().getLocality() != null
                && households.get(0).getAddress().getLocality().getCode() != null) {
            localityCode = households.get(0).getAddress().getLocality().getCode();
            geoPoint = commonUtils.getGeoPoint(households.get(0).getAddress());
        }
        if (localityCode != null) {
            boundaryHierarchy = commonUtils.getBoundaryHierarchyWithLocalityCode(localityCode, householdMember.getTenantId());
        }

        Map<String, String> userInfoMap = userService.
                getUserInfo(householdMember.getTenantId(), householdMember.getClientAuditDetails().getLastModifiedBy());

        HouseholdMemberIndexV1 householdMemberIndexV1 = HouseholdMemberIndexV1.builder()
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
                .build();
        return householdMemberIndexV1;
    }
}

