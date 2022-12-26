package org.egov.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.Household;
import org.egov.web.models.HouseholdRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;

    @Autowired
    public HouseholdService(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    public List<Household> create(HouseholdRequest householdRequest){
        List<String> ids = householdRequest.getHousehold().stream().map(Household::getClientReferenceId).collect(Collectors.toList());
        List<String> alreadyExists = householdRepository.validateId(ids, "clientReferenceId");
        if (!alreadyExists.isEmpty()) {
            log.info("Already exists {alreadyExists}", alreadyExists);
            throw new CustomException("AlREADY_EXISTS", String.format("ClientReferenceId already exists %s", alreadyExists));
        }

        return householdRequest.getHousehold();
    }
}
