package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncLogStatus;
import org.digit.health.sync.web.models.request.SyncLogSearchRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;


public class SyncSearchRequestTestBuilder {
    private SyncLogSearchRequest.SyncLogSearchRequestBuilder builder;


    public static SyncSearchRequestTestBuilder builder() {
        return new SyncSearchRequestTestBuilder();
    }

    public SyncSearchRequestTestBuilder() {
        this.builder = SyncLogSearchRequest.builder();
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
                .status(SyncLogStatus.FAILED.name());

        return this;
    }



    public RequestInfo requestInfo() {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid("uuid").tenantId("tenantId").id(1L).build()).build();
    }


    public SyncLogSearchRequest build() {
        return builder.build();
    }
}
