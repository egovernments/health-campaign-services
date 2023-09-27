package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.referralmanagement.Constants.FACILITY;


@Component
@Order(value = 3)
@Slf4j
public class RmFacilityEntitiesIdValidator implements Validator<ReferralBulkRequest, Referral> {
    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    @Autowired
    public RmFacilityEntitiesIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }


    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating facility id");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> entities = request.getReferrals();
        Map<String, List<Referral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(Referral::getTenantId));
        List<String> tenantIds = new ArrayList<>(tenantIdReferralMap.keySet());
        tenantIds.forEach(tenantId -> {
            List<Referral> referralList = tenantIdReferralMap.get(tenantId);
            if (!referralList.isEmpty()) {
                List<Facility> existingFacilityList = null;
                final List<String> facilityIdList = new ArrayList<>();
                try {
                    referralList.forEach(referral -> {
                        if(referral.getReferredToType().equals(FACILITY)){
                            addIgnoreNull(facilityIdList, referral.getReferredToId());
                        }
                    });
                    FacilitySearch facilitySearch = FacilitySearch.builder()
                            .id(facilityIdList.isEmpty() ? null : facilityIdList)
                            .build();
                    FacilityBulkResponse facilityBulkResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(referralManagementConfiguration.getFacilityHost()
                                    + referralManagementConfiguration.getFacilitySearchUrl()
                                    +"?limit=" + entities.size()
                                    + "&offset=0&tenantId=" + tenantId),
                            FacilitySearchRequest.builder().requestInfo(request.getRequestInfo()).facility(facilitySearch).build(),
                            FacilityBulkResponse.class
                    );
                    existingFacilityList = facilityBulkResponse.getFacilities();
                } catch (QueryBuilderException e) {
                    existingFacilityList = Collections.emptyList();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                final List<String> existingFacilityIds = new ArrayList<>();
                existingFacilityList.forEach(facility -> existingFacilityIds.add(facility.getId()));
                List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                                        (!entity.getReferredToType().equals(FACILITY) || !existingFacilityIds.contains(entity.getReferredToId()))
                        ).collect(Collectors.toList());
                invalidEntities.forEach(referral -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(referral, error, errorDetailsMap);
                });

            }
        });
        return errorDetailsMap;
    }

    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }
}
