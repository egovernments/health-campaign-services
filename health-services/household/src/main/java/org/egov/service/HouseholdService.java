package org.egov.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.web.models.Household;
import org.egov.web.models.HouseholdRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class HouseholdService {

    public List<Household> create(HouseholdRequest householdRequest){
        return householdRequest.getHousehold();
    }
}
