package org.egov.individual.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SearchValidator {

    public void validate (IndividualSearch individualSearch) {
        log.info("Validating search call");
        if (individualSearch.getCreatedFrom() != null && individualSearch.getCreatedTo() != null &&
        individualSearch.getCreatedFrom().compareTo(individualSearch.getCreatedTo()) > 0) {
            throw new CustomException("FROM_GREATER_THAN_TO_DATE", "From date is greater than to date");
        }
    }

}
