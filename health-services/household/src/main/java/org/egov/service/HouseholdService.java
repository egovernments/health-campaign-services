package org.egov.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.service.IdGenService;
import org.egov.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.Address;
import org.egov.web.models.Household;
import org.egov.web.models.HouseholdRequest;
import org.egov.web.models.HouseholdSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByClientReferenceIdOnly;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;

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
        List<String> ids = householdRequest.getHousehold().stream().map(Household::getClientReferenceId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            List<String> alreadyExists = householdRepository.validateIds(ids, "clientReferenceId");
            if (!alreadyExists.isEmpty()) {
                log.info("Already exists {}", alreadyExists);
                throw new CustomException("AlREADY_EXISTS",
                        String.format("ClientReferenceId already exists %s", alreadyExists));
            }
        }

        List<String> idList =  idGenService.getIdList(householdRequest.getRequestInfo(),
                getTenantId(householdRequest.getHousehold()),
                "household.id", "", householdRequest.getHousehold().size());
        enrichForCreate(householdRequest.getHousehold(), idList, householdRequest.getRequestInfo());

        List<Address> addresses = householdRequest.getHousehold().stream().map(Household::getAddress)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
           IntStream.range(0, addresses.size()).forEach(i -> {
              addresses.get(i).setId(UUID.randomUUID().toString());
           });
        }

        householdRepository.save(householdRequest.getHousehold(), "save-household-topic");
        return householdRequest.getHousehold();
    }

    public List<Household> search(HouseholdSearchRequest request, Integer limit, Integer offset, String tenantId,
                                  Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException {

        if (isSearchByIdOnly(request.getHousehold())) {
            return householdRepository.findById(Arrays.asList(request.getHousehold().getId()),
                    "id", includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }

        if (isSearchByClientReferenceIdOnly(request.getHousehold())) {
             return householdRepository.findById(Arrays.asList(request.getHousehold().getClientReferenceId()),
                        "clientReferenceId", includeDeleted).stream()
                     .filter(lastChangedSince(lastChangedSince))
                     .filter(havingTenantId(tenantId))
                     .filter(includeDeleted(includeDeleted))
                     .collect(Collectors.toList());
        }

        return householdRepository.find(request.getHousehold(), limit, offset,
                        tenantId, lastChangedSince, includeDeleted);
    }
}
