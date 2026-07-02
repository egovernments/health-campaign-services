package org.egov.id.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class IdPoolConfig {
    String seqCode;
    int paddingLength;
}

