package org.egov.web.models.Organisation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrgContactUpdateDiff {

    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;

    private String tenantId;

    private String organisationId;

    private List<ContactDetails> oldContacts;

    private List<ContactDetails> newContacts;

}
