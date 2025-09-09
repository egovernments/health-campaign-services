package handlers

import (
	"encoding/json"
	"log"
	"net/http"
	"regexp"
	"strings"
	"time"

	"digit-abdm/configs"
	"digit-abdm/internal/core/ports"
	"digit-abdm/internal/utils"
	"digit-abdm/pkg/dtos"

	"github.com/gin-gonic/gin"

	errorsx "digit-abdm/internal/errorsx"
)

type ABHAHandler struct {
	abhaService ports.ABHAService
	cfg         *configs.Config
}

func NewABHAHandler(abhaService ports.ABHAService, cfg *configs.Config) *ABHAHandler {
	return &ABHAHandler{
		abhaService: abhaService,
		cfg:         cfg,
	}
}

func (h *ABHAHandler) SendAadhaarOTP(c *gin.Context) {
	ctx := c.Request.Context()

	var req dtos.AadhaarRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[SendAadhaarOTP] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "Invalid request body", map[string]any{"details": err.Error()}, err))
		return
	}

	log.Printf("[SendAadhaarOTP] Start | aadhaar(len)=%d", len(strings.TrimSpace(req.AadhaarNumber)))
	if b, err := json.Marshal(map[string]any{"aadhaar": "***masked***"}); err == nil {
		log.Printf("[SendAadhaarOTP] Incoming request (masked): %s", string(b))
	}

	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		log.Printf("[SendAadhaarOTP] ERROR: FetchPublicKey failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "Failed to fetch public key", nil, err))
		return
	}
	log.Printf("[SendAadhaarOTP] Public key fetched")

	encryptedLoginID, err := h.abhaService.EncryptData(ctx, pubKey, req.AadhaarNumber)
	if err != nil {
		log.Printf("[SendAadhaarOTP] ERROR: EncryptData failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "Failed to encrypt Aadhaar number", nil, err))
		return
	}
	log.Printf("[SendAadhaarOTP] Aadhaar encrypted, cipher(len)=%d", len(encryptedLoginID))

	otpRequest := dtos.OTPRequest{
		Scope:     []string{"abha-enrol"},
		LoginHint: "aadhaar",
		LoginId:   encryptedLoginID,
		OTPSystem: "aadhaar",
	}

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[SendAadhaarOTP] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "Failed to fetch auth token", nil, err))
		return
	}
	log.Printf("[SendAadhaarOTP] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.SendOTPRequest(ctx, otpRequest, token)
	if err != nil {
		log.Printf("[SendAadhaarOTP] ERROR: SendOTPRequest failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[SendAadhaarOTP] OTP request OK, resp(len)=%d", len(resp))

	// Parse txnId and persist (idempotent for resend)
	var out struct {
		TxnId   string `json:"txnId"`
		Message string `json:"message"`
	}
	if err := json.Unmarshal(resp, &out); err != nil {
		log.Printf("[SendAadhaarOTP] WARN: resp unmarshal failed (best-effort): %v", err)
	}
	if out.TxnId != "" {
		tenantID := c.GetHeader("X-Tenant-Id")
		if strings.TrimSpace(tenantID) == "" {
			tenantID = "default"
		}
		if err := h.abhaService.RecordAadhaarTxnOnOtp(ctx, tenantID, req.AadhaarNumber, out.TxnId); err != nil {
			log.Printf("[SendAadhaarOTP] WARN: persist txn failed (non-fatal): %v", err)
		} else {
			log.Printf("[SendAadhaarOTP] Persisted txn | tenant=%s txnId=%s", tenantID, out.TxnId)
		}
	}

	errorsx.OK(c, resp)
}

func (h *ABHAHandler) VerifyAndEnrolByAadhaarWithOTP(c *gin.Context) {
	ctx := c.Request.Context()

	var input dtos.VerifyOTPInput
	if err := c.ShouldBindJSON(&input); err != nil {
		log.Printf("[VerifyAndEnrolByAadhaarWithOTP] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "Invalid request body", map[string]any{"details": err.Error()}, err))
		return
	}
	log.Printf("[VerifyAndEnrolByAadhaarWithOTP] Start | txnId=%s otp(len)=%d mobile(len)=%d",
		input.TxnId, len(input.Otp), len(strings.TrimSpace(input.Mobile)))

	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		log.Printf("[VerifyAndEnrolByAadhaarWithOTP] ERROR: FetchPublicKey failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "Failed to fetch public key", nil, err))
		return
	}
	log.Printf("[VerifyAndEnrolByAadhaarWithOTP] Public key fetched")

	encryptedOTP, err := h.abhaService.EncryptData(ctx, pubKey, input.Otp)
	if err != nil {
		log.Printf("[VerifyAndEnrolByAadhaarWithOTP] ERROR: EncryptData(otp) failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "Failed to encrypt OTP", nil, err))
		return
	}
	log.Printf("[VerifyAndEnrolByAadhaarWithOTP] OTP encrypted, cipher(len)=%d", len(encryptedOTP))

	enrolReq := dtos.EnrolByAadhaarWithOTPRequest{
		Consent: struct {
			Code    string `json:"code"`
			Version string `json:"version"`
		}{
			Code:    "abha-enrollment",
			Version: "1.4",
		},
	}
	enrolReq.AuthData.AuthMethods = []string{"otp"}
	enrolReq.AuthData.OTP.TimeStamp = time.Now().Format("2006-01-02 15:04:05")
	enrolReq.AuthData.OTP.TxnId = input.TxnId
	enrolReq.AuthData.OTP.OtpValue = encryptedOTP
	enrolReq.AuthData.OTP.Mobile = input.Mobile

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[VerifyAndEnrolByAadhaarWithOTP] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "Failed to fetch auth token", nil, err))
		return
	}
	log.Printf("[VerifyAndEnrolByAadhaarWithOTP] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.SendEnrolRequestWithOTP(ctx, enrolReq, token)
	if err != nil {
		log.Printf("[VerifyAndEnrolByAadhaarWithOTP] ERROR: service.SendEnrolRequestWithOTP failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[VerifyAndEnrolByAadhaarWithOTP] Success, resp(len)=%d", len(resp))

	errorsx.OK(c, resp)
}

func (h *ABHAHandler) GetAbhaCard(c *gin.Context) {
	var req struct {
		AbhaNumber string `json:"abha_number" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[GetAbhaCard] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "abha_number is required", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[GetAbhaCard] Start | abha=%s", maskMiddle(req.AbhaNumber))

	imageData, err := h.abhaService.FetchAbhaCard(ctx, req.AbhaNumber)
	if err != nil {
		log.Printf("[GetAbhaCard] ERROR: FetchAbhaCard failed: %v", err)
		c.Error(errorsx.Internal("ABHA_CARD_FETCH_FAILED", "failed to fetch ABHA card", nil, err))
		return
	}
	log.Printf("[GetAbhaCard] Success, image(len)=%d", len(imageData))

	// Binary response
	c.Data(http.StatusOK, "image/jpeg", imageData)
}

func (h *ABHAHandler) GetABHACardUnified(c *gin.Context) {
	var req dtos.AbhaCardRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[GetABHACardUnified] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "abha_number and card_type are required", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[GetABHACardUnified] Start | abha=%s cardType=%s", maskMiddle(req.AbhaNumber), req.CardType)

	imageData, contentType, err := h.abhaService.FetchAbhaCardByType(ctx, req.AbhaNumber, req.CardType)
	if err != nil {
		log.Printf("[GetABHACardUnified] ERROR: service.FetchAbhaCardByType failed: %v", err)
		c.Error(errorsx.Internal("ABHA_CARD_FETCH_FAILED", "failed to fetch ABHA card", nil, err))
		return
	}

	log.Printf("[GetABHACardUnified] Success | content-type=%s image(len)=%d", contentType, len(imageData))

	switch contentType {
	case "image/svg+xml":
		c.Header("Content-Disposition", "inline; filename=abha.svg")
	case "image/jpeg":
		c.Header("Content-Disposition", "inline; filename=abha.jpg")
	case "image/png":
		c.Header("Content-Disposition", "inline; filename=abha.png")
	}
	c.Data(http.StatusOK, contentType, imageData)
}

func (h *ABHAHandler) GetABHAQRCode(c *gin.Context) {
	var req dtos.AbhaQRCodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[GetABHAQRCode] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "abha_number is required", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[GetABHAQRCode] Start | abha=%s", maskMiddle(req.AbhaNumber))

	imageData, err := h.abhaService.FetchQRCodeByABHANumber(ctx, req.AbhaNumber)
	if err != nil {
		log.Printf("[GetABHAQRCode] ERROR: service.FetchQRCodeByABHANumber failed: %v", err)
		c.Error(errorsx.Internal("ABHA_QR_FETCH_FAILED", "failed to fetch ABHA QR code", nil, err))
		return
	}
	log.Printf("[GetABHAQRCode] Success | image(len)=%d", len(imageData))

	c.Header("Content-Disposition", "inline; filename=abha_qr.png")
	c.Data(http.StatusOK, "image/png", imageData)
}

func (h *ABHAHandler) RegisterRoutes(router *gin.RouterGroup) {
	// ABHA Create Routes
	createGroup := router.Group("/abha/create")
	{
		createGroup.POST("/send-aadhaar-otp", h.SendAadhaarOTP)
		createGroup.POST("/verify-and-enroll-with-aadhaar-otp", h.VerifyAndEnrolByAadhaarWithOTP)

		createGroup.POST("/link-mobile", h.LinkMobileNumber)
		createGroup.POST("/verify-mobile-otp", h.VerifyMobileOTP)
		createGroup.POST("/address-suggestion", h.AddressSuggestion)
		createGroup.POST("/enrol-address", h.EnrolAddress)

		createGroup.POST("/verify-aadhaar-otp-v2", h.VerifyAadhaarOtpAndCreateV2)
	}

	// ABHA Card Fetch Route
	cardGroup := router.Group("/abha/card")
	{
		cardGroup.POST("/fetch", h.GetABHACardUnified)      // v1
		cardGroup.POST("/fetch-v2", h.GetABHACardUnifiedV2) // v2
	}

	qrGroup := router.Group("/abha/qr")
	{
		qrGroup.POST("", h.GetABHAQRCode)
	}

	// ABHA Login
	loginGroup := router.Group("/abha/login")
	{
		loginGroup.POST("/send-otp", h.LoginSendOTP)
		loginGroup.POST("/verify-otp", h.LoginVerifyOTP)
		loginGroup.POST("/check-auth-methods", h.CheckAuthMethods)

		loginGroup.POST("/profile/request-otp", h.ProfileLoginRequestOTPv2)
		loginGroup.POST("/profile/verify-otp", h.ProfileLoginVerifyOTP)
	}
}

func (h *ABHAHandler) LinkMobileNumber(c *gin.Context) {
	var req dtos.LinkMobileRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[LinkMobileNumber] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[LinkMobileNumber] Start | mobile(len)=%d txnId=%s", len(strings.TrimSpace(req.Mobile)), req.TransactionID)

	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		log.Printf("[LinkMobileNumber] ERROR: FetchPublicKey failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "Failed to fetch public key", nil, err))
		return
	}
	log.Printf("[LinkMobileNumber] Public key fetched")

	encryptedMobile, err := h.abhaService.EncryptData(ctx, pubKey, req.Mobile)
	if err != nil {
		log.Printf("[LinkMobileNumber] ERROR: EncryptData(mobile) failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "Failed to encrypt mobile", nil, err))
		return
	}
	req.Mobile = encryptedMobile
	log.Printf("[LinkMobileNumber] Mobile encrypted, cipher(len)=%d", len(encryptedMobile))

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[LinkMobileNumber] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "auth token error", nil, err))
		return
	}
	log.Printf("[LinkMobileNumber] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.LinkMobileNumber(ctx, req, token)
	if err != nil {
		log.Printf("[LinkMobileNumber] ERROR: service.LinkMobileNumber failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[LinkMobileNumber] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

func (h *ABHAHandler) VerifyMobileOTP(c *gin.Context) {
	var req dtos.VerifyMobileOTPRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[VerifyMobileOTP] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[VerifyMobileOTP] Start | txnId=%s otp(len)=%d", req.TransactionID, len(req.Otp))

	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		log.Printf("[VerifyMobileOTP] ERROR: FetchPublicKey failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "Failed to fetch public key", nil, err))
		return
	}
	log.Printf("[VerifyMobileOTP] Public key fetched")

	encryptedOTP, err := h.abhaService.EncryptData(ctx, pubKey, req.Otp)
	if err != nil {
		log.Printf("[VerifyMobileOTP] ERROR: EncryptData(otp) failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "Failed to encrypt OTP", nil, err))
		return
	}
	req.Otp = encryptedOTP
	log.Printf("[VerifyMobileOTP] OTP encrypted, cipher(len)=%d", len(encryptedOTP))

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[VerifyMobileOTP] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "auth token error", nil, err))
		return
	}
	log.Printf("[VerifyMobileOTP] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.VerifyMobileOTP(ctx, req, token)
	if err != nil {
		log.Printf("[VerifyMobileOTP] ERROR: service.VerifyMobileOTP failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[VerifyMobileOTP] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

func (h *ABHAHandler) AddressSuggestion(c *gin.Context) {
	var req dtos.AddressSuggestionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[AddressSuggestion] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[AddressSuggestion] Start | txnId=%s", req.TransactionID)

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[AddressSuggestion] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "auth token error", nil, err))
		return
	}
	log.Printf("[AddressSuggestion] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.AddressSuggestion(ctx, req, token)
	if err != nil {
		log.Printf("[AddressSuggestion] ERROR: service.AddressSuggestion failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[AddressSuggestion] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

func (h *ABHAHandler) EnrolAddress(c *gin.Context) {
	var req dtos.EnrolAddressRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[EnrolAddress] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}
	ctx := c.Request.Context()
	log.Printf("[EnrolAddress] Start | txnId=%s abhaAddress(len)=%d", req.TransactionID, len(strings.TrimSpace(req.AbhaAddress)))

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[EnrolAddress] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "auth token error", nil, err))
		return
	}
	log.Printf("[EnrolAddress] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.EnrolAddress(ctx, req, token)
	if err != nil {
		log.Printf("[EnrolAddress] ERROR: service.EnrolAddress failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[EnrolAddress] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

func (h *ABHAHandler) LoginSendOTP(c *gin.Context) {
	var req dtos.SendOtpRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[LoginSendOTP] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[LoginSendOTP] Start | type=%s value(len)=%d otpSystem=%s",
		req.Type, len(strings.TrimSpace(req.Value)), req.OtpSystem)

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[LoginSendOTP] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "auth token error", nil, err))
		return
	}
	log.Printf("[LoginSendOTP] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.LoginSendOTP(ctx, req, token)
	if err != nil {
		log.Printf("[LoginSendOTP] ERROR: service.LoginSendOTP failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[LoginSendOTP] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

func (h *ABHAHandler) LoginVerifyOTP(c *gin.Context) {
	var req dtos.VerifyOtpRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[LoginVerifyOTP] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}
	ctx := c.Request.Context()
	log.Printf("[LoginVerifyOTP] Start | txnId=%s type=%s otpSystem=%s otp(len)=%d",
		req.TransactionID, req.Type, req.OtpSystem, len(strings.TrimSpace(req.Otp)))

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[LoginVerifyOTP] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "auth token error", nil, err))
		return
	}
	log.Printf("[LoginVerifyOTP] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.LoginVerifyOTP(ctx, req, token)
	if err != nil {
		log.Printf("[LoginVerifyOTP] ERROR: service.LoginVerifyOTP failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[LoginVerifyOTP] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

func (h *ABHAHandler) CheckAuthMethods(c *gin.Context) {
	var req dtos.CheckAuthMethodsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[CheckAuthMethods] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[CheckAuthMethods] Start | abhaAddress=%s", maskMiddle(req.AbhaAddress))

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[CheckAuthMethods] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "auth token error", nil, err))
		return
	}
	log.Printf("[CheckAuthMethods] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.LoginCheckAuthMethods(ctx, req, token)
	if err != nil {
		log.Printf("[CheckAuthMethods] ERROR: service.LoginCheckAuthMethods failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[CheckAuthMethods] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

// -------------------------- profile handler

// POST /abha/login/profile/request-otp
func (h *ABHAHandler) ProfileLoginRequestOTP(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.ProfileLoginSendInput
	if err := c.ShouldBindJSON(&in); err != nil {
		log.Printf("[ProfileLoginRequestOTP] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}
	log.Printf("[ProfileLoginRequestOTP] Start | loginHint=%s otpSystem=%s scope(len)=%d",
		in.LoginHint, in.OtpSystem, len(in.Scope))

	scope := in.Scope
	if len(scope) == 0 {
		scope = []string{"abha-login"}
		if in.OtpSystem == "aadhaar" {
			scope = append(scope, "aadhaar-verify")
		} else {
			scope = append(scope, "mobile-verify")
		}
	}

	loginId := in.Value
	loginHint := in.LoginHint
	otpSystem := in.OtpSystem

	if strings.EqualFold(loginHint, "abha-number") {
		if !utils.ValidateABHANumber(in.Value) {
			log.Printf("[ProfileLoginRequestOTP] ERROR: invalid ABHA format: %s", in.Value)
			c.Error(errorsx.BadRequest(errorsx.CodeInvalidLoginHint, "Expected 14 digits, optionally grouped like 91-XXXX-XXXX-XXXX", nil, nil))
			return
		}

		abhaCanon, _ := utils.CanonicalizeABHA(in.Value)
		log.Printf("[ProfileLoginRequestOTP] Canon ABHA=%s", abhaCanon)

		aadhaarPlain, err := h.abhaService.ResolveAadhaarFromABHA(ctx, abhaCanon)
		if err != nil {
			log.Printf("[ProfileLoginRequestOTP] ERROR: ResolveAadhaarFromABHA failed: %v", err)
			c.Error(errorsx.BadRequest("ABHA_RESOLUTION_FAILED", err.Error(), nil, err))
			return
		}

		pub, err := h.abhaService.FetchPublicKey(ctx)
		if err != nil {
			log.Printf("[ProfileLoginRequestOTP] ERROR: FetchPublicKey failed: %v", err)
			c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "failed to fetch public key", nil, err))
			return
		}
		cipher, err := h.abhaService.EncryptData(ctx, pub, aadhaarPlain)
		if err != nil {
			log.Printf("[ProfileLoginRequestOTP] ERROR: EncryptData(aadhaar) failed: %v", err)
			c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "failed to encrypt aadhaar", nil, err))
			return
		}

		loginId = cipher
		loginHint = "aadhaar"
		otpSystem = "aadhaar"

		if !sliceHas(scope, "aadhaar-verify") {
			scope = append(scope, "aadhaar-verify")
		}
	}

	if strings.EqualFold(loginHint, "aadhaar") && !strings.EqualFold(in.LoginHint, "abha-number") {
		pub, err := h.abhaService.FetchPublicKey(ctx)
		if err != nil {
			log.Printf("[ProfileLoginRequestOTP] ERROR: FetchPublicKey failed: %v", err)
			c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "failed to fetch public key", nil, err))
			return
		}
		cipher, err := h.abhaService.EncryptData(ctx, pub, in.Value)
		if err != nil {
			log.Printf("[ProfileLoginRequestOTP] ERROR: EncryptData(aadhaar) failed: %v", err)
			c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "failed to encrypt aadhaar", nil, err))
			return
		}
		loginId = cipher
		otpSystem = "aadhaar"
	}

	req := dtos.ProfileLoginRequestOTP{
		Scope:     scope,
		LoginHint: loginHint,
		LoginId:   loginId,
		OTPSystem: otpSystem,
	}

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[ProfileLoginRequestOTP] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "failed to fetch auth token", nil, err))
		return
	}
	log.Printf("[ProfileLoginRequestOTP] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.ProfileLoginRequestOTP(ctx, req, token)
	if err != nil {
		log.Printf("[ProfileLoginRequestOTP] ERROR: service.ProfileLoginRequestOTP failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[ProfileLoginRequestOTP] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

func (h *ABHAHandler) ProfileLoginRequestOTPv2(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.ProfileLoginSendInput
	if err := c.ShouldBindJSON(&in); err != nil {
		log.Printf("[ProfileLoginRequestOTPv2] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}
	log.Printf("[ProfileLoginRequestOTPv2] Start | loginHint=%s otpSystem=%s", in.LoginHint, in.OtpSystem)

	lh := strings.ToLower(strings.TrimSpace(in.LoginHint))
	switch lh {
	case "aadhaar", "mobile", "abha-number":
	default:
		log.Printf("[ProfileLoginRequestOTPv2] ERROR: invalid loginHint=%s", lh)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidLoginHint, "allowed: aadhaar | mobile | abha-number", nil, nil))
		return
	}

	otpSystem := strings.ToLower(strings.TrimSpace(in.OtpSystem))
	if otpSystem != "aadhaar" && otpSystem != "abdm" {
		log.Printf("[ProfileLoginRequestOTPv2] ERROR: invalid otpSystem=%s", otpSystem)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidOtpSystem, "allowed: aadhaar | abdm", nil, nil))
		return
	}

	scope := in.Scope
	if len(scope) == 0 {
		scope = []string{"abha-login"}
		if lh == "aadhaar" {
			scope = append(scope, "aadhaar-verify")
		} else {
			scope = append(scope, "mobile-verify")
		}
	}

	loginId := in.Value
	if lh == "aadhaar" || lh == "abha-number" {
		pub, err := h.abhaService.FetchPublicKey(ctx)
		if err != nil {
			log.Printf("[ProfileLoginRequestOTPv2] ERROR: FetchPublicKey failed: %v", err)
			c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "failed to fetch public key", nil, err))
			return
		}
		enc, err := h.abhaService.EncryptData(ctx, pub, in.Value)
		if err != nil {
			log.Printf("[ProfileLoginRequestOTPv2] ERROR: EncryptData(value) failed: %v", err)
			c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "failed to encrypt value", nil, err))
			return
		}
		loginId = enc
	}

	req := dtos.ProfileLoginRequestOTP{
		Scope:     scope,
		LoginHint: lh,
		LoginId:   loginId,
		OTPSystem: otpSystem,
	}

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[ProfileLoginRequestOTPv2] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "failed to fetch auth token", nil, err))
		return
	}
	log.Printf("[ProfileLoginRequestOTPv2] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.ProfileLoginRequestOTP(ctx, req, token)
	if err != nil {
		log.Printf("[ProfileLoginRequestOTPv2] ERROR: service.ProfileLoginRequestOTP failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[ProfileLoginRequestOTPv2] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

// POST /abha/login/profile/verify-otp
func (h *ABHAHandler) ProfileLoginVerifyOTP(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.ProfileLoginVerifyInput
	if err := c.ShouldBindJSON(&in); err != nil {
		log.Printf("[ProfileLoginVerifyOTP] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}
	log.Printf("[ProfileLoginVerifyOTP] Start | txnId=%s otpSystem=%s otp(len)=%d", in.TxnId, in.OtpSystem, len(in.OtpValue))

	// Default scopes
	scope := in.Scope
	if len(scope) == 0 {
		scope = []string{"abha-login"}
		if in.OtpSystem == "aadhaar" {
			scope = append(scope, "aadhaar-verify")
		} else {
			scope = append(scope, "mobile-verify")
		}
	}

	pub, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		log.Printf("[ProfileLoginVerifyOTP] ERROR: FetchPublicKey failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodePublicKeyFetchFailed, "failed to fetch public key", nil, err))
		return
	}
	cipherOTP, err := h.abhaService.EncryptData(ctx, pub, in.OtpValue)
	if err != nil {
		log.Printf("[ProfileLoginVerifyOTP] ERROR: EncryptData(otp) failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeEncryptFailed, "failed to encrypt otp", nil, err))
		return
	}
	log.Printf("[ProfileLoginVerifyOTP] OTP encrypted, cipher(len)=%d", len(cipherOTP))

	var req dtos.ProfileLoginVerifyOTP
	req.Scope = scope
	req.AuthData.AuthMethods = []string{"otp"}
	req.AuthData.OTP.TxnId = in.TxnId
	req.AuthData.OTP.OtpValue = cipherOTP

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		log.Printf("[ProfileLoginVerifyOTP] ERROR: FetchABDMToken failed: %v", err)
		c.Error(errorsx.Internal(errorsx.CodeAuthTokenFetchFailed, "failed to fetch auth token", nil, err))
		return
	}
	log.Printf("[ProfileLoginVerifyOTP] ABDM token fetched, len=%d", len(token))

	resp, err := h.abhaService.ProfileLoginVerifyOTP(ctx, req, token)
	if err != nil {
		log.Printf("[ProfileLoginVerifyOTP] ERROR: service.ProfileLoginVerifyOTP failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[ProfileLoginVerifyOTP] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

// ---- create v2

func (h *ABHAHandler) VerifyAadhaarOtpAndCreateV2(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.VerifyAndCreateV2Input
	if err := c.ShouldBindJSON(&in); err != nil {
		log.Printf("[VerifyAadhaarOtpAndCreateV2] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "invalid payload", map[string]any{"details": err.Error()}, err))
		return
	}
	log.Printf("[VerifyAadhaarOtpAndCreateV2] Start | txnId=%s tenant=%s clientRef=%s userId=%d",
		in.TxnId, in.TenantId, in.ClientReferenceId, in.UserID)

	// Lightweight pre-validation (fail fast)
	if !regexp.MustCompile(`^\d{6}$`).MatchString(in.Otp) {
		log.Printf("[VerifyAadhaarOtpAndCreateV2] ERROR: invalid OTP format")
		c.Error(errorsx.BadRequest("INVALID_OTP_FORMAT", "otp must be 6 digits", nil, nil))
		return
	}
	if in.Mobile != "" && !regexp.MustCompile(`^\d{10}$`).MatchString(in.Mobile) {
		log.Printf("[VerifyAadhaarOtpAndCreateV2] ERROR: invalid mobile format")
		c.Error(errorsx.BadRequest("INVALID_MOBILE_FORMAT", "mobile must be 10 digits", nil, nil))
		return
	}

	resp, err := h.abhaService.VerifyAadhaarOtpAndCreateIndividualV2(ctx, in)
	if err != nil {
		log.Printf("[VerifyAadhaarOtpAndCreateV2] ERROR: service.VerifyAadhaarOtpAndCreateIndividualV2 failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[VerifyAadhaarOtpAndCreateV2] Success | resp(len)=%d", len(resp))
	errorsx.OK(c, resp)
}

// v2 handler
func (h *ABHAHandler) GetABHACardUnifiedV2(c *gin.Context) {
	var req dtos.AbhaCardRequestV2
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[GetABHACardUnifiedV2] ERROR: bind JSON failed: %v", err)
		c.Error(errorsx.BadRequest(errorsx.CodeInvalidPayload, "abha_number and card_type are required", gin.H{"details": err.Error()}, err))
		return
	}

	if req.Token == "" && req.RefreshToken == "" {
		log.Printf("[GetABHACardUnifiedV2] ERROR: missing token(s)")
		c.Error(errorsx.BadRequest("MISSING_TOKEN", "either token or refresh_token must be provided", nil, nil))
		return
	}

	switch req.CardType {
	case "getCard", "getSvgCard", "getPngCard":
	default:
		log.Printf("[GetABHACardUnifiedV2] ERROR: invalid cardType=%s", req.CardType)
		c.Error(errorsx.BadRequest("INVALID_CARD_TYPE", "card_type must be one of: getCard, getSvgCard, getPngCard", nil, nil))
		return
	}

	ctx := c.Request.Context()
	log.Printf("[GetABHACardUnifiedV2] Start | abha=%s cardType=%s token(len)=%d refreshToken(len)=%d",
		maskMiddle(req.AbhaNumber), req.CardType, len(req.Token), len(req.RefreshToken))

	imageData, contentType, err := h.abhaService.FetchAbhaCardByTypeV2(
		ctx, req.AbhaNumber, req.CardType, req.Token, req.RefreshToken,
	)
	if err != nil {
		log.Printf("[GetABHACardUnifiedV2] ERROR: service.FetchAbhaCardByTypeV2 failed: %v", err)
		c.Error(err)
		return
	}
	log.Printf("[GetABHACardUnifiedV2] Success | contentType=%s image(len)=%d", contentType, len(imageData))

	switch contentType {
	case "image/svg+xml":
		c.Header("Content-Disposition", "inline; filename=abha.svg")
	case "image/jpeg":
		c.Header("Content-Disposition", "inline; filename=abha.jpg")
	case "image/png":
		c.Header("Content-Disposition", "inline; filename=abha.png")
	}
	c.Data(http.StatusOK, contentType, imageData)
}

// -------------------------- helpers (logging-safe) --------------------------

func sliceHas(arr []string, needle string) bool {
	for _, v := range arr {
		if v == needle {
			return true
		}
	}
	return false
}

func maskMiddle(s string) string {
	s = strings.TrimSpace(s)
	if s == "" {
		return ""
	}
	if len(s) <= 6 {
		return "***"
	}
	return s[:3] + "****" + s[len(s)-3:]
}
