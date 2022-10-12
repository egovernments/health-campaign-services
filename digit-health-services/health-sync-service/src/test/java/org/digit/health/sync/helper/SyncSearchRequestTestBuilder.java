package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncStatus;
import org.digit.health.sync.web.models.request.SyncSearchRequest;
import org.digit.health.sync.web.models.request.SyncUpRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;


public class SyncSearchRequestTestBuilder {
    private SyncSearchRequest.SyncSearchRequestBuilder builder;


    public static SyncSearchRequestTestBuilder builder() {
        return new SyncSearchRequestTestBuilder();
    }

    public SyncSearchRequestTestBuilder() {
        this.builder = SyncSearchRequest.builder();
    }

    public SyncSearchRequestTestBuilder withSyncId() {
        builder
                .requestInfo(requestInfo())
                .tenantId("mq")
                .syncId("sync-id");

        return this;
    }

    public SyncSearchRequestTestBuilder withReferenceId() {
        builder.requestInfo(requestInfo())
                .tenantId("mq")
                .reference(ReferenceId.builder().id("ref-id").type("campaign").build());

        return this;
    }

    public SyncSearchRequestTestBuilder withFileStoreId() {
        builder.requestInfo(requestInfo())
                .tenantId("mq")
                .fileStoreId("file-store-id");

        return this;
    }

    public SyncSearchRequestTestBuilder withStatus() {
        builder.requestInfo(requestInfo())
                .tenantId("mq")
                .status(SyncStatus.FAILED.name());

        return this;
    }



    public RequestInfo requestInfo() {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid("uuid").tenantId("tenantId").id(1L).build()).build();
    }


    public SyncSearchRequest build() {
        return builder.build();
    }
}
