package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.request.SyncUpRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;


public class SyncUpRequestTestBuilder {
    private SyncUpRequest.SyncUpRequestBuilder builder;


    public static SyncUpRequestTestBuilder builder() {
        return new SyncUpRequestTestBuilder();
    }

    public SyncUpRequestTestBuilder() {
        this.builder = SyncUpRequest.builder();
    }

    public SyncUpRequestTestBuilder withFileDetails() {
        builder
                .requestInfo(requestInfo())
                .referenceId(ReferenceId.builder()
                        .id("1")
                        .type("campaign")
                        .build())
                .fileDetails(FileDetails.builder()
                        .fileStoreId("007dcf61-d68f-4069-990f-82c25a0069a5")
                        .checksum("c6c78309a2d52f2d0ec5487e2090534f").build());
        return this;
    }

    public RequestInfo requestInfo() {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid("uuid").tenantId("tenantId").id(1L).build()).build();
    }


    public SyncUpRequest build() {
        return builder.build();
    }
}
