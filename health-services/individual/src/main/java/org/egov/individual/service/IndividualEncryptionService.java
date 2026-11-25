package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.StoredIdentifier;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.SET_INDIVIDUALS;
import static org.egov.individual.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class IndividualEncryptionService {
    private final IndividualPiiCryptoService piiCryptoService;

    private final IndividualRepository individualRepository;

    public IndividualEncryptionService(IndividualPiiCryptoService piiCryptoService,
                                       IndividualRepository individualRepository) {
        this.piiCryptoService = piiCryptoService;
        this.individualRepository = individualRepository;
    }

    /**
     * Encrypts every PII field (one enc-service call for the batch) and checks Aadhaar
     * uniqueness on the hash. The key argument is kept for callers; field selection now lives
     * in IndividualPiiCryptoService instead of the MDMS IndividualEncrypt policy.
     */
    public List<Individual> encrypt(IndividualBulkRequest request, List<Individual> individuals, String key, boolean isBulk) {
        List<Individual> encryptedIndividuals = piiCryptoService.encrypt(individuals);
        validateAadhaarUniqueness(encryptedIndividuals, request, isBulk);
        return encryptedIndividuals;
    }

    /** Stored (encrypted) form without the duplicate checks, for messages such as delete. */
    public List<Individual> toStoredForm(List<Individual> individuals) {
        return piiCryptoService.encrypt(individuals);
    }

    /** Decrypts whatever is cipher text; plain values (rows not yet backfilled) are left as they are. */
    public List<Individual> decrypt(List<Individual> individuals, String key, RequestInfo requestInfo) {
        return piiCryptoService.decrypt(individuals, requestInfo);
    }

    private void validateAadhaarUniqueness (List<Individual> individuals, IndividualBulkRequest request, boolean isBulk) {

        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        String tenantId = getTenantId(individuals);

        if (!individuals.isEmpty()) {
            for (Individual individual : individuals) {
                if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                    Identifier identifier = individual.getIdentifiers().stream()
                            .filter(id -> id.getIdentifierType().contains("AADHAAR"))
                            .findFirst().orElse(null);
                    if (identifier != null && StringUtils.isNotBlank(identifier.getIdentifierId())) {
                        // identifierId is cipher text here; the repository matches on the hash
                        StoredIdentifier identifierSearch = new StoredIdentifier();
                        identifierSearch.setIsDeleted(null);  // no isDeleted filter, same as before
                        identifierSearch.setIdentifierType(identifier.getIdentifierType());
                        identifierSearch.setIdentifierIdHash(identifier instanceof StoredIdentifier
                                ? ((StoredIdentifier) identifier).getIdentifierIdHash() : null);
                        if (identifierSearch.getIdentifierIdHash() == null) {
                            identifierSearch.setIdentifierId(identifier.getIdentifierId());
                        }
                        IndividualSearch individualSearch = IndividualSearch.builder().identifier(identifierSearch).build();
                        List<Individual> individualsList = null;
                        try {
                            individualsList = individualRepository.find(individualSearch,null,
                                    null,tenantId,null,false).getResponse();
                        } catch (Exception exception) {
                            log.error("database error occurred", ExceptionUtils.getStackTrace(exception));
                            throw new CustomException("DATABASE_ERROR", exception.getMessage());
                        }
                        if (!CollectionUtils.isEmpty(individualsList)) {
                            boolean isSelfIdentifier = individualsList.stream()
                                    .anyMatch(ind -> ind.getId().equalsIgnoreCase(individual.getId()));
                            if (!isSelfIdentifier) {
                                Error error = Error.builder().errorMessage("Aadhaar already exists for Individual - "
                                        +individualsList.get(0).getIndividualId()).errorCode("DUPLICATE_AADHAAR")
                                        .type(Error.ErrorType.NON_RECOVERABLE)
                                        .exception(new CustomException("DUPLICATE_AADHAAR", "Aadhaar already exists for Individual - "
                                                +individualsList.get(0).getIndividualId())).build();
                                populateErrorDetails(individual, error, errorDetailsMap);
                            }
                        }
                    }

                }
            }
        }

        if (!errorDetailsMap.isEmpty()) {
            Map<Individual, ErrorDetails> errorDetailsMapForTracer = new HashMap<>();
            Stream.of(errorDetailsMap).forEach(e -> populateErrorDetails(request, errorDetailsMapForTracer, e,
                    SET_INDIVIDUALS));
            handleErrors(errorDetailsMapForTracer, isBulk, VALIDATION_ERROR);
        }
    }
}
