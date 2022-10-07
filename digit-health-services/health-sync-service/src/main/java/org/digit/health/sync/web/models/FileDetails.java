package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Validated
public class FileDetails {

    @NotEmpty
    @NotNull
    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @NotEmpty
    @NotNull
    @JsonProperty("checksum")
    private String checksum;
}
