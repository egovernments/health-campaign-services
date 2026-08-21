package org.egov.referralmanagement.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncFileLink {
    @JsonProperty("fileType")
    private String fileType;

    @JsonProperty("url")
    private String url;

    @JsonProperty("recordCount")
    private Long recordCount;

    @JsonProperty("expiresAt")
    private Long expiresAt;
}
