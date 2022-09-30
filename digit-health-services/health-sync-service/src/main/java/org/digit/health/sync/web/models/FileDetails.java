package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileDetails {

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("checksum")
    private String checksum;
}
