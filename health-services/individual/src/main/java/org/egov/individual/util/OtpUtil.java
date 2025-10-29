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
                   ObjectMapper mapper,
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

        var registerData = individualRequest.getIndividualRegister();

        // Determine username/identifier for OTP: prefer mobile, fallback to email
        String otpIdentifier = (registerData.getMobileNumber() != null && !registerData.getMobileNumber().isEmpty())
                ? registerData.getMobileNumber()
                : registerData.getEmailId();

        Otp otp = Otp.builder()
                .userName(otpIdentifier)
                .mobileNumber(registerData.getMobileNumber())
                .type(OTP_TYPE_LOGIN)
                .tenantId(registerData.getTenantId())
                .userType(String.valueOf(UserType.CITIZEN))
                .build();
        OtpRequest otpRequest = OtpRequest.builder().otp(otp).requestInfo(individualRequest.getRequestInfo()).build();

        try{
            LinkedHashMap responseMap = (LinkedHashMap)serviceRequestRepository.fetchResult(uri, otpRequest);
            OtpResponse otpResponse = mapper.convertValue(responseMap,OtpResponse.class);
            return otpResponse;
        }
        catch(IllegalArgumentException  e)
        {
            throw new CustomException("SEND_OTP_FAILED","Failed to send Otp for user"+ e);
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
        try {
            OtpValidateResponse otpValidateResponse = restTemplate.postForObject(otpValidateEndpoint.toString(), request, OtpValidateResponse.class);
            if (null != otpValidateResponse && null != otpValidateResponse.getOtp())
                return otpValidateResponse.getOtp().isValidationSuccessful();
            else
                return false;
        } catch (HttpClientErrorException e) {
            log.error("Otp validation failed", e);
            throw new ServiceCallException(e.getResponseBodyAsString());
        }
    }
}

