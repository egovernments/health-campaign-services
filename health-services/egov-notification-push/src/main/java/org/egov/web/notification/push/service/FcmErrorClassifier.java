package org.egov.web.notification.push.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.messaging.FirebaseMessagingException;

public final class FcmErrorClassifier {

    public static final String UNREGISTERED = "UNREGISTERED";
    public static final String SENDER_ID_MISMATCH = "SENDER_ID_MISMATCH";
    public static final String TIMEOUT = "TIMEOUT";
    public static final long MAX_RETRY_AFTER_SECONDS = 3600;

    private static final Set<String> PERMANENT = Set.of(UNREGISTERED, "INVALID_ARGUMENT", SENDER_ID_MISMATCH);

    private FcmErrorClassifier() {
    }

    public static String code(Throwable e) {
        if (e instanceof FirebaseMessagingException fme && fme.getMessagingErrorCode() != null) {
            return fme.getMessagingErrorCode().name();
        }
        if (e instanceof FirebaseException fe && fe.getErrorCode() != null) {
            return fe.getErrorCode() == ErrorCode.UNKNOWN && fe.getCause() != null
                    ? fe.getCause().getClass().getSimpleName()
                    : fe.getErrorCode().name();
        }
        return e == null ? "UNKNOWN" : e.getClass().getSimpleName();
    }

    public static boolean isPermanent(String code) {
        return PERMANENT.contains(code);
    }

    public static long retryAfterMs(Throwable e) {
        if (!(e instanceof FirebaseException fe)) {
            return 0;
        }
        IncomingHttpResponse response = fe.getHttpResponse();
        if (response == null || response.getHeaders() == null) {
            return 0;
        }
        for (Map.Entry<String, Object> header : response.getHeaders().entrySet()) {
            if ("retry-after".equalsIgnoreCase(header.getKey())) {
                Object value = header.getValue() instanceof List<?> list && !list.isEmpty() ? list.get(0) : header.getValue();
                try {
                    long seconds = Long.parseLong(String.valueOf(value).trim());
                    return Math.max(0, Math.min(seconds, MAX_RETRY_AFTER_SECONDS)) * 1000;
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

}
