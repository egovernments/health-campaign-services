package org.egov.helper;

import org.egov.common.contract.request.RequestInfo;


public class RequestInfoTestBuilder {
    private RequestInfo.RequestInfoBuilder builder;

    public RequestInfoTestBuilder() {
        this.builder = RequestInfo.builder();
    }

    public static RequestInfoTestBuilder builder() {
        return new RequestInfoTestBuilder();
    }

    public RequestInfo build() {
        return this.builder.build();
    }

    public RequestInfoTestBuilder withCompleteRequestInfo() {
        this.builder.userInfo(UserTestBuilder.builder().withCompleteUserInfo().build())
                .action("create")
                .apiId("some-api-id")
                .authToken("some-auth-token")
                .did("some-did")
                .correlationId("some-correlation-id")
                .key("some-key")
                .msgId("some-msg-id")
                .ts(System.currentTimeMillis())
                .ver("1");
        return this;
    }

    public RequestInfoTestBuilder requestInfoWithUserInfoButWithOutUUID() {
        this.builder.userInfo(UserTestBuilder.builder().userInfoWithOutUUID().build())
                .action("create")
                .apiId("some-api-id")
                .authToken("some-auth-token")
                .did("some-did")
                .correlationId("some-correlation-id")
                .key("some-key")
                .msgId("some-msg-id")
                .ts(System.currentTimeMillis())
                .ver("1");
        return this;
    }

    public RequestInfoTestBuilder requestInfoWithoutUserInfo() {
        this.builder.userInfo(null)
                .action("create")
                .apiId("some-api-id")
                .authToken("some-auth-token")
                .did("some-did")
                .correlationId("some-correlation-id")
                .key("some-key")
                .msgId("some-msg-id")
                .ts(System.currentTimeMillis())
                .ver("1");
        return this;
    }
}