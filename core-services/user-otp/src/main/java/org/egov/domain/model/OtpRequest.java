package org.egov.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.egov.domain.exception.InvalidOtpRequestException;

import static org.springframework.util.StringUtils.isEmpty;

@Getter
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@NoArgsConstructor
public class OtpRequest {
	@Setter
    private String userName;
	@Setter
	private String email;
	private String mobileNumber;
    private String tenantId;

	@Setter
    private OtpRequestType type;

	private String userType;

    public void validate() {
        if(isTenantIdAbsent()
				|| isInvalidType()) {
            throw new InvalidOtpRequestException(this);
        }
    }

	public boolean isMobileNumberNumeric() {
		// TODO Auto-generated method stub
		if(!(type!=null && type.toString().equalsIgnoreCase(OtpRequestType.PASSWORD_RESET.toString())))
			return !StringUtils.isNumeric(mobileNumber);
		return false;
	}

	public boolean isMobileNumberValidLength() {
		// TODO Auto-generated method stub
		if(!(type!=null && type.toString().equalsIgnoreCase(OtpRequestType.PASSWORD_RESET.toString())))
			return !(mobileNumber != null && mobileNumber.matches("^[0-9]{10,13}$"));
		return false;
	}

	public boolean isRegistrationRequestType() {
    	return OtpRequestType.REGISTER.equals(getType());
	}
	
	public boolean isLoginRequestType() {
    	return OtpRequestType.LOGIN.equals(getType());
	}

	public boolean isInvalidType() {
    	return isEmpty(type);
	}

	public boolean isTenantIdAbsent() {
        return isEmpty(tenantId);
    }

	public boolean isMobileNumberAbsent() {
		return isEmpty(mobileNumber);
	}
}
