package org.egov.referralmanagement.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalityDownsyncResult {
    private String locality;
    private String status;
    private Map<String, String> s3Keys;
    private String errorMessage;
}
