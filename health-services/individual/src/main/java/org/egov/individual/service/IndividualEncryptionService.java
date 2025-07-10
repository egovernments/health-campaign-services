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
import org.egov.individual.util.EncryptionDecryptionUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.SET_INDIVIDUALS;
import static org.egov.individual.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class IndividualEncryptionService {
    private final EncryptionDecryptionUtil encryptionDecryptionUtil;

    private final IndividualRepository individualRepository;

    public IndividualEncryptionService(EncryptionDecryptionUtil encryptionDecryptionUtil,
                                       IndividualRepository individualRepository) {
        this.encryptionDecryptionUtil = encryptionDecryptionUtil;
        this.individualRepository = individualRepository;
    }


    public List<Individual> encrypt(IndividualBulkRequest request, List<Individual> individuals, String key, boolean isBulk) {
       
        return individuals;
    }

    public IndividualSearch encrypt(IndividualSearch individualSearch, String key) {
        return individualSearch;
    }

    public List<Individual> decrypt(List<Individual> individuals, String key, RequestInfo requestInfo) {
        return individuals;
    }
}
