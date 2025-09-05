// pkg/dtos/profile_login.go
package dtos

// Handler input for sending OTP (you post a plain Aadhaar OR mobile here)
type ProfileLoginSendInput struct {
	RequestInfo RequestInfo `json:"RequestInfo" binding:"required"`
	// "aadhaar" or "mobile"
	LoginHint string `json:"loginHint" binding:"required"`
	// Plain Aadhaar number or mobile (we encrypt Aadhaar in handler)
	Value string `json:"value" binding:"required"`
	// "aadhaar" for Aadhaar OTP flow, "abdm" for mobile OTP flow (ABDM system)
	OtpSystem string `json:"otpSystem" binding:"required"`
	// Optional: override default scopes. If empty, we derive from LoginHint/OtpSystem.
	Scope []string `json:"scope"`
}

// Exact payload ABDM expects for /profile/login/request/otp
type ProfileLoginRequestOTP struct {
	RequestInfo RequestInfo `json:"RequestInfo" binding:"required"`
	Scope       []string    `json:"scope"`
	LoginHint   string      `json:"loginHint"` // "aadhaar" | "mobile"
	LoginId     string      `json:"loginId"`   // encrypted Aadhaar OR (for mobile) plain/enc per policy
	OTPSystem   string      `json:"otpSystem"` // "aadhaar" | "abdm"
}

// Handler input for verify step (you post txnId + plain OTP here)
type ProfileLoginVerifyInput struct {
	RequestInfo RequestInfo `json:"RequestInfo" binding:"required"`
	TxnId       string      `json:"txnId" binding:"required"`
	OtpValue    string      `json:"otpValue" binding:"required"` // plain OTP; we encrypt in handler
	// Optional: override scope (defaults same as send step)
	Scope []string `json:"scope"`
	// "aadhaar" | "abdm" (match what you used in send step)
	OtpSystem string `json:"otpSystem" binding:"required"`
	// "aadhaar" | "mobile" (match what you used in send step)
	LoginHint string `json:"loginHint" binding:"required"`
}

// Exact payload ABDM expects for /profile/login/verify
type ProfileLoginVerifyOTP struct {
	Scope    []string `json:"scope"`
	AuthData struct {
		AuthMethods []string `json:"authMethods"`
		OTP         struct {
			TxnId    string `json:"txnId"`
			OtpValue string `json:"otpValue"`
		} `json:"otp"`
	} `json:"authData"`
}

// Typical verify response (token etc.). Adjust if sandbox differs.
type ProfileLoginVerifyResponse struct {
	TxnId        string `json:"txnId"`
	Token        string `json:"token"`
	RefreshToken string `json:"refreshToken"`
	ABHANumber   string `json:"ABHANumber"`
	// sometimes preferredAbhaAddress / profile fields may appear
	PreferredAbhaAddress string `json:"preferredAbhaAddress"`
}
