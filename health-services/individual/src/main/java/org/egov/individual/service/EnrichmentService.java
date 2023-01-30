package org.egov.individual.service;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.collectFromList;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.uuidSupplier;
import static org.egov.individual.Constants.SYSTEM_GENERATED;

@Service
@Slf4j
public class EnrichmentService {

    private final IdGenService idGenService;

    private final IndividualProperties properties;

    @Autowired
    public EnrichmentService(IdGenService idGenService,
                             IndividualProperties properties) {
        this.idGenService = idGenService;
        this.properties = properties;
    }

    public void create(List<Individual> validIndividuals, IndividualBulkRequest request) throws Exception {
        log.info("extracting tenantId");
        final String tenantId = getTenantId(validIndividuals);
        log.info("generating id for individuals");
        List<String> indIdList = idGenService.getIdList(request.getRequestInfo(),
                tenantId, properties.getIndividualId(),
                null, validIndividuals.size());
        log.info("enriching individuals");
        enrichForCreate(validIndividuals, indIdList, request.getRequestInfo());
        enrichAddressesForCreate(request, validIndividuals);
        enrichIdentifiersForCreate(request, validIndividuals);
    }

    public void update(List<Individual> validIndividuals, IndividualBulkRequest request) throws Exception {
        validIndividuals.forEach(
                individual -> {
                    enrichAddressForUpdate(request, individual);
                    enrichIdentifierForUpdate(request, individual);
                }
        );
        Map<String, Individual> iMap = getIdToObjMap(validIndividuals);
        log.info("enriching individuals");
        enrichForUpdate(iMap, request);
    }

