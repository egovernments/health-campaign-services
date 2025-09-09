package errorsx

const (
	CodeInvalidPayload        = "INVALID_PAYLOAD"
	CodeInvalidLoginHint      = "INVALID_LOGIN_HINT"
	CodeInvalidOtpSystem      = "INVALID_OTP_SYSTEM"
	CodePublicKeyFetchFailed  = "PUBLIC_KEY_FETCH_FAILED"
	CodeEncryptFailed         = "ENCRYPT_FAILED"
	CodeAuthTokenFetchFailed  = "AUTH_TOKEN_FETCH_FAILED"
	CodeRequestOtpCallFailed  = "REQUEST_OTP_CALL_FAILED"
	CodeRequestOtpUpstream    = "REQUEST_OTP_UPSTREAM"
	CodeLoginVerifyCallFailed = "LOGIN_VERIFY_CALL_FAILED"
	CodeLoginVerifyUpstream   = "LOGIN_VERIFY_UPSTREAM"
	CodeInternal              = "INTERNAL_ERROR"
	CodePanic                 = "PANIC"
)
