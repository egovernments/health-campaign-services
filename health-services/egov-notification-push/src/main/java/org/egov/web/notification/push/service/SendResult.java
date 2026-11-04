package org.egov.web.notification.push.service;

public record SendResult(int attempted, int sent, int failedPermanent, int queuedForRetry) {

    public static final SendResult EMPTY = new SendResult(0, 0, 0, 0);

}
