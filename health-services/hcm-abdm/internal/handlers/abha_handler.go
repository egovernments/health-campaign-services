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

// func (h *ABHAHandler) RequestOTP(w http.ResponseWriter, r *http.Request) {
// 	ctx := r.Context()

// 	var otpReq dtos.OTPRequest
// 	if err := json.NewDecoder(r.Body).Decode(&otpReq); err != nil {
// 		http.Error(w, "Invalid request body", http.StatusBadRequest)
// 		return
// 	}

// 	pubKey, err := h.abhaService.FetchPublicKey(ctx)
// 	if err != nil {
// 		http.Error(w, "Failed to fetch public key: "+err.Error(), http.StatusInternalServerError)
// 		return
// 	}

// 	encryptedLoginID, err := h.abhaService.EncryptData(ctx, pubKey, otpReq.LoginId)
// 	if err != nil {
// 		http.Error(w, "Failed to encrypt data: "+err.Error(), http.StatusInternalServerError)
// 		return
// 	}
// 	otpReq.LoginId = encryptedLoginID

// 	token := r.Header.Get("Authorization")
// 	resp, err := h.abhaService.SendOTPRequest(ctx, otpReq, token)
// 	if err != nil {
// 		http.Error(w, "OTP request failed: "+err.Error(), http.StatusInternalServerError)
// 		return
// 	}

// 	w.Header().Set("Content-Type", "application/json")
// 	w.Write(resp)
// }

//////////////-------------------------------------------------------------

// func (h *ABHAHandler) SendAadhaarOTP(c *gin.Context) {
// 	ctx := c.Request.Context()

// 	// Step 1: Accept Aadhaar number from user
// 	var req dtos.AadhaarRequest
// 	if err := c.ShouldBindJSON(&req); err != nil {
// 		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body", "details": err.Error()})
// 		return
// 	}

// 	// Step 2: Fetch public key
// 	pubKey, err := h.abhaService.FetchPublicKey(ctx)
// 	if err != nil {
// 		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch public key", "details": err.Error()})
// 		return
// 	}

// 	// Step 3: Encrypt Aadhaar number (as LoginId)
// 	encryptedLoginID, err := h.abhaService.EncryptData(ctx, pubKey, req.AadhaarNumber)
// 	if err != nil {
// 		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to encrypt Aadhaar number", "details": err.Error()})
// 		return
// 	}

// 	// Step 4: Construct final OTPRequest
// 	otpRequest := dtos.OTPRequest{
// 		Scope:     []string{"abha-enrol"},
// 		LoginHint: "aadhaar",
// 		LoginId:   encryptedLoginID,
// 		OTPSystem: "aadhaar",
// 	}

// 	// Fetch token from util function
// 	token, err := utils.FetchABDMToken(ctx, h.cfg)
// 	if err != nil {
// 		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch auth token", "details": err.Error()})
// 		return
// 	}

// 	// Send OTP request via service
// 	resp, err := h.abhaService.SendOTPRequest(ctx, otpRequest, token)
// 	if err != nil {
// 		c.JSON(http.StatusInternalServerError, gin.H{"error": "OTP request failed", "details": err.Error()})
// 		return
// 	}

// 	c.Data(http.StatusOK, "application/json", resp)
// }

