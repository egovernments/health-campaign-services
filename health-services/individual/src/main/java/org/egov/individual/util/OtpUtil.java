package org.egov.individual.util;


import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.user.enums.UserType;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.ServiceRequestRepository;
import org.egov.individual.web.models.otp.*;
import org.egov.individual.web.models.register.IndividualRegisterRequest;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ServiceCallException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import static org.egov.individual.Constants.OTP_TYPE_LOGIN;

@Component
@Slf4j
public class OtpUtil {

    private final IndividualProperties config;

    private final ObjectMapper mapper;

    private final ServiceRequestRepository serviceRequestRepository;

    private final RestTemplate restTemplate;

    @Autowired
    public OtpUtil(IndividualProperties config,
                   @Qualifier("objectMapper") ObjectMapper mapper,
                   ServiceRequestRepository serviceRequestRepository,
                   RestTemplate restTemplate) {
        this.config = config;
        this.mapper = mapper;
        this.serviceRequestRepository = serviceRequestRepository;
        this.restTemplate = restTemplate;
    }

    public OtpResponse sendOtp(IndividualRegisterRequest individualRequest){

        StringBuilder uri = new StringBuilder(config.getUserOtpHost())
                .append(config.getUserOtpSendEndpoint());

        log.info("OTP service URI: {}", uri.toString());

        var registerData = individualRequest.getIndividualRegister();

        // Check if both mobile number and email are null or empty
        boolean hasMobileNumber = registerData.getMobileNumber() != null && !registerData.getMobileNumber().isEmpty();
        boolean hasEmail = registerData.getEmailId() != null && !registerData.getEmailId().isEmpty();

        if (!hasMobileNumber && !hasEmail) {
            log.error("Both mobile number and email are null or empty for OTP request");
            throw new CustomException("CONTACT_REQUIRED", "At least one contact method (mobile number or email) is required for OTP");
        }

        // Determine username/identifier for OTP: prefer mobile, fallback to email
        String otpIdentifier = hasMobileNumber ? registerData.getMobileNumber() : registerData.getEmailId();

        log.info("Sending OTP to identifier: {}", otpIdentifier);
        log.info("Has mobile number: {}, Has email: {}", hasMobileNumber, hasEmail);

        // Build OTP request - only include mobile number if present
        Otp.OtpBuilder otpBuilder = Otp.builder()
                .userName(otpIdentifier)
                .type(OTP_TYPE_LOGIN)
                .tenantId(registerData.getTenantId())
                .userType(String.valueOf(UserType.CITIZEN));

        // Only set mobile number if it's present and not empty
        if (hasMobileNumber) {
            otpBuilder.mobileNumber(registerData.getMobileNumber());
            log.info("Including mobile number in OTP request");
        } else {
            log.info("Mobile number not present, using email for OTP");
        }

        Otp otp = otpBuilder.build();
        OtpRequest otpRequest = OtpRequest.builder()
                .otp(otp)
                .requestInfo(individualRequest.getRequestInfo())
                .build();

        log.info("OTP request prepared for tenant: {}, userType: {}", registerData.getTenantId(), UserType.CITIZEN);
        log.info("OTP request"+otpRequest);

        try{
            log.info("Calling OTP service at: {}", uri);
            LinkedHashMap responseMap = (LinkedHashMap)serviceRequestRepository.fetchResult(uri, otpRequest);
            OtpResponse otpResponse = mapper.convertValue(responseMap, OtpResponse.class);
            log.info("OTP sent successfully to {}", otpIdentifier);
            return otpResponse;
        }
        catch(IllegalArgumentException e)
        {
            log.error("Failed to send OTP: {}", e.getMessage(), e);
            throw new CustomException("SEND_OTP_FAILED","Failed to send OTP for user: " + e.getMessage());
        }
        catch(Exception e)
        {
            log.error("Unexpected error while sending OTP: {}", e.getMessage(), e);
            throw new CustomException("SEND_OTP_FAILED","Failed to send OTP for user: " + e.getMessage());
        }

    }

    public Boolean validateOtp(IndividualRegisterRequest individualRegisterRequest) {
        var individualData = individualRegisterRequest.getIndividualRegister();
        String otpIdentifier = (individualData.getMobileNumber() != null && !individualData.getMobileNumber().isEmpty())
                ? individualData.getMobileNumber()
                : individualData.getEmailId();
        OtpValidate otp = OtpValidate.builder().otp(individualData.getOtp()).identity(otpIdentifier).tenantId(individualData.getTenantId())
                .userType(UserType.CITIZEN).build();
        RequestInfo requestInfo = RequestInfo.builder().action("validate").ts(System.currentTimeMillis()).build();
        OtpValidateRequest otpValidationRequest = OtpValidateRequest.builder().requestInfo(requestInfo).otp(otp)
                .build();
        return validateEGovOtp(otpValidationRequest);

    }

    public boolean validateEGovOtp(OtpValidateRequest request) {

        StringBuilder otpValidateEndpoint = new StringBuilder(config.getEgovOtpServiceHost())
                .append(config.getOtpValidateEndpoint());

        log.info("OTP validation service URI: {}", otpValidateEndpoint.toString());

        if (request == null || request.getOtp() == null) {
            log.error("OTP validation request or OTP is null");
            return false;
        }

        log.info("Validating OTP for identity: {}, tenantId: {}", request.getOtp().getIdentity(), request.getOtp().getTenantId());
        log.info("Validate request"+request);

        try {
            OtpValidateResponse otpValidateResponse = restTemplate.postForObject(otpValidateEndpoint.toString(), request, OtpValidateResponse.class);

            if (otpValidateResponse == null) {
                log.error("OTP validation response is null");
                return false;
            }

            if (otpValidateResponse.getOtp() == null) {
                log.error("OTP object in validation response is null");
                return false;
            }

            boolean isValid = otpValidateResponse.getOtp().isValidationSuccessful();
            if (isValid) {
                log.info("OTP validation successful for identity: {}", request.getOtp().getIdentity());
            } else {
                log.warn("OTP validation failed for identity: {}", request.getOtp().getIdentity());
            }
            return isValid;

        } catch (HttpClientErrorException e) {
            log.error("OTP validation failed with HTTP error. Status: {}, Response: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new ServiceCallException(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unexpected error during OTP validation: {}", e.getMessage(), e);
            throw new ServiceCallException("OTP validation failed: " + e.getMessage());
        }
    }
}

