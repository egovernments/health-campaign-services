package dtos

// ---------- ABHA CREATE REQUEST DTOs ----------

type VerifyAadhaarDemographicsRequest struct {
	TransactionID string `json:"transaction_id"`
	Aadhaar       string `json:"aadhaar"`
	Name          string `json:"name"`
	Gender        string `json:"gender"`
	DateOfBirth   string `json:"date_of_birth"` // Format: dd-mm-yyyy
	StateCode     string `json:"state_code"`
	DistrictCode  string `json:"district_code"`
	Address       string `json:"address"`
	PinCode       string `json:"pin_code"`
	Mobile        string `json:"mobile"`
	ProfilePhoto  string `json:"profile_photo"`
}

type SendAadhaarOtpRequest struct {
	TransactionID string `json:"transaction_id"`
	Aadhaar       string `json:"aadhaar"`
}

type VerifyAadhaarOtpRequest struct {
	TransactionID string `json:"transaction_id"`
	Otp           string `json:"otp"`
	Mobile        string `json:"mobile"`
}

type LinkMobileNumberRequest struct {
	TransactionID string `json:"transaction_id"`
	Mobile        string `json:"mobile"`
}

type VerifyMobileOtpRequest struct {
	TransactionID string `json:"transaction_id"`
	Otp           string `json:"otp"`
}

type AbhaAddressSuggestionRequest struct {
	TransactionID string `json:"transaction_id"`
}

type EnrolAbhaAddressRequest struct {
	TransactionID string `json:"transaction_id"`
	AbhaAddress   string `json:"abha_address"`
}

// ---------- ABHA LOGIN REQUEST DTOs ----------

type SendOtpRequest struct {
	Type      string `json:"type"`       // e.g. abha-address, mobile
	OtpSystem string `json:"otp_system"` // e.g. aadhaar, abdm
	Value     string `json:"value"`
}

type VerifyOtpRequest struct {
	Type          string `json:"type"`
	OtpSystem     string `json:"otp_system"`
	TransactionID string `json:"transaction_id"`
	Otp           string `json:"otp"`
}

type CheckAuthMethodsRequest struct {
	AbhaAddress string `json:"abha_address"`
}

// ---------- LINK PATIENT REQUEST DTO ----------

type LinkAbhaToPatientRequest struct {
	AbhaNumber string `json:"abha_number"`
	Patient    string `json:"patient"`
}

// --------- OTPRequest -------------------------- 25 July
type OTPRequest struct {
	Scope     []string `json:"scope"`
	LoginHint string   `json:"loginHint"`
	LoginId   string   `json:"loginId"`
	OTPSystem string   `json:"otpSystem"`
}

type EnrolByAadhaarWithOTPRequest struct {
	AuthData struct {
		AuthMethods []string `json:"authMethods"`
		OTP         struct {
			TimeStamp string `json:"timeStamp"`
			TxnId     string `json:"txnId"`
			OtpValue  string `json:"otpValue"`
			Mobile    string `json:"mobile"`
		} `json:"otp"`
	} `json:"authData"`
	Consent struct {
		Code    string `json:"code"`
		Version string `json:"version"`
	} `json:"consent"`
}

type SendAadhaarOTPRequest struct {
	Aadhaar       string `json:"aadhaar"`
	TransactionID string `json:"transaction_id,omitempty"`
}

type VerifyAadhaarOTPRequest struct {
	TransactionID string `json:"transaction_id"`
	Otp           string `json:"otp"`
	Mobile        string `json:"mobile"`
}

type LinkMobileRequest struct {
	Mobile        string `json:"mobile"`
	TransactionID string `json:"transaction_id"`
}

type VerifyMobileOTPRequest struct {
	TransactionID string `json:"transaction_id"`
	Otp           string `json:"otp"`
}

type AddressSuggestionRequest struct {
	TransactionID string `json:"transaction_id"`
}

type EnrolAddressRequest struct {
	TransactionID string `json:"transaction_id"`
	AbhaAddress   string `json:"abha_address"`
}

//  ------------------------------------------
//   ------------------------------------------

type AadhaarRequest struct {
	AadhaarNumber string `json:"aadhaarNumber" binding:"required"`
}

type VerifyOTPInput struct {
	TxnId  string `json:"txnId" binding:"required"`
	Otp    string `json:"otp" binding:"required"`
	Mobile string `json:"mobile" binding:"required"`
}

type AbhaCardRequest struct {
	AbhaNumber string `json:"abha_number" binding:"required"`
	CardType   string `json:"card_type" binding:"required"` // "getCard", "getSvgCard", "getPngCard"
}

type AbhaQRCodeRequest struct {
	AbhaNumber string `json:"abha_number" binding:"required"`
}
