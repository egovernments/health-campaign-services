package handlers

import (
	"log"
	"net/http"
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

func (h *ABHAHandler) SendAadhaarOTP(c *gin.Context) {
	ctx := c.Request.Context()

	// Step 1: Accept Aadhaar number from user
	var req dtos.AadhaarRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body", "details": err.Error()})
		return
	}

	// Step 2: Fetch public key
	pubKey, err := h.abhaService.FetchPublicKey(ctx)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch public key", "details": err.Error()})
		return
	}

	// Step 3: Encrypt Aadhaar number (as LoginId)
	encryptedLoginID, err := h.abhaService.EncryptData(ctx, pubKey, req.AadhaarNumber)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to encrypt Aadhaar number", "details": err.Error()})
		return
	}

	// Step 4: Construct final OTPRequest
	otpRequest := dtos.OTPRequest{
		Scope:     []string{"abha-enrol"},
		LoginHint: "aadhaar",
		LoginId:   encryptedLoginID,
		OTPSystem: "aadhaar",
	}

	// Fetch token from util function
	token, err := utils.FetchABDMToken(ctx, h.cfg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch auth token", "details": err.Error()})
		return
	}

	// Send OTP request via service
	resp, err := h.abhaService.SendOTPRequest(ctx, otpRequest, token)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "OTP request failed", "details": err.Error()})
		return
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
	}

	// ABHA Card Fetch Route
	cardGroup := router.Group("/abha/card")
	{
		cardGroup.POST("/fetch", h.GetABHACardUnified) // Handles getCard, getSvgCard, getPngCard
	}

	qrGroup := router.Group("/abha/qr")
	{
		qrGroup.POST("", h.GetABHAQRCode)
	}

	// // ABHA Login Routes
	// loginGroup := router.Group("/abha/login")
	// {
	// 	loginGroup.POST("/send-otp", h.SendLoginOTP)     // Send Login OTP
	// 	loginGroup.POST("/verify-otp", h.VerifyLoginOTP) // Verify Login OTP
	// }
}