    public void delete(List<Individual> validIndividuals, IndividualBulkRequest request) throws Exception {
        enrichIndividualIdInAddress(validIndividuals);
        validIndividuals = validIndividuals.stream()
                .map(EnrichmentService::enrichIndividualIdInIdentifiers)
                .collect(Collectors.toList());
        validIndividuals.forEach(individual -> {
            RequestInfo requestInfo = request.getRequestInfo();
            if (individual.getIsDeleted()) {
                enrichForDelete(Collections.singletonList(individual), requestInfo, true);
                enrichForDelete(individual.getAddress(), requestInfo, false);
                enrichForDelete(individual.getIdentifiers(), requestInfo, false);
            } else {
                Integer previousRowVersion = individual.getRowVersion();
                individual.getIdentifiers().stream().filter(Identifier::getIsDeleted)
                        .forEach(identifier -> {
                            AuditDetails existingAuditDetails = identifier.getAuditDetails();
                            AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                                    request.getRequestInfo().getUserInfo().getUuid());
                            identifier.setAuditDetails(auditDetails);
                            individual.setAuditDetails(auditDetails);
                            individual.setRowVersion(previousRowVersion + 1);
                        });
                individual.getAddress().stream().filter(Address::getIsDeleted)
                        .forEach(address -> {
                            AuditDetails existingAuditDetails = address.getAuditDetails();
                            AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                                    request.getRequestInfo().getUserInfo().getUuid());
                            address.setAuditDetails(auditDetails);
                            individual.setAuditDetails(auditDetails);
                            individual.setRowVersion(previousRowVersion + 1);
                        });
            }
        });
    }

    private static void enrichAddressesForCreate(IndividualBulkRequest request, List<Individual> validIndividuals) {
        List<Address> addresses = collectFromList(validIndividuals,
                Individual::getAddress);
        if (!addresses.isEmpty()) {
            log.info("enriching addresses");
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichForCreate(addresses, addressIdList, request.getRequestInfo(), false);
            enrichIndividualIdInAddress(validIndividuals);
        }
    }

    private static void enrichIdentifiersForCreate(IndividualBulkRequest request, List<Individual> validIndividuals) {
        log.info("enriching identifiers");
        request.setIndividuals(validIndividuals.stream()
                .map(EnrichmentService::enrichWithSystemGeneratedIdentifier)
                .map(EnrichmentService::enrichIndividualIdInIdentifiers)
                .collect(Collectors.toList()));
        List<Identifier> identifiers = collectFromList(validIndividuals,
                Individual::getIdentifiers);
        List<String> identifierIdList = uuidSupplier().apply(identifiers.size());
        enrichForCreate(identifiers, identifierIdList, request.getRequestInfo(), false);
    }

    private static Individual enrichIndividualIdInIdentifiers(Individual individual) {
        List<Identifier> identifiers = individual.getIdentifiers();
        identifiers.forEach(identifier -> identifier.setIndividualId(individual.getId()));
        individual.setIdentifiers(identifiers);
        return individual;
    }

    private static void enrichIndividualIdInAddress(List<Individual> individuals) {
        individuals.stream().filter(individual -> individual.getAddress() != null)
                .forEach(individual -> individual.getAddress()
                        .forEach(address -> address.setIndividualId(individual.getId())));
    }

    private static Individual enrichWithSystemGeneratedIdentifier(Individual individual) {
        if (individual.getIdentifiers() == null || individual.getIdentifiers().isEmpty()) {
            List<Identifier> identifiers = new ArrayList<>();
            identifiers.add(Identifier.builder()
                    .identifierType(SYSTEM_GENERATED)
                    .identifierId(individual.getId())
                    .build());
            individual.setIdentifiers(identifiers);
        }
        return individual;
    }

    private static void enrichAddressForUpdate(IndividualBulkRequest request, Individual individual) {
        List<Address> addressesToCreate = individual.getAddress().stream()
                .filter(ad1 -> ad1.getId() == null)
                .collect(Collectors.toList());
        if (!addressesToCreate.isEmpty()) {
            log.info("enriching addresses to create");
            List<String> addressIdList = uuidSupplier().apply(addressesToCreate.size());
            enrichForCreate(addressesToCreate, addressIdList, request.getRequestInfo(), false);
            addressesToCreate.forEach(address -> address.setIndividualId(individual.getId()));
        }

        List<Address> addressesToUpdate = individual.getAddress().stream()
                .filter(ad1 -> ad1.getId() != null)
                .collect(Collectors.toList());
        if (!addressesToUpdate.isEmpty()) {
            log.info("enriching addresses to update");
            addressesToUpdate.forEach(address -> {
                address.setIndividualId(individual.getId());
                AuditDetails existingAuditDetails = address.getAuditDetails();
                AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                        request.getRequestInfo().getUserInfo().getUuid());
                address.setAuditDetails(auditDetails);
                if (address.getIsDeleted() == null) {
                    address.setIsDeleted(Boolean.FALSE);
                }
            });
        }
    }

    private static void enrichIdentifierForUpdate(IndividualBulkRequest request,
                                                  Individual individual) {
        if (individual.getIdentifiers() != null) {
            List<Identifier> identifiersToCreate = individual.getIdentifiers().stream().filter(havingNullId())
                    .collect(Collectors.toList());
            if (!identifiersToCreate.isEmpty()) {
                List<String> addressIdList = uuidSupplier().apply(identifiersToCreate.size());
                enrichForCreate(identifiersToCreate, addressIdList, request.getRequestInfo(), false);
                identifiersToCreate.forEach(identifier -> identifier.setIndividualId(individual.getId()));
            }

            List<Identifier> identifiersToUpdate = individual.getIdentifiers().stream()
                    .filter(notHavingNullId())
                    .collect(Collectors.toList());
            if (!identifiersToUpdate.isEmpty()) {
                identifiersToUpdate.forEach(identifier -> {
                    identifier.setIndividualId(individual.getId());
                    AuditDetails existingAuditDetails = identifier.getAuditDetails();
                    AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                            request.getRequestInfo().getUserInfo().getUuid());
                    identifier.setAuditDetails(auditDetails);
                    if (identifier.getIsDeleted() == null) {
                        identifier.setIsDeleted(Boolean.FALSE);
                    }
                });
            }
        }
    }

    private static Predicate<Identifier> havingNullId() {
        return identifier -> identifier.getId() == null;
    }

    private static Predicate<Identifier> notHavingNullId() {
        return havingNullId().negate();
    }
}
