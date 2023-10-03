package org.egov.individual.validators;

import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;

public class SearchValidator {

    public void validate (IndividualSearch individualSearch) {
        if (individualSearch.getCreatedFrom() != null && individualSearch.getCreatedTo() != null &&
        individualSearch.getCreatedFrom().compareTo(individualSearch.getCreatedTo()) > 0) {
            throw new CustomException("FROM_GREATER_THAN_TO_DATE", "From date is greater than to date");
        }
    }

}
