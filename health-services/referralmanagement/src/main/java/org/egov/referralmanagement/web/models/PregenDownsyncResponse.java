package org.egov.referralmanagement.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import java.util.List;

/**
 * Returned when lastSyncedTime=null and pre-generated files are available.
 * The app downloads each file via the provided URLs instead of using inline data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PregenDownsyncResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("DownloadLinks")
    private List<DownsyncFileLink> downloadLinks;
}
