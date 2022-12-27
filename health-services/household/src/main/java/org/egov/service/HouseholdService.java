package org.egov.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.Address;
import org.egov.web.models.Household;
import org.egov.web.models.HouseholdRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForCreate;
import static org.egov.common.utils.CommonUtils.getTenantId;

@Service
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;

    private final IdGenService idGenService;

    @Autowired
    public HouseholdService(HouseholdRepository householdRepository, IdGenService idGenService) {
        this.householdRepository = householdRepository;
        this.idGenService = idGenService;
    }

    public List<Household> create(HouseholdRequest householdRequest) throws Exception {
        List<String> ids = householdRequest.getHousehold().stream().map(Household::getClientReferenceId).filter(h -> h != null).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            List<String> alreadyExists = householdRepository.validateIds(ids, "clientReferenceId");
            if (!alreadyExists.isEmpty()) {
                log.info("Already exists {alreadyExists}", alreadyExists.toString());
                throw new CustomException("AlREADY_EXISTS", String.format("ClientReferenceId already exists %s", alreadyExists));
            }
        }

        List<String> idList =  idGenService.getIdList(householdRequest.getRequestInfo(),
                getTenantId(householdRequest.getHousehold()),
                "household.id", "", householdRequest.getHousehold().size());
        enrichForCreate(householdRequest.getHousehold(), idList, householdRequest.getRequestInfo());
        AuditDetails auditDetailsForCreate = getAuditDetailsForCreate(householdRequest.getRequestInfo());
        IntStream.range(0, householdRequest.getHousehold().size()).forEach(i -> {
            Address address = householdRequest.getHousehold().get(i).getAddress();
            address.setAuditDetails(auditDetailsForCreate);
            address.setIsDeleted(false);
            address.setRowVersion(1);
        });
        householdRepository.save(householdRequest.getHousehold(), "save-household-topic");
        return householdRequest.getHousehold();
    }
}
