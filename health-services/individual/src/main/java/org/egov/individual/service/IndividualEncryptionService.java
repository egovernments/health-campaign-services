package org.egov.individual.service;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.Individual;
import org.egov.individual.util.EncryptionDecryptionUtil;
import org.egov.individual.web.models.IndividualSearch;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IndividualEncryptionService {
    private final EncryptionDecryptionUtil encryptionDecryptionUtil;

    public IndividualEncryptionService(EncryptionDecryptionUtil encryptionDecryptionUtil) {
        this.encryptionDecryptionUtil = encryptionDecryptionUtil;
    }


    public List<Individual> encrypt(List<Individual> individuals, String key) {
        List<Individual> encryptedIndividuals = (List<Individual>) encryptionDecryptionUtil
                .encryptObject(individuals, key, Individual.class);
        // TODO: Validate for aadhaar uniqueness
        return encryptedIndividuals;
    }

    public Individual encrypt(Individual individual, String key) {
        Individual encryptedIndividual = (Individual) encryptionDecryptionUtil
                .encryptObject(individual, key, Individual.class);
        // TODO: Validate for aadhaar uniqueness
        return encryptedIndividual;
    }

    public IndividualSearch encrypt(IndividualSearch individualSearch, String key) {
        IndividualSearch encryptedIndividualSearch = (IndividualSearch) encryptionDecryptionUtil
                .encryptObject(individualSearch, key, IndividualSearch.class);
        return encryptedIndividualSearch;
    }

    public List<Individual> decrypt(List<Individual> individuals, String key, RequestInfo requestInfo) {
        List<Individual> encryptedIndividuals = filterEncryptedIndividuals(individuals);
        List<Individual> decryptedIndividuals = (List<Individual>) encryptionDecryptionUtil
                .decryptObject(encryptedIndividuals, key, Individual.class, requestInfo);
        if (individuals.size() > decryptedIndividuals.size()) {
            // add the already decrypted objects to the list
            List<String> ids = decryptedIndividuals.stream()
                    .map(Individual::getId)
                    .collect(Collectors.toList());
            for (Individual individual : individuals) {
                if (!ids.contains(individual.getId())) {
                    decryptedIndividuals.add(individual);
                }
            }
        }
        return decryptedIndividuals;
    }

    private List<Individual> filterEncryptedIndividuals(List<Individual> individuals) {
        return individuals.stream()
                .filter(individual -> isCipherText(individual.getMobileNumber())
                        || isCipherText(!CollectionUtils.isEmpty(individual.getIdentifiers())
                        ? individual.getIdentifiers().stream().findAny().get().getIdentifierId()
                        : null))
                .collect(Collectors.toList());
    }

    private boolean isCipherText(String text) {
        //sample encrypted data - 640326|7hsFfY6olwUbet1HdcLxbStR1BSkOye8N3M=
        //Encrypted data will have a prefix followed by '|' and the base64 encoded data
        if ((StringUtils.isNotBlank(text) && text.contains("|"))) {
            String base64Data = text.split("\\|")[1];
            return StringUtils.isNotBlank(base64Data) && (base64Data.length() % 4 == 0 || base64Data.endsWith("="));
        }
        return false;
    }
}
