package org.egov.individual.validators;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class AadharMobileNumberValidator implements Validator<IndividualBulkRequest, Individual> {

    private final IndividualRepository individualRepository;

    private final IndividualProperties properties;

    @Autowired
    public AadharMobileNumberValidator(IndividualRepository individualRepository, IndividualProperties properties) {
        this.individualRepository = individualRepository;
        this.properties = properties;
    }

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        log.info("AadharMobileNumberValidator::validate::Validating aadharNumber and mobileNumber");
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> individuals = request.getIndividuals();

        if (!individuals.isEmpty()) {
            for (Individual individual : individuals) {
                //check mobile number has all numbers , if present
                if (StringUtils.isNotBlank(individual.getMobileNumber()) && !isValid(individual.getMobileNumber(),properties.getMobilePattern())) {
                    Error error = Error.builder().errorMessage("Invalid MobileNumber").errorCode("INVALID_MOBILENUMBER").type(Error.ErrorType.NON_RECOVERABLE).exception(new CustomException("INVALID_MOBILENUMBER", "Invalid MobileNumber")).build();
                    populateErrorDetails(individual, error, errorDetailsMap);
                }

               //check aadhar number has 12 digits, if present
               if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                   Identifier identifier = individual.getIdentifiers().stream()
                           .filter(id -> id.getIdentifierType().contains("AADHAAR"))
                           .findFirst().orElse(null);
                   if (identifier != null && StringUtils.isNotBlank(identifier.getIdentifierId())
                               && !isValid(identifier.getIdentifierId(),properties.getAadhaarPattern())) {
                       Error error = Error.builder().errorMessage("Invalid Aadhaar").errorCode("INVALID_AADHAAR").type(Error.ErrorType.NON_RECOVERABLE).exception(new CustomException("INVALID_AADHAAR", "Invalid Aadhaar")).build();
                       populateErrorDetails(individual, error, errorDetailsMap);
                   }
               }
            }

        }
        return errorDetailsMap;
    }

    private boolean isValid(String value, String PATTERN) {

        Pattern pattern = Pattern.compile(PATTERN);
        Matcher matcher = pattern.matcher(value);
        return matcher.matches();
    }

}
