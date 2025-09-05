// pkg/dtos/verify_create_v2.go
package dtos

// Client → Handler
type VerifyAndCreateV2Input struct {
	RequestInfo RequestInfo `json:"RequestInfo" binding:"required"`
	// ABDM verify inputs
	TxnId string `json:"txnId" binding:"required"`
	Otp   string `json:"otp"   binding:"required"` // plain OTP; we encrypt in handler
	// Optional if sandbox asks
	Mobile string `json:"mobile,omitempty"`

	// For Individual create
	TenantId          string `json:"tenantId"           binding:"required"`
	ClientReferenceId string `json:"clientReferenceId"  binding:"required"`
	// Optional hints (fallbacks used when profile doesn’t carry these)
	LocalityCode string `json:"localityCode,omitempty"`

	// Downstream (HCM) RequestInfo
	// In your sample, RequestInfo.authToken and userInfo.{id,uuid} are needed.
	HcmAuthToken string `json:"hcmAuthToken" binding:"required"`
	UserID       int64  `json:"userId"       binding:"required"`
	UserUUID     string `json:"userUuid"     binding:"required"`
}

// ABDM enrol-by-Aadhaar verify payload (v2 uses the same endpoint you already have)
type EnrolByAadhaarWithOTPRequestV2 struct {
	Consent struct {
		Code    string `json:"code"`
		Version string `json:"version"`
	} `json:"consent"`
	AuthData struct {
		AuthMethods []string `json:"authMethods"`
		OTP         struct {
			TimeStamp string `json:"timeStamp"`
			TxnId     string `json:"txnId"`
			OtpValue  string `json:"otpValue"`
			Mobile    string `json:"mobile,omitempty"`
		} `json:"otp"`
	} `json:"authData"`
}

// Minimal shape we use from ABDM verify response
// dtos/enrol_with_otp_v2.go  (new or replace your current V2 struct)
type EnrolWithOTPResponseV2 struct {
	TxnId  string `json:"txnId"`
	Tokens struct {
		Token            string `json:"token"`
		RefreshToken     string `json:"refreshToken"`
		ExpiresIn        *int   `json:"expiresIn,omitempty"`
		RefreshExpiresIn *int   `json:"refreshExpiresIn,omitempty"`
	} `json:"tokens"`
	ABHAProfile ABHAProfileV2 `json:"ABHAProfile"`
}

type ABHAProfileV2 struct {
	// Some payloads send ABHANumber, others send healthIdNumber.
	// Keep both and pick the first non-empty.
	ABHANumber     string `json:"ABHANumber"`
	HealthIdNumber string `json:"healthIdNumber"`

	FirstName    string   `json:"firstName"`
	MiddleName   string   `json:"middleName"`
	LastName     string   `json:"lastName"`
	Gender       string   `json:"gender"`
	Dob          string   `json:"dob"`
	Mobile       string   `json:"mobile"`
	Email        *string  `json:"email"` // can be null -> must be *string
	Address      string   `json:"address"`
	DistrictCode string   `json:"districtCode"`
	StateCode    string   `json:"stateCode"`
	PinCode      string   `json:"pinCode"`
	PhrAddress   []string `json:"phrAddress"`
	Photo        string   `json:"photo"`
}

// Minimal shape from HCM individual create response (we pass it through)
type IndividualCreateResponse struct {
	Individual struct {
		Id                string `json:"id"`
		IndividualId      string `json:"individualId"`
		ClientReferenceId string `json:"clientReferenceId"`
		AdditionalFields  any    `json:"additionalFields"`
	} `json:"Individual"`
}
