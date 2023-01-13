package org.egov.individual.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorDetails {
    private Exception exception;
    private String errorCode;
    private String errorMessage;
    private String id;
    private String status;
    private ApiDetails apiDetails;
    private String additionalDetails;
}
