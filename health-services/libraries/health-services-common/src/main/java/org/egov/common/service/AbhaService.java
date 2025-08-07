package org.egov.common.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.abha.AbhaGatewayOtpVerifyRequest;
import org.egov.common.models.abha.AbhaOtpRequest;
import org.egov.common.models.abha.AbhaOtpResponse;
import org.egov.common.models.abha.AbhaGatewayOtpVerifyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service to interact with ABHA Aadhaar OTP APIs
 */
@Service
@ConditionalOnProperty(name = "egov.abha.integration.enabled", havingValue = "true")
@Slf4j
public class AbhaService {

    private final String abhaHost;
    private final String sendOtpPath;
    private final String verifyOtpPath;
    private final ServiceRequestClient serviceRequestClient;

    @Autowired
    public AbhaService(ServiceRequestClient serviceRequestClient,
                       @Value("${egov.abha.host}") String abhaHost,
                       @Value("${egov.abha.send.otp.path:/api/abha/create/send-aadhaar-otp}") String sendOtpPath,
                       @Value("${egov.abha.verify.otp.path:/api/abha/create/verify-and-enroll-with-aadhaar-otp}") String verifyOtpPath) {
        this.abhaHost = abhaHost;
        this.sendOtpPath = sendOtpPath;
        this.verifyOtpPath = verifyOtpPath;
        this.serviceRequestClient = serviceRequestClient;
    }

    /**
     * Calls /send-aadhaar-otp API with Aadhaar number
     * @param request AbhaOtpRequest containing aadhaarNumber
     * @return AbhaOtpResponse containing txnId and message
     */
    public AbhaOtpResponse sendAadhaarOtp(AbhaOtpRequest request) {
        String uri = abhaHost + sendOtpPath;
        return serviceRequestClient.fetchResult(new StringBuilder(uri), request, AbhaOtpResponse.class);
    }

    /**
     * Calls /verify-and-enroll-with-aadhaar-otp API with txnId, otp, and mobile
     * @param request AbhaGatewayOtpVerifyRequest containing txnId, otp, and mobile
     * @return AbhaGatewayOtpVerifyResponse containing ABHANumber, tokens, profile
     */
    public AbhaGatewayOtpVerifyResponse verifyAadhaarOtp(AbhaGatewayOtpVerifyRequest request) {
        String uri = abhaHost + verifyOtpPath;
        return serviceRequestClient.fetchResult(new StringBuilder(uri), request, AbhaGatewayOtpVerifyResponse.class);
    }
}
