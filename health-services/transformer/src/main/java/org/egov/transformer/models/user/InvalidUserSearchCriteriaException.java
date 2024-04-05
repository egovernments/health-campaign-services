package org.egov.transformer.models.user;

import lombok.Getter;

public class InvalidUserSearchCriteriaException extends RuntimeException {
    private static final long serialVersionUID = 6283251530711731311L;
    @Getter
    private UserSearchCriteria searchCriteria;

    public InvalidUserSearchCriteriaException(UserSearchCriteria searchCriteria) {
        super("Insufficient or invalid search criteria: " + searchCriteria.toString());
        this.searchCriteria = searchCriteria;
    }
}
