package org.egov.common.models.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Deprecated
public class Role {
    private static final String CITIZEN = "CITIZEN";
    private String name;
    private String code;
    private String description;
    private String tenantId;
}