package org.egov.individual.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiDetails {
    private String url;
    private String requestBody;
    private Map<String, Object> requestHeaders;
    private String additionalDetails;
}