func (h *ABHAHandler) SendAadhaarOTP(c *gin.Context) {
	ctx := c.Request.Context()

	var req dtos.AadhaarRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body", "details": err.Error()})
		return
	}

	log.Printf("[SendOTPRequest] Request payload: %+v", req)
	// Debug log: print request payload
	if b, err := json.Marshal(req); err == nil {
		log.Printf("[SendAadhaarOTP] Incoming request: %s", string(b))
	} else {
		log.Printf("[SendAadhaarOTP] Failed to marshal request: %v", err)
	}
	// Fetch ABDM pubkey + encrypt Aadhaar for ABDM transport
	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch public key", "details": err.Error()})
		return
	}
	encryptedLoginID, err := h.abhaService.EncryptData(ctx, pubKey, req.AadhaarNumber)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to encrypt Aadhaar number", "details": err.Error()})
		return
	}

	otpRequest := dtos.OTPRequest{
		Scope:     []string{"abha-enrol"},
		LoginHint: "aadhaar",
		LoginId:   encryptedLoginID,
		OTPSystem: "aadhaar",
	}

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch auth token", "details": err.Error()})
		return
	}

	resp, err := h.abhaService.SendOTPRequest(ctx, otpRequest, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "OTP request failed", "details": err.Error()})
		return
	}

	// Parse txnId and persist (idempotent for resend)
	var out struct {
		TxnId   string `json:"txnId"`
		Message string `json:"message"`
	}
	_ = json.Unmarshal(resp, &out) // best-effort; if it fails we still return ABDM response
	if out.TxnId != "" {
		tenantID := c.GetHeader("X-Tenant-Id")
		if strings.TrimSpace(tenantID) == "" {
			tenantID = "default"
		}
		if err := h.abhaService.RecordAadhaarTxnOnOtp(ctx, tenantID, req.AadhaarNumber, out.TxnId); err != nil {
			// Don't fail the user call on persistence errors; log for ops
			log.Printf("[SendAadhaarOTP] WARN: failed to persist txn: %v", err)
		}
	}

	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) VerifyAndEnrolByAadhaarWithOTP(c *gin.Context) {
	ctx := c.Request.Context()

	// Step 1: Parse user input
	var input dtos.VerifyOTPInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body", "details": err.Error()})
		return
	}

	// Step 2: Fetch ABDM public key
	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch public key", "details": err.Error()})
		return
	}

	// Step 3: Encrypt the OTP
	encryptedOTP, err := h.abhaService.EncryptData(ctx, pubKey, input.Otp)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to encrypt OTP", "details": err.Error()})
		return
	}

	// Step 4: Build the final request
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
	enrolReq.AuthData.OTP.TimeStamp = time.Now().Format("2006-01-02 15:04:05") // correct format
	enrolReq.AuthData.OTP.TxnId = input.TxnId
	enrolReq.AuthData.OTP.OtpValue = encryptedOTP
	enrolReq.AuthData.OTP.Mobile = input.Mobile

	// Step 5: Get auth token
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch auth token", "details": err.Error()})
		return
	}

	// Step 6: Call service
	resp, err := h.abhaService.SendEnrolRequestWithOTP(ctx, enrolReq, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Enrolment request failed", "details": err.Error()})
		return
	}

	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) GetAbhaCard(c *gin.Context) {
	var req struct {
		AbhaNumber string `json:"abha_number" binding:"required"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "abha_number is required"})
		return
	}

	ctx := c.Request.Context()

	imageData, err := h.abhaService.FetchAbhaCard(ctx, req.AbhaNumber)
	if err != nil {
		log.Printf("Failed to fetch ABHA card: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch ABHA card"})
		return
	}

	// Return image as binary response
	c.Data(http.StatusOK, "image/jpeg", imageData)
}

func (h *ABHAHandler) GetABHACardUnified(c *gin.Context) {
	var req dtos.AbhaCardRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "abha_number and card_type are required"})
		return
	}

	ctx := c.Request.Context()

	imageData, contentType, err := h.abhaService.FetchAbhaCardByType(ctx, req.AbhaNumber, req.CardType)
	if err != nil {
		log.Printf("Failed to fetch ABHA card: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch ABHA card"})
		return
	}

	log.Printf("[Handler] Final content-type: %s", contentType)

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
		c.JSON(http.StatusBadRequest, gin.H{"error": "abha_number is required"})
		return
	}

	ctx := c.Request.Context()

	imageData, err := h.abhaService.FetchQRCodeByABHANumber(ctx, req.AbhaNumber)
	if err != nil {
		log.Printf("[Handler] Failed to fetch ABHA QR code: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch ABHA QR code"})
		return
	}

	c.Header("Content-Disposition", "inline; filename=abha_qr.png")
	c.Data(http.StatusOK, "image/png", imageData)
}

func (h *ABHAHandler) RegisterRoutes(router *gin.RouterGroup) {
	// ABHA Create Routes
	createGroup := router.Group("/abha/create")
	{
		createGroup.POST("/send-aadhaar-otp", h.SendAadhaarOTP)                                   // Send Aadhaar OTP
		createGroup.POST("/verify-and-enroll-with-aadhaar-otp", h.VerifyAndEnrolByAadhaarWithOTP) // Verify Aadhaar OTP and Create ABHA
		// createGroup.POST("/link-mobile", h.LinkMobile)                   // Link Mobile via OTP
		// createGroup.POST("/verify-mobile-otp", h.VerifyMobileOTP)        // Verify Mobile OTP
		// createGroup.POST("/address-suggestion", h.AddressSuggestion)     // Fetch ABHA address suggestions
		// createGroup.POST("/enrol-address", h.EnrolAddress)               // Enroll ABHA address
		// ABHA Create (additional)
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

		// loginGroup.POST("/profile/request-otp", h.ProfileLoginRequestOTP)
		loginGroup.POST("/profile/request-otp", h.ProfileLoginRequestOTPv2)
		loginGroup.POST("/profile/verify-otp", h.ProfileLoginVerifyOTP)

	}

}

//----------------------------------------------------------------------------

func (h *ABHAHandler) LinkMobileNumber(c *gin.Context) {
	var req dtos.LinkMobileRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}

	ctx := c.Request.Context()

	// Step 2: Fetch ABDM public key
	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch public key", "details": err.Error()})
		return
	}

	// Step 3: Encrypt the OTP
	encryptedMobile, err := h.abhaService.EncryptData(ctx, pubKey, req.Mobile)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to encrypt OTP", "details": err.Error()})
		return
	}

	req.Mobile = encryptedMobile

	// fetch auth-token
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "auth token error"})
		return
	}

	resp, err := h.abhaService.LinkMobileNumber(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) VerifyMobileOTP(c *gin.Context) {
	var req dtos.VerifyMobileOTPRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}

	ctx := c.Request.Context()

	// Step 2: Fetch ABDM public key
	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch public key", "details": err.Error()})
		return
	}

	// Step 3: Encrypt the OTP
	encryptedOTP, err := h.abhaService.EncryptData(ctx, pubKey, req.Otp)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to encrypt OTP", "details": err.Error()})
		return
	}
	req.Otp = encryptedOTP

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "auth token error"})
		return
	}

	resp, err := h.abhaService.VerifyMobileOTP(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) AddressSuggestion(c *gin.Context) {
	var req dtos.AddressSuggestionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}

	ctx := c.Request.Context()
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "auth token error"})
		return
	}

	resp, err := h.abhaService.AddressSuggestion(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) EnrolAddress(c *gin.Context) {
	var req dtos.EnrolAddressRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}
	ctx := c.Request.Context()

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "auth token error"})
		return
	}

	resp, err := h.abhaService.EnrolAddress(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) LoginSendOTP(c *gin.Context) {
	var req dtos.SendOtpRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}

	ctx := c.Request.Context()
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "auth token error"})
		return
	}

	resp, err := h.abhaService.LoginSendOTP(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) LoginVerifyOTP(c *gin.Context) {
	var req dtos.VerifyOtpRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}
	ctx := c.Request.Context()
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "auth token error"})
		return
	}

	resp, err := h.abhaService.LoginVerifyOTP(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) CheckAuthMethods(c *gin.Context) {
	var req dtos.CheckAuthMethodsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload"})
		return
	}

	ctx := c.Request.Context()
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "auth token error"})
		return
	}

	resp, err := h.abhaService.LoginCheckAuthMethods(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)

}

//// -------------------------- profile handler

// internal/handlers/abha_handler.go  (additions)

// POST /abha/login/profile/request-otp
// Body: { "loginHint":"aadhaar"|"mobile", "value":"<aadhaar|mobile>", "otpSystem":"aadhaar"|"abdm", "scope":[optional] }
func (h *ABHAHandler) ProfileLoginRequestOTP(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.ProfileLoginSendInput
	if err := c.ShouldBindJSON(&in); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload", "details": err.Error()})
		return
	}

	// Default scopes if not provided
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
	loginHint := in.LoginHint // "aadhaar" | "mobile" | "abha-number"
	otpSystem := in.OtpSystem // "aadhaar" | "abdm"

	// ABHA path -> resolve to Aadhaar and proceed as Aadhaar login
	if strings.EqualFold(loginHint, "abha-number") {
		if !utils.ValidateABHANumber(in.Value) {
			c.JSON(http.StatusBadRequest, gin.H{
				"error":   "INVALID_ABHA_FORMAT",
				"details": "Expected 14 digits, optionally grouped like 91-XXXX-XXXX-XXXX",
			})
			return
		}

		// Canonicalize to DB shape (XX-XXXX-XXXX-XXXX)
		abhaCanon, _ := utils.CanonicalizeABHA(in.Value)

		// Resolve linked Aadhaar (decrypt from DB)
		aadhaarPlain, err := h.abhaService.ResolveAadhaarFromABHA(ctx, abhaCanon)
		if err != nil {
			c.JSON(http.StatusNotFound, gin.H{
				"error":   "ABHA_RESOLUTION_FAILED",
				"details": err.Error(),
			})
			return
		}

		// Encrypt Aadhaar for ABDM login
		pub, err := h.abhaService.FetchPublicKey(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch public key", "details": err.Error()})
			return
		}
		cipher, err := h.abhaService.EncryptData(ctx, pub, aadhaarPlain)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to encrypt aadhaar", "details": err.Error()})
			return
		}

		loginId = cipher
		loginHint = "aadhaar" // ABDM accepts aadhaar or mobile, not abha-number
		otpSystem = "aadhaar"

		// Ensure scopes contain aadhaar-verify
		has := func(s string) bool {
			for _, v := range scope {
				if v == s {
					return true
				}
			}
			return false
		}
		if !has("aadhaar-verify") {
			scope = append(scope, "aadhaar-verify")
		}
	}

	// Direct Aadhaar path (non-ABHA)
	if strings.EqualFold(loginHint, "aadhaar") && !strings.EqualFold(in.LoginHint, "abha-number") {
		pub, err := h.abhaService.FetchPublicKey(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch public key", "details": err.Error()})
			return
		}
		cipher, err := h.abhaService.EncryptData(ctx, pub, in.Value)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to encrypt aadhaar", "details": err.Error()})
			return
		}
		loginId = cipher
		otpSystem = "aadhaar"
	}

	// Build ABDM request
	req := dtos.ProfileLoginRequestOTP{
		Scope:     scope,
		LoginHint: loginHint, // "aadhaar" or "mobile"
		LoginId:   loginId,   // encrypted Aadhaar or mobile
		OTPSystem: otpSystem, // "aadhaar" or "abdm"
	}

	// ABDM client token
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch auth token", "details": err.Error()})
		return
	}

	// Send
	resp, err := h.abhaService.ProfileLoginRequestOTP(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": "request-otp failed", "details": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

func (h *ABHAHandler) ProfileLoginRequestOTPv2(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.ProfileLoginSendInput
	if err := c.ShouldBindJSON(&in); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload", "details": err.Error()})
		return
	}

	// Validate/normalize loginHint
	lh := strings.ToLower(strings.TrimSpace(in.LoginHint))
	switch lh {
	case "aadhaar", "mobile", "abha-number":
	default:
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid loginHint", "details": "allowed: aadhaar | mobile | abha-number"})
		return
	}

	// Validate/normalize otpSystem
	otpSystem := strings.ToLower(strings.TrimSpace(in.OtpSystem))
	if otpSystem != "aadhaar" && otpSystem != "abdm" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid otpSystem", "details": "allowed: aadhaar | abdm"})
		return
	}

	// Derive default scopes if not provided
	scope := in.Scope
	if len(scope) == 0 {
		scope = []string{"abha-login"}
		if lh == "aadhaar" {
			scope = append(scope, "aadhaar-verify")
		} else {
			// mobile and abha-number -> mobile-verify (ABDM OTP system)
			scope = append(scope, "mobile-verify")
		}
	}

	// Prepare loginId (encrypt for aadhaar and abha-number)
	loginId := in.Value
	if lh == "aadhaar" || lh == "abha-number" {
		pub, err := h.abhaService.FetchPublicKey(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch public key", "details": err.Error()})
			return
		}
		enc, err := h.abhaService.EncryptData(ctx, pub, in.Value)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to encrypt value", "details": err.Error()})
			return
		}
		loginId = enc
	}

	// Build ABDM request body
	req := dtos.ProfileLoginRequestOTP{
		Scope:     scope,     // e.g., ["abha-login","aadhaar-verify"] or ["abha-login","mobile-verify"]
		LoginHint: lh,        // "aadhaar" | "mobile" | "abha-number"
		LoginId:   loginId,   // encrypted Aadhaar/ABHA-number, or plain mobile
		OTPSystem: otpSystem, // "aadhaar" | "abdm"
	}

	// ABDM client token
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch auth token", "details": err.Error()})
		return
	}

	// Call service (no extra header args)
	resp, err := h.abhaService.ProfileLoginRequestOTP(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": "request-otp failed", "details": err.Error()})
		return
	}

	c.Data(http.StatusOK, "application/json", resp)
}

// POST /abha/login/profile/verify-otp
// Body: { "txnId":"...", "otpValue":"123456", "otpSystem":"aadhaar"|"abdm", "loginHint":"aadhaar"|"mobile", "scope":[optional] }
func (h *ABHAHandler) ProfileLoginVerifyOTP(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.ProfileLoginVerifyInput
	if err := c.ShouldBindJSON(&in); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload", "details": err.Error()})
		return
	}

	// Default scopes if not provided
	scope := in.Scope
	if len(scope) == 0 {
		scope = []string{"abha-login"}
		if in.OtpSystem == "aadhaar" {
			scope = append(scope, "aadhaar-verify")
		} else {
			scope = append(scope, "mobile-verify")
		}
	}

	otpValue := in.OtpValue
	// Encrypt OTP (ABDM expects encrypted OTP for this profile flow)
	pub, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch public key", "details": err.Error()})
		return
	}
	cipherOTP, err := h.abhaService.EncryptData(ctx, pub, in.OtpValue)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to encrypt otp", "details": err.Error()})
		return
	}
	otpValue = cipherOTP

	var req dtos.ProfileLoginVerifyOTP
	req.Scope = scope
	req.AuthData.AuthMethods = []string{"otp"}
	req.AuthData.OTP.TxnId = in.TxnId
	req.AuthData.OTP.OtpValue = otpValue

	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch auth token", "details": err.Error()})
		return
	}

	resp, err := h.abhaService.ProfileLoginVerifyOTP(ctx, req, token)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": "verify-otp failed", "details": err.Error()})
		return
	}
	c.Data(http.StatusOK, "application/json", resp)
}

////  --- create v2

// POST /abha/create/verify-aadhaar-otp-v2
func (h *ABHAHandler) VerifyAadhaarOtpAndCreateV2(c *gin.Context) {
	ctx := c.Request.Context()

	var in dtos.VerifyAndCreateV2Input
	if err := c.ShouldBindJSON(&in); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid payload", "details": err.Error()})
		return
	}

	// Lightweight pre-validation to fail fast before we call ABDM
	if !regexp.MustCompile(`^\d{6}$`).MatchString(in.Otp) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "INVALID_OTP_FORMAT", "details": "otp must be 6 digits"})
		return
	}
	if in.Mobile != "" && !regexp.MustCompile(`^\d{10}$`).MatchString(in.Mobile) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "INVALID_MOBILE_FORMAT", "details": "mobile must be 10 digits"})
		return
	}

	// Fetch ABDM client token (M2M)
	// token, err := utils.FetchABDMToken(ctx, h.cfg)
	// if err != nil {
	// 	c.JSON(http.StatusInternalServerError, gin.H{"error": "ABDM_TOKEN_FAILED", "details": err.Error()})
	// 	return
	// }

	// Delegate to service
	resp, err := h.abhaService.VerifyAadhaarOtpAndCreateIndividualV2(ctx, in)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "VERIFY_AND_CREATE_FAILED", "details": err.Error()})
		return
	}

	c.Data(http.StatusOK, "application/json", resp)
}

// v2 handler
func (h *ABHAHandler) GetABHACardUnifiedV2(c *gin.Context) {
	var req dtos.AbhaCardRequestV2
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "abha_number and card_type are required"})
		return
	}

	// Require at least one: token or refresh_token
	if req.Token == "" && req.RefreshToken == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "either token or refresh_token must be provided"})
		return
	}

	// Optional: strict card_type validation here to fail fast
	switch req.CardType {
	case "getCard", "getSvgCard", "getPngCard":
	default:
		c.JSON(http.StatusBadRequest, gin.H{"error": "card_type must be one of: getCard, getSvgCard, getPngCard"})
		return
	}

	ctx := c.Request.Context()

	imageData, contentType, err := h.abhaService.FetchAbhaCardByTypeV2(
		ctx, req.AbhaNumber, req.CardType, req.Token, req.RefreshToken,
	)
	if err != nil {
		log.Printf("[HandlerV2] Fetch failed: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch ABHA card"})
		return
	}

	log.Printf("[HandlerV2] Final content-type: %s", contentType)

	switch contentType {
	case "image/svg+xml":
		c.Header("Content-Disposition", "inline; filename=abha.svg")
	case "image/jpeg":
		c.Header("Content-Disposition", "inline; filename=abha.jpg")
	case "image/png":
		c.Header("Content-Disposition", "inline; filename=abha.png")
	default:
		// Keep a sane default for unknown types (browser will handle)
	}

	c.Data(http.StatusOK, contentType, imageData)
}
