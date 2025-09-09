// package services

// import (
// 	"bytes"
// 	"context"
// 	"crypto/rand"
// 	"crypto/rsa"
// 	"crypto/sha1"
// 	"crypto/x509"
// 	"digit-abdm/internal/utils"
// 	"encoding/base64"
// 	"encoding/json"
// 	"encoding/pem"
// 	"fmt"
// 	"io"
// 	"log"
// 	"net/http"
// 	"regexp"
// 	"strings"
// 	"time"

// 	"github.com/google/uuid"

// 	"digit-abdm/configs"
// 	"digit-abdm/internal/core/domain"
// 	"digit-abdm/internal/core/ports"
// 	errorsx "digit-abdm/internal/errorsx"
// 	"digit-abdm/pkg/dtos"
// )

// type abhaService struct {
// 	cfg        *configs.Config
// 	httpClient *http.Client
// 	abhaRepo   ports.AbhaRepository
// }

// func NewABHAService(cfg *configs.Config, repo ports.AbhaRepository) ports.ABHAService {
// 	return &abhaService{
// 		cfg:        cfg,
// 		httpClient: &http.Client{},
// 		abhaRepo:   repo,
// 	}
// }

// // FetchPublicKey retrieves RSA public key
// func (s *abhaService) FetchPublicKey(ctx context.Context) (*rsa.PublicKey, error) {
// 	log.Printf("[FetchPublicKey] Starting public key fetch from URL: %s", s.cfg.PublicKeyURL)

// 	req, err := http.NewRequestWithContext(ctx, "GET", s.cfg.PublicKeyURL, nil)
// 	if err != nil {
// 		log.Printf("[FetchPublicKey] ERROR: Failed to create HTTP request: %v", err)
// 		return nil, errorsx.Internal("PUBLIC_KEY_REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
// 	}

// 	log.Printf("[FetchPublicKey] Sending HTTP GET request...")
// 	resp, err := s.httpClient.Do(req)
// 	if err != nil {
// 		log.Printf("[FetchPublicKey] ERROR: HTTP request failed: %v", err)
// 		return nil, errorsx.Internal("PUBLIC_KEY_HTTP_FAILED", "public key fetch failed", nil, err)
// 	}
// 	defer resp.Body.Close()

// 	log.Printf("[FetchPublicKey] Response received with status: %d", resp.StatusCode)

// 	certPEM, err := io.ReadAll(resp.Body)
// 	if err != nil {
// 		log.Printf("[FetchPublicKey] ERROR: Failed to read response body: %v", err)
// 		return nil, errorsx.Internal("PUBLIC_KEY_READ_FAILED", "failed to read public key response", nil, err)
// 	}

// 	log.Printf("[FetchPublicKey] Response body length: %d bytes", len(certPEM))

// 	block, _ := pem.Decode(certPEM)
// 	if block == nil {
// 		log.Printf("[FetchPublicKey] ERROR: Failed to parse PEM block")
// 		return nil, errorsx.Internal("PEM_PARSE_FAILED", "failed to parse PEM block", nil, nil)
// 	}

// 	log.Printf("[FetchPublicKey] PEM block decoded successfully, type: %s", block.Type)

// 	pubKey, err := x509.ParsePKIXPublicKey(block.Bytes)
// 	if err != nil {
// 		log.Printf("[FetchPublicKey] ERROR: Failed to parse public key: %v", err)
// 		return nil, errorsx.Internal("PUBLIC_KEY_PARSE_FAILED", "failed to parse public key", nil, err)
// 	}

// 	rsaPubKey, ok := pubKey.(*rsa.PublicKey)
// 	if !ok {
// 		log.Printf("[FetchPublicKey] ERROR: Key is not RSA type")
// 		return nil, errorsx.Internal("PUBLIC_KEY_TYPE_INVALID", "not RSA public key", nil, nil)
// 	}

// 	log.Printf("[FetchPublicKey] SUCCESS: RSA public key fetched successfully, key size: %d bits", rsaPubKey.Size()*8)
// 	return rsaPubKey, nil
// }

// func (s *abhaService) EncryptData(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
// 	log.Printf("[EncryptData] Starting data encryption, data length: %d characters", len(data))

// 	// Use SHA-1 (per ABDM / DevGlan config)
// 	hash := sha1.New()
// 	log.Printf("[EncryptData] Using SHA-1 hash algorithm for OAEP encryption")

// 	encryptedBytes, err := rsa.EncryptOAEP(hash, rand.Reader, publicKey, []byte(data), nil)
// 	if err != nil {
// 		log.Printf("[EncryptData] ERROR: RSA OAEP encryption failed: %v", err)
// 		return "", errorsx.Internal("OAEP_ENCRYPT_FAILED", "RSA OAEP encryption failed", nil, err)
// 	}

// 	log.Printf("[EncryptData] Encryption successful, encrypted bytes length: %d", len(encryptedBytes))

// 	// Encode to base64
// 	encoded := base64.StdEncoding.EncodeToString(encryptedBytes)
// 	log.Printf("[EncryptData] Base64 encoding completed, encoded string length: %d", len(encoded))

// 	return encoded, nil
// }

// ////////////// --------------------------------------------------------------------------------------------

// func (s *abhaService) SendOTPRequest(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error) {
// 	log.Printf("[SendOTPRequest] Initiating OTP request to URL: %s", s.cfg.OTPRequestURL)
// 	log.Printf("[SendOTPRequest] Request payload: %+v", req)
// 	log.Printf("[SendOTPRequest] Using token length: %d characters", len(token))

// 	response, err := s.sendRequest(ctx, s.cfg.OTPRequestURL, req, token)
// 	if err != nil {
// 		log.Printf("[SendOTPRequest] ERROR: OTP request failed: %v", err)
// 		return nil, err
// 	}

// 	log.Printf("[SendOTPRequest] SUCCESS: OTP request completed, response length: %d bytes", len(response))
// 	return response, nil
// }

// func (s *abhaService) RecordAadhaarTxnOnOtp(ctx context.Context, tenantID, aadhaarPlain, txnID string) error {
// 	// Basic sanity — 12 digits Aadhaar
// 	if ok := regexp.MustCompile(`^\d{12}$`).MatchString(aadhaarPlain); !ok {
// 		return errorsx.BadRequest("INVALID_AADHAAR_FORMAT", "aadhaar must be 12 digits", nil, nil)
// 	}
// 	enc, err := utils.EncryptForDB(aadhaarPlain)
// 	if err != nil {
// 		return errorsx.Internal("AADHAAR_ENCRYPT_FAILED", "failed to encrypt aadhaar for db", nil, err)
// 	}
// 	hash := utils.HashForLookup(aadhaarPlain)
// 	actor := "system" // or pull from context
// 	_, err = s.abhaRepo.UpsertAadhaarTxnOnOtp(ctx, tenantID, txnID, enc, hash, actor)
// 	if err != nil {
// 		return errorsx.Internal("TXN_UPSERT_FAILED", "failed to upsert aadhaar txn", nil, err)
// 	}
// 	log.Printf("[RecordAadhaarTxnOnOtp] Upsert done | tenant=%s txn=%s", tenantID, txnID)
// 	return nil
// }

// func (s *abhaService) SendEnrolRequestWithOTP(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error) {
// 	log.Printf("[SendEnrolRequestWithOTP] Starting enrolment request to URL: %s", s.cfg.EnrolByAadhaarURL)
// 	log.Printf("[SendEnrolRequestWithOTP] Request payload: %+v", req)
// 	log.Printf("[SendEnrolRequestWithOTP] Using token length: %d characters", len(token))

// 	respBody, err := s.sendRequest(ctx, s.cfg.EnrolByAadhaarURL, req, token)
// 	if err != nil {
// 		log.Printf("[SendEnrolRequestWithOTP] ERROR: Enrolment request failed: %v", err)
// 		return nil, err
// 	}

// 	log.Printf("[SendEnrolRequestWithOTP] Response received, length: %d bytes", len(respBody))

// 	// Parse response
// 	var enrolResp dtos.EnrolWithOTPResponse
// 	if err := json.Unmarshal(respBody, &enrolResp); err != nil {
// 		log.Printf("[SendEnrolRequestWithOTP] ERROR: Failed to parse enrolment response: %v", err)
// 		return nil, errorsx.Internal("ENROL_PARSE_FAILED", "failed to parse enrolment response", nil, err)
// 	}

// 	log.Printf("[SendEnrolRequestWithOTP] Response parsed successfully")
// 	log.Printf("[SendEnrolRequestWithOTP] Response details: %+v", enrolResp)

// 	// Map response to model
// 	profile := enrolResp.ABHAProfile
// 	tokens := enrolResp.Tokens
// 	now := time.Now().UTC()

// 	log.Printf("[SendEnrolRequestWithOTP] ABHA profile extracted: %+v", profile)
// 	log.Printf("[SendEnrolRequestWithOTP] Tokens extracted: %+v", tokens)
// 	abha := &domain.AbhaNumber{
// 		ExternalID:       uuid.New().String(),
// 		ABHANumber:       profile.ABHANumber,
// 		HealthID:         "",
// 		Email:            profile.Email,
// 		FirstName:        profile.FirstName,
// 		MiddleName:       profile.MiddleName,
// 		LastName:         profile.LastName,
// 		ProfilePhoto:     profile.Photo,
// 		AccessToken:      tokens.Token,
// 		RefreshToken:     tokens.RefreshToken,
// 		Address:          profile.Address,
// 		DateOfBirth:      profile.Dob,
// 		District:         profile.DistrictCode,
// 		Gender:           profile.Gender,
// 		Name:             profile.FirstName + " " + profile.LastName,
// 		Pincode:          profile.PinCode,
// 		State:            profile.StateCode,
// 		Mobile:           profile.Mobile,
// 		New:              true,
// 		CreatedBy:        0, // or actual user ID if available
// 		LastModifiedBy:   0, // or actual user ID if available
// 		CreatedDate:      now,
// 		LastModifiedDate: now,
// 	}

// 	if len(profile.PhrAddress) > 0 {
// 		abha.HealthID = profile.PhrAddress[0]
// 		log.Printf("[SendEnrolRequestWithOTP] Health ID extracted from PhrAddress: %s", abha.HealthID)
// 	} else {
// 		log.Printf("[SendEnrolRequestWithOTP] WARNING: No PhrAddress found in profile")
// 	}

// 	log.Printf("[SendEnrolRequestWithOTP] Attempting to save ABHA profile to database...")
// 	// Save to DB
// 	if err := s.abhaRepo.SaveAbhaProfile(ctx, abha); err != nil {
// 		log.Printf("[SendEnrolRequestWithOTP] ERROR: Failed to save ABHA profile to database: %v", err)
// 		return nil, errorsx.Internal("ABHA_SAVE_FAILED", "failed to save ABHA profile", nil, err)
// 	}

// 	log.Printf("[SendEnrolRequestWithOTP] SUCCESS: ABHA profile saved successfully with ID: %s", abha.ExternalID)
// 	return respBody, nil
// }

// func (s *abhaService) FetchAbhaCard(ctx context.Context, abhaNumber string) ([]byte, error) {
// 	log.Printf("[FetchAbhaCard] Starting card fetch for ABHA number: %s", abhaNumber)

// 	// Get access token from DB
// 	abha, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
// 	if err != nil {
// 		log.Printf("[FetchAbhaCard] ERROR: Failed to get ABHA profile from DB: %v", err)
// 		return nil, errorsx.NotFound("ABHA_PROFILE_NOT_FOUND", "failed to get ABHA profile", nil, err)
// 	}
// 	if abha.AccessToken == "" {
// 		log.Printf("[FetchAbhaCard] ERROR: Access token not found for ABHA number: %s", abhaNumber)
// 		return nil, errorsx.BadRequest("ACCESS_TOKEN_MISSING", "access token not found for ABHA number", nil, nil)
// 	}

// 	log.Printf("[FetchAbhaCard] ABHA profile found with access token length: %d chars", len(abha.AccessToken))

// 	// Get auth token
// 	log.Printf("[FetchAbhaCard] Fetching ABDM auth token...")
// 	authToken, err := utils.FetchABDMToken(ctx, s.cfg)
// 	if err != nil {
// 		log.Printf("[FetchAbhaCard] ERROR: Failed to fetch ABDM auth token: %v", err)
// 		return nil, errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM auth token", nil, err)
// 	}

// 	log.Printf("[FetchAbhaCard] ABDM auth token fetched successfully, length: %d chars", len(authToken))

// 	// Make HTTP request to getCard
// 	cardURL := "https://healthidsbx.abdm.gov.in/api/v1/account/getCard"
// 	log.Printf("[FetchAbhaCard] Creating HTTP POST request to: %s", cardURL)

// 	req, err := http.NewRequestWithContext(ctx, http.MethodPost, cardURL, nil)
// 	if err != nil {
// 		log.Printf("[FetchAbhaCard] ERROR: Failed to create HTTP request: %v", err)
// 		return nil, errorsx.Internal("GETCARD_REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
// 	}
// 	req.Header.Set("accept", "*/*")
// 	req.Header.Set("Accept-Language", "en-US")
// 	req.Header.Set("X-Token", "Bearer "+abha.AccessToken)
// 	req.Header.Set("Authorization", "Bearer "+authToken)

// 	log.Printf("[FetchAbhaCard] Request headers set, sending request...")

// 	client := &http.Client{Timeout: 10 * time.Second}
// 	resp, err := client.Do(req)
// 	if err != nil {
// 		log.Printf("[FetchAbhaCard] ERROR: HTTP request failed: %v", err)
// 		return nil, errorsx.Internal("GETCARD_HTTP_FAILED", "failed to call getCard", nil, err)
// 	}
// 	defer resp.Body.Close()

// 	log.Printf("[FetchAbhaCard] Response received with status code: %d", resp.StatusCode)

// 	if resp.StatusCode != http.StatusOK {
// 		bodyBytes, _ := io.ReadAll(resp.Body)
// 		log.Printf("[FetchAbhaCard] ERROR: getCard failed with status %d, response: %s", resp.StatusCode, string(bodyBytes))
// 		return nil, errorsx.FromUpstream(resp.StatusCode, bodyBytes, "GETCARD")
// 	}

// 	responseBody, err := io.ReadAll(resp.Body)
// 	if err != nil {
// 		log.Printf("[FetchAbhaCard] ERROR: Failed to read response body: %v", err)
// 		return nil, errorsx.Internal("GETCARD_READ_FAILED", "failed to read getCard response", nil, err)
// 	}

// 	log.Printf("[FetchAbhaCard] SUCCESS: Card data fetched, response length: %d bytes", len(responseBody))
// 	return responseBody, nil
// }

// func (s *abhaService) FetchAbhaCardByType(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
// 	log.Printf("[FetchAbhaCardByType] Starting card fetch for ABHA: %s, card type: %s", abhaNumber, cardType)

// 	abhaData, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
// 	if err != nil || abhaData.AccessToken == "" {
// 		log.Printf("[FetchAbhaCardByType] ERROR: ABHA not found or access token missing for number: %s", abhaNumber)
// 		return nil, "", errorsx.NotFound("ABHA_NOT_FOUND_OR_TOKEN_MISSING", "abha not found or access token missing", nil, err)
// 	}
// 	log.Printf("[FetchAbhaCardByType] ABHA profile found: %s, access token length: %d chars",
// 		abhaData.ABHANumber, len(abhaData.AccessToken))

// 	log.Printf("[FetchAbhaCardByType] Fetching ABDM auth token...")
// 	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
// 	if err != nil {
// 		log.Printf("[FetchAbhaCardByType] ERROR: Failed to fetch ABDM token: %v", err)
// 		return nil, "", errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM token", nil, err)
// 	}
// 	log.Printf("[FetchAbhaCardByType] ABDM auth token fetched successfully, length: %d chars", len(abdmToken))

// 	var endpoint string
// 	switch cardType {
// 	case "getCard":
// 		endpoint = s.cfg.ABHACardEndpoints.GetCard
// 	case "getSvgCard":
// 		endpoint = s.cfg.ABHACardEndpoints.GetSvgCard
// 	case "getPngCard":
// 		endpoint = s.cfg.ABHACardEndpoints.GetPngCard
// 	default:
// 		log.Printf("[FetchAbhaCardByType] ERROR: Unsupported card type: %s", cardType)
// 		return nil, "", errorsx.BadRequest("INVALID_CARD_TYPE", fmt.Sprintf("unsupported card type: %s", cardType), nil, nil)
// 	}
// 	log.Printf("[FetchAbhaCardByType] Card type '%s' mapped to endpoint: %s", cardType, endpoint)

// 	fetchImage := func(token string) ([]byte, string, error) {
// 		log.Printf("[FetchAbhaCardByType:fetchImage] Creating HTTP GET request to endpoint with token length: %d chars", len(token))

// 		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
// 		if err != nil {
// 			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: Failed to create HTTP request: %v", err)
// 			return nil, "", errorsx.Internal("GETCARD_REQ_BUILD_FAILED", "failed to build card request", nil, err)
// 		}
// 		req.Header.Set("Accept", "*/*")
// 		req.Header.Set("Accept-Language", "en-US")
// 		req.Header.Set("X-Token", "Bearer "+token)
// 		req.Header.Set("Authorization", "Bearer "+abdmToken)

// 		log.Printf("[FetchAbhaCardByType:fetchImage] Sending HTTP GET request...")
// 		resp, err := s.httpClient.Do(req)
// 		if err != nil {
// 			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: HTTP request failed: %v", err)
// 			return nil, "", errorsx.Internal("GETCARD_HTTP_FAILED", "failed to call card endpoint", nil, err)
// 		}
// 		defer resp.Body.Close()

// 		log.Printf("[FetchAbhaCardByType:fetchImage] Response received with status: %d", resp.StatusCode)

// 		data, err := io.ReadAll(resp.Body)
// 		if err != nil {
// 			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: Failed to read response body: %v", err)
// 			return nil, "", errorsx.Internal("GETCARD_READ_FAILED", "failed to read response", nil, err)
// 		}

// 		log.Printf("[FetchAbhaCardByType:fetchImage] Response body read, length: %d bytes", len(data))

// 		if resp.StatusCode != http.StatusOK {
// 			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: Request failed with status %d: %s", resp.StatusCode, string(data))
// 			return nil, "", errorsx.FromUpstream(resp.StatusCode, data, "ABHA_CARD_FETCH")
// 		}

// 		// Auto-detect content type
// 		detectedType := http.DetectContentType(data)
// 		log.Printf("[FetchAbhaCardByType:fetchImage] Detected content type: %s", detectedType)

// 		// SVG case: attempt to extract embedded image
// 		if detectedType == "text/xml" || detectedType == "image/svg+xml" {
// 			log.Printf("[FetchAbhaCardByType:fetchImage] Attempting to extract base64 image from SVG...")
// 			decoded, actualType, decodeErr := utils.ExtractImageFromSVG(data)
// 			if decodeErr != nil {
// 				log.Printf("[FetchAbhaCardByType:fetchImage] SVG base64 extraction failed: %v", decodeErr)
// 			} else {
// 				log.Printf("[FetchAbhaCardByType:fetchImage] Successfully extracted base64 image from SVG, type: %s", actualType)
// 				return decoded, actualType, nil
// 			}
// 		}

// 		log.Printf("[FetchAbhaCardByType:fetchImage] Returning raw data with detected type: %s", detectedType)
// 		return data, detectedType, nil
// 	}

// 	// Try with access token
// 	log.Printf("[FetchAbhaCardByType] Attempting to fetch card using access token...")
// 	imageData, detectedType, err := fetchImage(abhaData.AccessToken)
// 	if err == nil {
// 		log.Printf("[FetchAbhaCardByType] SUCCESS: Card fetched successfully using access token")
// 		return imageData, detectedType, nil
// 	}

// 	log.Printf("[FetchAbhaCardByType] Access token failed: %v", err)

// 	// Retry with refresh token
// 	if abhaData.RefreshToken != "" {
// 		log.Printf("[FetchAbhaCardByType] Retrying with refresh token...")
// 		imageData, detectedType, err2 := fetchImage(abhaData.RefreshToken)
// 		if err2 == nil {
// 			log.Printf("[FetchAbhaCardByType] SUCCESS: Card fetched successfully using refresh token")
// 			return imageData, detectedType, nil
// 		}
// 		log.Printf("[FetchAbhaCardByType] ERROR: Refresh token also failed: %v", err2)
// 		return nil, "", errorsx.UpstreamFailed("ABHA_CARD_FETCH_FAILED", "failed with both access and refresh tokens", http.StatusBadGateway, nil, err2)
// 	}

// 	log.Printf("[FetchAbhaCardByType] ERROR: No refresh token available for retry")
// 	return nil, "", errorsx.Internal("ABHA_CARD_FETCH_FAILED", "failed with access token and no refresh token available", nil, err)
// }

// // helper to safely slice long token logs
// func min(a, b int) int {
// 	if a < b {
// 		return a
// 	}
// 	return b
// }

// func (s *abhaService) FetchQRCodeByABHANumber(ctx context.Context, abhaNumber string) ([]byte, error) {
// 	log.Printf("[FetchQRCodeByABHANumber] Starting QR code fetch for ABHA number: %s", abhaNumber)

// 	abhaData, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
// 	if err != nil || abhaData.AccessToken == "" {
// 		log.Printf("[FetchQRCodeByABHANumber] ERROR: ABHA not found or access token missing for number: %s", abhaNumber)
// 		return nil, errorsx.NotFound("ABHA_NOT_FOUND_OR_TOKEN_MISSING", "abha not found or access token missing", nil, err)
// 	}
// 	log.Printf("[FetchQRCodeByABHANumber] ABHA profile found: %s, access token length: %d chars",
// 		abhaData.ABHANumber, len(abhaData.AccessToken))

// 	log.Printf("[FetchQRCodeByABHANumber] Fetching ABDM auth token...")
// 	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
// 	if err != nil {
// 		log.Printf("[FetchQRCodeByABHANumber] ERROR: Failed to fetch ABDM token: %v", err)
// 		return nil, errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM token", nil, err)
// 	}
// 	log.Printf("[FetchQRCodeByABHANumber] ABDM auth token fetched successfully, length: %d chars", len(abdmToken))

// 	endpoint := s.cfg.QRCode
// 	log.Printf("[FetchQRCodeByABHANumber] QR endpoint configured: %s", endpoint)

// 	// Reusable fetch function
// 	fetchQR := func(token string) ([]byte, error) {
// 		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Creating HTTP GET request with token length: %d chars", len(token))

// 		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
// 		if err != nil {
// 			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: Failed to create HTTP request: %v", err)
// 			return nil, errorsx.Internal("QR_REQ_BUILD_FAILED", "failed to build QR request", nil, err)
// 		}

// 		req.Header.Set("Accept", "*/*")
// 		req.Header.Set("Accept-Language", "en-US")
// 		req.Header.Set("X-Token", "Bearer "+token)
// 		req.Header.Set("Authorization", "Bearer "+abdmToken)

// 		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Sending HTTP GET request...")
// 		resp, err := s.httpClient.Do(req)
// 		if err != nil {
// 			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: HTTP request failed: %v", err)
// 			return nil, errorsx.Internal("QR_HTTP_FAILED", "failed to call QR endpoint", nil, err)
// 		}
// 		defer resp.Body.Close()

// 		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Response received with status: %d", resp.StatusCode)

// 		body, err := io.ReadAll(resp.Body)
// 		if err != nil {
// 			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: Failed to read response body: %v", err)
// 			return nil, errorsx.Internal("QR_READ_FAILED", "failed to read QR response", nil, err)
// 		}

// 		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Response body read, length: %d bytes", len(body))

// 		if resp.StatusCode != http.StatusOK {
// 			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: QR fetch failed with status %d: %s",
// 				resp.StatusCode, string(body))
// 			return nil, errorsx.FromUpstream(resp.StatusCode, body, "QR_FETCH")
// 		}

// 		log.Printf("[FetchQRCodeByABHANumber:fetchQR] QR data fetched successfully")
// 		return body, nil
// 	}

// 	log.Printf("[FetchQRCodeByABHANumber] Attempting to fetch QR code using access token...")
// 	imageData, err := fetchQR(abhaData.AccessToken)
// 	if err == nil {
// 		log.Printf("[FetchQRCodeByABHANumber] SUCCESS: QR code fetched successfully using access token")
// 		return imageData, nil
// 	}

// 	log.Printf("[FetchQRCodeByABHANumber] Access token failed, retrying with refresh token: %v", err)

// 	if abhaData.RefreshToken != "" {
// 		log.Printf("[FetchQRCodeByABHANumber] Attempting to fetch QR code using refresh token...")
// 		imageData, err2 := fetchQR(abhaData.RefreshToken)
// 		if err2 == nil {
// 			log.Printf("[FetchQRCodeByABHANumber] SUCCESS: QR code fetched successfully using refresh token")
// 			return imageData, nil
// 		}
// 		log.Printf("[FetchQRCodeByABHANumber] ERROR: Refresh token also failed: %v", err2)
// 		return nil, errorsx.UpstreamFailed("QR_FETCH_FAILED", "both access and refresh token failed", http.StatusBadGateway, nil, err2)
// 	}

// 	log.Printf("[FetchQRCodeByABHANumber] ERROR: No refresh token available for retry")
// 	return nil, errorsx.Internal("QR_FETCH_FAILED", "access token failed and no refresh token available", nil, err)
// }

// func (s *abhaService) sendRequest(ctx context.Context, url string, payload interface{}, token string) ([]byte, error) {
// 	log.Printf("[sendRequest] Starting HTTP POST request to URL: %s", url)

// 	body, err := json.Marshal(payload)
// 	if err != nil {
// 		log.Printf("[sendRequest] ERROR: Failed to marshal payload: %v", err)
// 		return nil, errorsx.Internal("REQ_MARSHAL_FAILED", "failed to marshal request payload", nil, err)
// 	}

// 	log.Printf("[sendRequest] Payload marshalled successfully, body length: %d bytes", len(body))

// 	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(body))
// 	if err != nil {
// 		log.Printf("[sendRequest] ERROR: Failed to create HTTP request: %v", err)
// 		return nil, errorsx.Internal("REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
// 	}

// 	requestID := uuid.New().String()
// 	timestamp := time.Now().UTC().Format("2006-01-02T15:04:05.000Z")

// 	req.Header.Set("Authorization", "Bearer "+token)
// 	req.Header.Set("Content-Type", "application/json")
// 	req.Header.Set("REQUEST-ID", requestID)
// 	req.Header.Set("TIMESTAMP", timestamp)

// 	log.Printf("[sendRequest] Headers set - REQUEST-ID: %s, TIMESTAMP: %s, Token length: %d chars",
// 		requestID, timestamp, len(token))

// 	log.Printf("[sendRequest] Sending HTTP POST request...")
// 	resp, err := s.httpClient.Do(req)
// 	if err != nil {
// 		log.Printf("[sendRequest] ERROR: HTTP request failed: %v", err)
// 		return nil, errorsx.Internal("HTTP_DO_FAILED", "failed to call upstream API", nil, err)
// 	}
// 	defer resp.Body.Close()

// 	log.Printf("[sendRequest] Response received with status code: %d", resp.StatusCode)

// 	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
// 		respBody, _ := io.ReadAll(resp.Body)
// 		log.Printf("[sendRequest] ERROR: API returned non-success status %d, response: %s",
// 			resp.StatusCode, string(respBody))
// 		return nil, errorsx.FromUpstream(resp.StatusCode, respBody, "API")
// 	}

// 	responseBody, err := io.ReadAll(resp.Body)
// 	if err != nil {
// 		log.Printf("[sendRequest] ERROR: Failed to read response body: %v", err)
// 		return nil, errorsx.Internal("RESP_READ_FAILED", "failed to read response body", nil, err)
// 	}

// 	log.Printf("[sendRequest] SUCCESS: Request completed, response body length: %d bytes", len(responseBody))
// 	return responseBody, nil
// }

// // ---------------------- CREATE FLOW ----------------------

// // create/link_mobile_number
// func (s *abhaService) LinkMobileNumber(ctx context.Context, req dtos.LinkMobileRequest, token string) ([]byte, error) {
// 	log.Printf("[LinkMobileNumber] Starting mobile link for mobile: %s, txnId: %s", req.Mobile, req.TransactionID)

// 	payload := map[string]interface{}{
// 		"scope":     []string{"abha-enrol", "mobile-verify"},
// 		"loginHint": "mobile",
// 		"loginId":   req.Mobile,
// 		"txnId":     req.TransactionID,
// 		"otpSystem": "abdm",
// 	}

// 	log.Printf("[LinkMobileNumber] Payload created: %+v", payload)
// 	log.Printf("[LinkMobileNumber] Sending request to URL: %s", s.cfg.LinkMobileURL)

// 	response, err := s.sendRequest(ctx, s.cfg.LinkMobileURL, payload, token)
// 	if err != nil {
// 		log.Printf("[LinkMobileNumber] ERROR: Mobile link request failed: %v", err)
// 		return nil, err
// 	}

// 	log.Printf("[LinkMobileNumber] SUCCESS: Mobile link completed, response length: %d bytes", len(response))
// 	return response, nil
// }

// // create/verify_mobile_otp
// func (s *abhaService) VerifyMobileOTP(ctx context.Context, req dtos.VerifyMobileOTPRequest, token string) ([]byte, error) {
// 	log.Printf("[VerifyMobileOTP] Starting mobile OTP verification for txnId: %s", req.TransactionID)

// 	// Build payload exactly as required by ABDM (authData.otp.{timeStamp, txnId, otpValue})
// 	timestamp := time.Now().Format("2006-01-02 15:04:05")
// 	payload := map[string]interface{}{
// 		"scope": []string{"abha-enrol", "mobile-verify"},
// 		"authData": map[string]interface{}{
// 			"authMethods": []string{"otp"},
// 			"otp": map[string]interface{}{
// 				"timeStamp": timestamp,
// 				"txnId":     req.TransactionID,
// 				"otpValue":  req.Otp,
// 			},
// 		},
// 	}

// 	log.Printf("[VerifyMobileOTP] Payload created with timestamp: %s, OTP length: %d", timestamp, len(req.Otp))
// 	log.Printf("[VerifyMobileOTP] Sending request to URL: %s", s.cfg.VerifyMobileURL)

// 	response, err := s.sendRequest(ctx, s.cfg.VerifyMobileURL, payload, token)
// 	if err != nil {
// 		log.Printf("[VerifyMobileOTP] ERROR: Mobile OTP verification failed: %v", err)
// 		return nil, err
// 	}

// 	log.Printf("[VerifyMobileOTP] SUCCESS: Mobile OTP verification completed, response length: %d bytes", len(response))
// 	return response, nil
// }

// // create/address_suggestion
// func (s *abhaService) AddressSuggestion(ctx context.Context, req dtos.AddressSuggestionRequest, token string) ([]byte, error) {
// 	url := s.cfg.AddressSuggestionURL

// 	log.Printf("[AddressSuggestion] Hitting URL: %s", url)
// 	log.Printf("[AddressSuggestion] txnId=%s token=%s", req.TransactionID, token)

// 	httpReq, err := http.NewRequestWithContext(ctx, "GET", url, nil)
// 	if err != nil {
// 		log.Printf("[AddressSuggestion] ERROR building request: %v", err)
// 		return nil, errorsx.Internal("ADDR_SUGGEST_REQ_BUILD_FAILED", "failed to build request", nil, err)
// 	}
// 	httpReq.Header.Set("TRANSACTION_ID", req.TransactionID)
// 	httpReq.Header.Set("Authorization", "Bearer "+token)
// 	httpReq.Header.Set("REQUEST-ID", uuid.New().String())

// 	const abdmTimestampLayout = "2006-01-02T15:04:05"
// 	timestamp := time.Now().Format(abdmTimestampLayout)
// 	httpReq.Header.Set("TIMESTAMP", timestamp)

// 	log.Printf("[AddressSuggestion] Headers => TRANSACTION_ID=%s TIMESTAMP=%s", req.TransactionID, timestamp)

// 	resp, err := s.httpClient.Do(httpReq)
// 	if err != nil {
// 		log.Printf("[AddressSuggestion] ERROR making request: %v", err)
// 		return nil, errorsx.Internal("ADDR_SUGGEST_HTTP_FAILED", "address suggestion call failed", nil, err)
// 	}
// 	defer resp.Body.Close()

// 	body, err := io.ReadAll(resp.Body)
// 	if err != nil {
// 		log.Printf("[AddressSuggestion] ERROR reading body: %v", err)
// 		return nil, errorsx.Internal("ADDR_SUGGEST_READ_FAILED", "failed to read address suggestion response", nil, err)
// 	}

// 	log.Printf("[AddressSuggestion] Status=%d Response=%s", resp.StatusCode, string(body))
// 	return body, nil
// }

// // create/enrol_abha_address
// func (s *abhaService) EnrolAddress(ctx context.Context, req dtos.EnrolAddressRequest, token string) ([]byte, error) {
// 	payload := map[string]interface{}{
// 		"transaction_id": req.TransactionID,
// 		"abha_address":   req.AbhaAddress,
// 		"preferred":      1,
// 	}
// 	respBytes, err := s.sendRequest(ctx, s.cfg.EnrolAddressURL, payload, token)
// 	if err != nil {
// 		return nil, err
// 	}

// 	// update abha_number table if possible
// 	var resp struct {
// 		TxnId          string `json:"txnId"`
// 		HealthIdNumber string `json:"healthIdNumber"`
// 		PreferredAbha  string `json:"preferredAbhaAddress"`
// 	}
// 	if json.Unmarshal(respBytes, &resp) == nil {
// 		abha := &domain.AbhaNumber{
// 			ABHANumber: resp.HealthIdNumber,
// 			HealthID:   resp.PreferredAbha,
// 		}
// 		_ = s.abhaRepo.SaveAbhaProfile(ctx, abha)
// 	}
// 	return respBytes, nil
// }

// // ---------------------- LOGIN FLOW ----------------------

// func (s *abhaService) LoginSendOTP(ctx context.Context, req dtos.SendOtpRequest, token string) ([]byte, error) {
// 	log.Printf("[LoginSendOTP] Starting login OTP request for type: %s, value: %s, otp_system: %s",
// 		req.Type, req.Value, req.OtpSystem)

// 	scope := []string{}
// 	if req.OtpSystem == "aadhaar" {
// 		scope = append(scope, "aadhaar-verify")
// 	} else {
// 		scope = append(scope, "mobile-verify")
// 	}
// 	if req.Type == "abha-address" {
// 		scope = append([]string{"abha-address-login"}, scope...)
// 	} else {
// 		scope = append([]string{"abha-login"}, scope...)
// 	}

// 	payload := map[string]interface{}{
// 		"scope":      scope,
// 		"type":       req.Type,
// 		"value":      req.Value,
// 		"otp_system": req.OtpSystem,
// 	}

// 	log.Printf("[LoginSendOTP] Payload created with scope: %v", scope)
// 	log.Printf("[LoginSendOTP] Sending request to URL: %s", s.cfg.LoginSendOtpURL)

// 	response, err := s.sendRequest(ctx, s.cfg.LoginSendOtpURL, payload, token)
// 	if err != nil {
// 		log.Printf("[LoginSendOTP] ERROR: Login OTP send request failed: %v", err)
// 		return nil, err
// 	}

// 	log.Printf("[LoginSendOTP] SUCCESS: Login OTP sent, response length: %d bytes", len(response))
// 	return response, nil
// }

// func (s *abhaService) LoginVerifyOTP(ctx context.Context, req dtos.VerifyOtpRequest, token string) ([]byte, error) {
// 	log.Printf("[LoginVerifyOTP] Starting login OTP verification for txnId: %s, type: %s, otp_system: %s",
// 		req.TransactionID, req.Type, req.OtpSystem)

// 	scope := []string{}
// 	if req.OtpSystem == "aadhaar" {
// 		scope = append(scope, "aadhaar-verify")
// 	} else {
// 		scope = append(scope, "mobile-verify")
// 	}
// 	if req.Type == "abha-address" {
// 		scope = append([]string{"abha-address-login"}, scope...)
// 	} else {
// 		scope = append([]string{"abha-login"}, scope...)
// 	}

// 	payload := map[string]interface{}{
// 		"scope":          scope,
// 		"transaction_id": req.TransactionID,
// 		"otp":            req.Otp,
// 	}

// 	log.Printf("[LoginVerifyOTP] Payload created with scope: %v, OTP length: %d", scope, len(req.Otp))
// 	log.Printf("[LoginVerifyOTP] Sending request to URL: %s", s.cfg.LoginVerifyOtpURL)

// 	respBytes, err := s.sendRequest(ctx, s.cfg.LoginVerifyOtpURL, payload, token)
// 	if err != nil {
// 		log.Printf("[LoginVerifyOTP] ERROR: Login OTP verification failed: %v", err)
// 		return nil, err
// 	}

// 	log.Printf("[LoginVerifyOTP] Response received, length: %d bytes", len(respBytes))

// 	// update tokens in DB
// 	var out struct {
// 		TxnId         string `json:"txnId"`
// 		Token         string `json:"token"`
// 		RefreshToken  string `json:"refreshToken"`
// 		ABHANumber    string `json:"ABHANumber"`
// 		PreferredAbha string `json:"preferredAbhaAddress"`
// 	}
// 	if json.Unmarshal(respBytes, &out) == nil {
// 		log.Printf("[LoginVerifyOTP] Response parsed successfully, ABHA: %s, PreferredAbha: %s",
// 			out.ABHANumber, out.PreferredAbha)

// 		abha := &domain.AbhaNumber{
// 			ABHANumber:   out.ABHANumber,
// 			HealthID:     out.PreferredAbha,
// 			AccessToken:  out.Token,
// 			RefreshToken: out.RefreshToken,
// 		}

// 		log.Printf("[LoginVerifyOTP] Attempting to save ABHA profile to database...")
// 		if saveErr := s.abhaRepo.SaveAbhaProfile(ctx, abha); saveErr != nil {
// 			log.Printf("[LoginVerifyOTP] WARNING: Failed to save ABHA profile: %v", saveErr)
// 		} else {
// 			log.Printf("[LoginVerifyOTP] ABHA profile saved successfully")
// 		}
// 	} else {
// 		log.Printf("[LoginVerifyOTP] WARNING: Failed to parse response for token extraction")
// 	}

// 	log.Printf("[LoginVerifyOTP] SUCCESS: Login OTP verification completed")
// 	return respBytes, nil
// }

// func (s *abhaService) LoginCheckAuthMethods(ctx context.Context, req dtos.CheckAuthMethodsRequest, token string) ([]byte, error) {
// 	log.Printf("[LoginCheckAuthMethods] Starting auth methods check for ABHA address: %s", req.AbhaAddress)

// 	payload := map[string]interface{}{
// 		"abha_address": req.AbhaAddress,
// 	}

// 	log.Printf("[LoginCheckAuthMethods] Payload created: %+v", payload)
// 	log.Printf("[LoginCheckAuthMethods] Sending request to URL: %s", s.cfg.LoginCheckAuthURL)

// 	response, err := s.sendRequest(ctx, s.cfg.LoginCheckAuthURL, payload, token)
// 	if err != nil {
// 		log.Printf("[LoginCheckAuthMethods] ERROR: Auth methods check failed: %v", err)
// 		return nil, err
// 	}

// 	log.Printf("[LoginCheckAuthMethods] SUCCESS: Auth methods check completed, response length: %d bytes", len(response))
// 	return response, nil
// }

// func (s *abhaService) logLoginTxn(ctx context.Context, txn *domain.TransactionLog) {
// 	go func() {
// 		if err := s.abhaRepo.InsertLoginTransaction(ctx, txn); err != nil {
// 			log.Printf("[LoginTxnLog] Failed to insert: %v", err)
// 		}
// 	}()
// }

// /////////////////// -------------------------- profile login logic

// // internal/core/services/abha_service.go  (additions)

// func (s *abhaService) ProfileLoginRequestOTP(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error) {
// 	return s.sendRequestWithABDMHeaders(ctx, s.cfg.ProfileLoginRequestOTPURL, req, token)
// }

// func (s *abhaService) ProfileLoginVerifyOTP(ctx context.Context, req dtos.ProfileLoginVerifyOTP, token string) ([]byte, error) {
// 	respBytes, err := s.sendRequestWithABDMHeaders(ctx, s.cfg.ProfileLoginVerifyOTPURL, req, token)
// 	if err != nil {
// 		return nil, err
// 	}

// 	// Try to persist tokens if present
// 	var out dtos.ProfileLoginVerifyResponse
// 	if json.Unmarshal(respBytes, &out) == nil && (out.Token != "" || out.RefreshToken != "") {
// 		abha := &domain.AbhaNumber{
// 			ABHANumber:       out.ABHANumber,
// 			HealthID:         out.PreferredAbhaAddress,
// 			AccessToken:      out.Token,
// 			RefreshToken:     out.RefreshToken,
// 			LastModifiedDate: time.Now().UTC(),
// 		}

// 		mask := func(s string) string {
// 			if len(s) <= 6 {
// 				return "****"
// 			}
// 			return s[:3] + "****" + s[len(s)-3:]
// 		}

// 		log.Printf("ABHA created: number=%s healthID=%s accessToken=%s refreshToken=%s lastModified=%s",
// 			abha.ABHANumber,
// 			abha.HealthID,
// 			mask(abha.AccessToken),
// 			mask(abha.RefreshToken),
// 			abha.LastModifiedDate.Format(time.RFC3339),
// 		)
// 		// _ = s.abhaRepo.SaveAbhaProfile(ctx, abha)
// 	}
// 	return respBytes, nil
// }

// // Same as sendRequest, but TIMESTAMP must be RFC3339 with millis + 'Z' (what your cURLs show)
// func (s *abhaService) sendRequestWithABDMHeaders(ctx context.Context, url string, payload interface{}, token string) ([]byte, error) {
// 	body, err := json.Marshal(payload)
// 	if err != nil {
// 		return nil, errorsx.Internal("REQ_MARSHAL_FAILED", "failed to marshal request payload", nil, err)
// 	}

// 	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBuffer(body))
// 	if err != nil {
// 		return nil, errorsx.Internal("REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
// 	}

// 	// NOTE: token is *single* Bearer (your curl shows "Bearer Bearer ...", which is a copy/paste artifact)
// 	req.Header.Set("Authorization", "Bearer "+token)
// 	req.Header.Set("Content-Type", "application/json")
// 	req.Header.Set("REQUEST-ID", uuid.New().String())
// 	req.Header.Set("TIMESTAMP", time.Now().UTC().Format(time.RFC3339Nano)) // e.g. 2025-09-01T06:57:40.811Z

// 	resp, err := s.httpClient.Do(req)
// 	if err != nil {
// 		return nil, errorsx.Internal("HTTP_DO_FAILED", "failed to call upstream API", nil, err)
// 	}
// 	defer resp.Body.Close()

// 	b, _ := io.ReadAll(resp.Body)
// 	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
// 		return nil, errorsx.FromUpstream(resp.StatusCode, b, "API")
// 	}
// 	return b, nil
// }

// // Verify Aadhaar OTP with ABDM, create Individual in HCM, then persist ABHA profile.
// // - Encrypts OTP per ABDM requirements
// // - Maps ABHAProfile (handles ABHANumber vs healthIdNumber)
// // - Creates Individual in HCM (omitting empty/placeholder fields, allowing short names)
// // - Persists ABHA profile with a valid ExternalID (HCM Individual.Id if valid UUID, else a new UUID)
// func (s *abhaService) VerifyAadhaarOtpAndCreateIndividualV2(ctx context.Context, in dtos.VerifyAndCreateV2Input) ([]byte, error) {
// 	log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] Start | txnId=%s tenant=%s clientRef=%s", in.TxnId, in.TenantId, in.ClientReferenceId)

// 	// ---- 1) Validate inputs
// 	if ok := regexp.MustCompile(`^\d{6}$`).MatchString(in.Otp); !ok {
// 		return nil, errorsx.BadRequest("INVALID_OTP_FORMAT", "otp must be 6 digits", nil, nil)
// 	}
// 	if in.Mobile != "" && !regexp.MustCompile(`^\d{10}$`).MatchString(in.Mobile) {
// 		return nil, errorsx.BadRequest("INVALID_MOBILE_FORMAT", "mobile must be 10 digits", nil, nil)
// 	}
// 	if strings.TrimSpace(in.TxnId) == "" {
// 		return nil, errorsx.BadRequest("MISSING_TXN_ID", "transaction id is required", nil, nil)
// 	}
// 	if strings.TrimSpace(in.TenantId) == "" || strings.TrimSpace(in.ClientReferenceId) == "" ||
// 		strings.TrimSpace(in.HcmAuthToken) == "" || strings.TrimSpace(in.UserUUID) == "" || in.UserID == 0 {
// 		return nil, errorsx.BadRequest("MISSING_HCM_INPUTS", "required HCM inputs missing", nil, nil)
// 	}

// 	// ---- 2) Encrypt OTP (ABDM requires RSA-OAEP SHA-1)
// 	pub, err := s.FetchPublicKey(ctx)
// 	if err != nil {
// 		return nil, errorsx.Internal("PUBLIC_KEY_FETCH_FAILED", "failed to fetch public key", nil, err)
// 	}
// 	encOTP, err := s.EncryptData(ctx, pub, in.Otp)
// 	if err != nil {
// 		return nil, errorsx.Internal("OTP_ENCRYPT_FAILED", "failed to encrypt otp", nil, err)
// 	}

// 	// ---- 3) Build ABDM verify payload
// 	var verifyReq dtos.EnrolByAadhaarWithOTPRequestV2
// 	verifyReq.Consent.Code = "abha-enrollment"
// 	verifyReq.Consent.Version = "1.4"
// 	verifyReq.AuthData.AuthMethods = []string{"otp"}
// 	verifyReq.AuthData.OTP.TimeStamp = time.Now().Format("2006-01-02 15:04:05") // ABDM expects this format
// 	verifyReq.AuthData.OTP.TxnId = in.TxnId
// 	verifyReq.AuthData.OTP.OtpValue = encOTP
// 	if in.Mobile != "" {
// 		verifyReq.AuthData.OTP.Mobile = in.Mobile // sandbox currently needs plaintext mobile sometimes
// 	}

// 	// ---- 4) ABDM verify call
// 	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
// 	if err != nil {
// 		return nil, errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM token", nil, err)
// 	}
// 	verifyRespBytes, err := s.sendRequest(ctx, s.cfg.EnrolByAadhaarURL, verifyReq, abdmToken)
// 	if err != nil {
// 		if strings.Contains(err.Error(), "INVALID_OTP") {
// 			return nil, errorsx.BadRequest("ABDM_INVALID_OTP", "invalid otp", nil, err)
// 		}
// 		return nil, errorsx.UpstreamFailed("ABDM_VERIFY_ERROR", "ABDM verify call failed", http.StatusBadGateway, nil, err)
// 	}

// 	// ---- 5) Parse ABDM response
// 	var verifyOut dtos.EnrolWithOTPResponseV2
// 	if uErr := json.Unmarshal(verifyRespBytes, &verifyOut); uErr != nil {
// 		return nil, errorsx.Internal("ABDM_VERIFY_PARSE_ERROR", "failed to parse ABDM verify response", nil, uErr)
// 	}

// 	abhaNumber := strings.TrimSpace(verifyOut.ABHAProfile.ABHANumber)
// 	if abhaNumber == "" {
// 		abhaNumber = strings.TrimSpace(verifyOut.ABHAProfile.HealthIdNumber)
// 	}
// 	if abhaNumber == "" {
// 		return nil, errorsx.UpstreamFailed("ABDM_VERIFY_ERROR", "missing ABHA number in response", http.StatusBadGateway, nil, nil)
// 	}

// 	// --- fetch Aadhaar linked to this txn and decrypt it for identifiers
// 	aadhaarPlain := ""
// 	if enc, err := s.abhaRepo.GetEncryptedAadhaarByTxn(ctx, in.TxnId); err == nil && strings.TrimSpace(enc) != "" {
// 		if dec, derr := utils.DecryptFromDB(enc); derr == nil && regexp.MustCompile(`^\d{12}$`).MatchString(dec) {
// 			aadhaarPlain = dec
// 		} else if derr != nil {
// 			log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: Aadhaar decrypt failed for txn=%s: %v", in.TxnId, derr)
// 		} else {
// 			log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: Aadhaar format invalid after decrypt for txn=%s", in.TxnId)
// 		}
// 	} else if err != nil {
// 		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: GetEncryptedAadhaarByTxn(%s) failed: %v", in.TxnId, err)
// 	}

// 	// Convenience getters & normalizers
// 	trim := func(s string) string { return strings.TrimSpace(s) }
// 	is10 := func(s string) bool { return regexp.MustCompile(`^\d{10}$`).MatchString(s) }
// 	addIfNonEmpty := func(m map[string]any, k, v string) {
// 		if strings.TrimSpace(v) != "" {
// 			m[k] = v
// 		}
// 	}
// 	// formatDob: ABHA "dd-MM-yyyy" -> HCM "dd/MM/yyyy"
// 	safeDob := trim(formatDob(verifyOut.ABHAProfile.Dob))
// 	safeGender := trim(normalizeGender(verifyOut.ABHAProfile.Gender))

// 	// Names (no placeholders)
// 	firstName := trim(verifyOut.ABHAProfile.FirstName)
// 	middleName := trim(verifyOut.ABHAProfile.MiddleName)
// 	lastName := trim(verifyOut.ABHAProfile.LastName)
// 	if firstName == "" {
// 		// givenName is required by your POJO contract; fail fast instead of injecting placeholders
// 		return nil, errorsx.UpstreamFailed("ABDM_VERIFY_ERROR", "missing first name in ABHA profile", http.StatusBadGateway, nil, nil)
// 	}

// 	// Optional fields
// 	emailStr := ""
// 	if verifyOut.ABHAProfile.Email != nil {
// 		emailStr = trim(*verifyOut.ABHAProfile.Email)
// 	}
// 	mobileStr := trim(verifyOut.ABHAProfile.Mobile)
// 	addressLine1 := trim(verifyOut.ABHAProfile.Address)
// 	pincode := trim(verifyOut.ABHAProfile.PinCode)

// 	healthID := ""
// 	if len(verifyOut.ABHAProfile.PhrAddress) > 0 {
// 		healthID = trim(verifyOut.ABHAProfile.PhrAddress[0])
// 	}

// 	// --- build identifiers slice dynamically (no placeholders)
// 	identifiers := make([]any, 0, 3)
// 	if aadhaarPlain != "" {
// 		identifiers = append(identifiers, map[string]any{
// 			"identifierType": "AADHAAR",
// 			"identifierId":   aadhaarPlain,
// 		})
// 	}
// 	identifiers = append(identifiers, map[string]any{
// 		"identifierType": "ABHA",
// 		"identifierId":   abhaNumber, // keep hyphenation as-is for display; backend may also store a normalized copy
// 	})

// 	// ---- Build HCM Individual payload: include only non-empty fields
// 	nameObj := map[string]any{"givenName": firstName}
// 	addIfNonEmpty(nameObj, "familyName", lastName)
// 	addIfNonEmpty(nameObj, "otherNames", middleName)

// 	indObj := map[string]any{
// 		"tenantId":          in.TenantId,
// 		"clientReferenceId": in.ClientReferenceId,
// 		"name":              nameObj,
// 		"rowVersion":        1,
// 		"isSystemUser":      false,
// 		// identifiers is always present (contains ABHA at least)
// 		"identifiers": identifiers,
// 	}

// 	// Optional Individual fields (added only if non-empty/valid)
// 	if safeDob != "" {
// 		indObj["dateOfBirth"] = safeDob
// 	}
// 	if safeGender != "" {
// 		indObj["gender"] = safeGender
// 	}
// 	if is10(mobileStr) {
// 		indObj["mobileNumber"] = mobileStr
// 	}
// 	if emailStr != "" {
// 		indObj["email"] = emailStr
// 	}

// 	// Address: include only if we have at least one meaningful field
// 	addresses := make([]any, 0, 1)
// 	if addressLine1 != "" || pincode != "" || strings.TrimSpace(in.LocalityCode) != "" {
// 		addr := map[string]any{
// 			"clientReferenceId": in.ClientReferenceId,
// 			"tenantId":          "default",
// 			"type":              "OTHER",
// 		}
// 		addIfNonEmpty(addr, "addressLine1", addressLine1)
// 		addIfNonEmpty(addr, "pincode", pincode)
// 		if lc := strings.TrimSpace(in.LocalityCode); lc != "" {
// 			addr["locality"] = map[string]any{"code": lc}
// 		}
// 		addresses = append(addresses, addr)
// 	}
// 	if len(addresses) > 0 {
// 		indObj["address"] = addresses
// 	}

// 	// ---- Request wrapper
// 	individualBody := map[string]any{
// 		"RequestInfo": map[string]any{
// 			"apiId":     "dev",
// 			"msgId":     "Create Individual",
// 			"authToken": in.HcmAuthToken,
// 			"userInfo":  map[string]any{"id": in.UserID, "uuid": in.UserUUID},
// 		},
// 		"Individual": indObj,
// 	}

// 	// ---- 7) Create Individual in HCM
// 	reqBytes, err := json.Marshal(individualBody)
// 	if err != nil {
// 		return nil, errorsx.Internal("INDIVIDUAL_PAYLOAD_MARSHAL_FAILED", "failed to marshal individual payload", nil, err)
// 	}

// 	indURL := s.cfg.IndividualCreateURL
// 	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, indURL, bytes.NewReader(reqBytes))
// 	if err != nil {
// 		return nil, errorsx.Internal("INDIVIDUAL_HTTP_REQ_FAILED", "failed to build individual request", nil, err)
// 	}
// 	httpReq.Header.Set("Content-Type", "application/json")
// 	httpReq.Header.Set("REQUEST-ID", uuid.New().String())

// 	resp, err := s.httpClient.Do(httpReq)
// 	if err != nil {
// 		return nil, errorsx.Internal("INDIVIDUAL_CREATE_CALL_FAILED", "failed to call individual create", nil, err)
// 	}
// 	defer resp.Body.Close()

// 	respBody, err := io.ReadAll(resp.Body)
// 	if err != nil {
// 		return nil, errorsx.Internal("INDIVIDUAL_RESP_READ_FAILED", "failed to read individual response", nil, err)
// 	}
// 	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
// 		return nil, errorsx.FromUpstream(resp.StatusCode, respBody, "INDIVIDUAL_CREATE_FAILED")
// 	}

// 	// ---- 8) Parse Individual response and persist ABHA profile
// 	var indOut dtos.IndividualCreateResponse
// 	if err := json.Unmarshal(respBody, &indOut); err != nil {
// 		return nil, errorsx.Internal("INDIVIDUAL_PARSE_FAILED", "failed to parse individual response", nil, err)
// 	}
// 	if strings.TrimSpace(indOut.Individual.Id) == "" {
// 		return nil, errorsx.Internal("INDIVIDUAL_ID_MISSING_IN_RESPONSE", "missing id in individual response", nil, nil)
// 	}

// 	// Use Individual.Id as external_id if it's a valid UUID; else generate one.
// 	externalID := uuid.New().String()
// 	if _, err := uuid.Parse(indOut.Individual.Id); err == nil {
// 		externalID = indOut.Individual.Id
// 	}

// 	// Business id for the transaction table
// 	hcmBusinessID := strings.TrimSpace(indOut.Individual.IndividualId)
// 	if hcmBusinessID == "" {
// 		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: individualId missing in response; falling back to id=%s", indOut.Individual.Id)
// 		hcmBusinessID = indOut.Individual.Id
// 	}

// 	now := time.Now().UTC()
// 	emailForProfile := emailStr
// 	mobileForProfile := mobileStr

// 	abhaProfile := &domain.AbhaNumber{
// 		ExternalID:       externalID,
// 		ABHANumber:       abhaNumber,
// 		HealthID:         healthID,
// 		AccessToken:      verifyOut.Tokens.Token,
// 		RefreshToken:     verifyOut.Tokens.RefreshToken,
// 		FirstName:        verifyOut.ABHAProfile.FirstName,
// 		MiddleName:       verifyOut.ABHAProfile.MiddleName,
// 		LastName:         verifyOut.ABHAProfile.LastName,
// 		Name:             strings.TrimSpace(verifyOut.ABHAProfile.FirstName + " " + verifyOut.ABHAProfile.LastName),
// 		Gender:           verifyOut.ABHAProfile.Gender,
// 		DateOfBirth:      verifyOut.ABHAProfile.Dob,
// 		Email:            emailForProfile,
// 		Mobile:           mobileForProfile,
// 		Address:          verifyOut.ABHAProfile.Address,
// 		State:            verifyOut.ABHAProfile.StateCode,
// 		District:         verifyOut.ABHAProfile.DistrictCode,
// 		Pincode:          verifyOut.ABHAProfile.PinCode,
// 		ProfilePhoto:     verifyOut.ABHAProfile.Photo,
// 		New:              true,
// 		CreatedBy:        0,
// 		LastModifiedBy:   0,
// 		CreatedDate:      now,
// 		LastModifiedDate: now,
// 	}

// 	ensureExternalID(abhaProfile)

// 	if err := s.abhaRepo.SaveAbhaProfile(ctx, abhaProfile); err != nil {
// 		return nil, errorsx.Internal("ABHA_SAVE_FAILED", "failed to save ABHA profile", nil, err)
// 	}

// 	log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] Success | ABHA=%s ExternalID=%s", abhaNumber, abhaProfile.ExternalID)

// 	// Update transaction record (best-effort)
// 	if err := s.abhaRepo.UpdateTxnOnVerify(ctx, in.TxnId, hcmBusinessID, abhaNumber, "system"); err != nil {
// 		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: UpdateTxnOnVerify failed: %v", err)
// 	}

// 	out := map[string]any{
// 		"abhaNumber":   abhaNumber,
// 		"individualId": hcmBusinessID,
// 		"hcmResponse":  json.RawMessage(respBody),
// 	}
// 	final, mErr := json.Marshal(out)
// 	if mErr != nil {
// 		return respBody, nil
// 	}
// 	return final, nil
// }

// // -------------- helpers --------------

// func fallback(v, def string) string {
// 	if strings.TrimSpace(v) == "" {
// 		return def
// 	}
// 	return v
// }

// // ABDM sometimes gives YYYY-MM-DD; HCM expects DD/MM/YYYY in your sample.
// func formatDob(s string) string {
// 	t, err := time.Parse("2006-01-02", s)
// 	if err == nil {
// 		return t.Format("02/01/2006")
// 	}
// 	t, err = time.Parse("02-01-2006", s)
// 	if err == nil {
// 		return t.Format("02/01/2006")
// 	}
// 	return s
// }

// func normalizeGender(g string) string {
// 	g = strings.ToUpper(strings.TrimSpace(g))
// 	switch g {
// 	case "M", "MALE":
// 		return "MALE"
// 	case "F", "FEMALE":
// 		return "FEMALE"
// 	case "O", "OTHER", "OTHERS":
// 		return "OTHER"
// 	default:
// 		return "MALE"
// 	}
// }

// // ---- helpers (keep in your file) ----
// func ensureExternalID(a *domain.AbhaNumber) {
// 	if strings.TrimSpace(a.ExternalID) == "" {
// 		a.ExternalID = uuid.New().String()
// 	}
// }

// func (s *abhaService) ResolveAadhaarFromABHA(ctx context.Context, abha string) (string, error) {
// 	abhaCanon, ok := utils.CanonicalizeABHA(abha)
// 	if !ok {
// 		return "", fmt.Errorf("INVALID_ABHA_FORMAT")
// 	}
// 	enc, err := s.abhaRepo.GetEncryptedAadhaarByABHA(ctx, abhaCanon)
// 	if err != nil {
// 		return "", fmt.Errorf("ABHA_NOT_FOUND_OR_NO_LINKED_AADHAAR: %w", err)
// 	}
// 	if strings.TrimSpace(enc) == "" {
// 		return "", fmt.Errorf("LINKED_AADHAAR_MISSING")
// 	}
// 	plain, derr := utils.DecryptFromDB(enc)
// 	if derr != nil {
// 		return "", fmt.Errorf("AADHAAR_DECRYPT_FAILED: %w", derr)
// 	}
// 	if !regexp.MustCompile(`^\d{12}$`).MatchString(plain) {
// 		return "", fmt.Errorf("AADHAAR_FORMAT_INVALID")
// 	}
// 	return plain, nil
// }

// // ---------------- Verify & Create (V2) ----------------

// // Verify Aadhaar OTP with ABDM, create Individual in HCM, then persist ABHA profile.
// // Logic unchanged; only syntax/brace hygiene.
// // func (s *abhaService) VerifyAadhaarOtpAndCreateIndividualV2(ctx context.Context, in dtos.VerifyAndCreateV2Input) ([]byte, error) {
// // 	log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] Start | txnId=%s tenant=%s clientRef=%s", in.TxnId, in.TenantId, in.ClientReferenceId)

// // 	// 1) Validate inputs
// // 	if ok := regexp.MustCompile(`^\d{6}$`).MatchString(in.Otp); !ok {
// // 		return nil, fmt.Errorf("INVALID_OTP_FORMAT: otp must be 6 digits")
// // 	}
// // 	if in.Mobile != "" && !regexp.MustCompile(`^\d{10}$`).MatchString(in.Mobile) {
// // 		return nil, fmt.Errorf("INVALID_MOBILE_FORMAT: mobile must be 10 digits")
// // 	}
// // 	if strings.TrimSpace(in.TxnId) == "" {
// // 		return nil, fmt.Errorf("MISSING_TXN_ID")
// // 	}
// // 	if strings.TrimSpace(in.TenantId) == "" || strings.TrimSpace(in.ClientReferenceId) == "" ||
// // 		strings.TrimSpace(in.HcmAuthToken) == "" || strings.TrimSpace(in.UserUUID) == "" || in.UserID == 0 {
// // 		return nil, fmt.Errorf("MISSING_HCM_INPUTS")
// // 	}

// // 	// 2) Encrypt OTP
// // 	pub, err := s.FetchPublicKey(ctx)
// // 	if err != nil {
// // 		return nil, fmt.Errorf("PUBLIC_KEY_FETCH_FAILED: %w", err)
// // 	}
// // 	encOTP, err := s.EncryptData(ctx, pub, in.Otp)
// // 	if err != nil {
// // 		return nil, fmt.Errorf("OTP_ENCRYPT_FAILED: %w", err)
// // 	}

// // 	// 3) Build ABDM verify payload
// // 	var verifyReq dtos.EnrolByAadhaarWithOTPRequestV2
// // 	verifyReq.Consent.Code = "abha-enrollment"
// // 	verifyReq.Consent.Version = "1.4"
// // 	verifyReq.AuthData.AuthMethods = []string{"otp"}
// // 	verifyReq.AuthData.OTP.TimeStamp = time.Now().Format("2006-01-02 15:04:05")
// // 	verifyReq.AuthData.OTP.TxnId = in.TxnId
// // 	verifyReq.AuthData.OTP.OtpValue = encOTP
// // 	if in.Mobile != "" {
// // 		verifyReq.AuthData.OTP.Mobile = in.Mobile
// // 	}

// // 	// 4) ABDM verify call
// // 	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
// // 	if err != nil {
// // 		return nil, fmt.Errorf("ABDM_TOKEN_FAILED: %w", err)
// // 	}
// // 	verifyRespBytes, err := s.sendRequest(ctx, s.cfg.EnrolByAadhaarURL, verifyReq, abdmToken)
// // 	if err != nil {
// // 		if strings.Contains(err.Error(), "INVALID_OTP") {
// // 			return nil, fmt.Errorf("ABDM_INVALID_OTP: %w", err)
// // 		}
// // 		return nil, fmt.Errorf("ABDM_VERIFY_ERROR: %w", err)
// // 	}

// // 	// 5) Parse ABDM response
// // 	var verifyOut dtos.EnrolWithOTPResponseV2
// // 	if uErr := json.Unmarshal(verifyRespBytes, &verifyOut); uErr != nil {
// // 		return nil, fmt.Errorf("ABDM_VERIFY_PARSE_ERROR: %w | body=%s", uErr, string(verifyRespBytes))
// // 	}

// // 	abhaNumber := strings.TrimSpace(verifyOut.ABHAProfile.ABHANumber)
// // 	if abhaNumber == "" {
// // 		abhaNumber = strings.TrimSpace(verifyOut.ABHAProfile.HealthIdNumber)
// // 	}
// // 	if abhaNumber == "" {
// // 		return nil, fmt.Errorf("ABDM_VERIFY_ERROR: missing ABHA number in response")
// // 	}

// // 	// Resolve linked Aadhaar (best-effort)
// // 	aadhaarPlain := ""
// // 	if enc, err := s.abhaRepo.GetEncryptedAadhaarByTxn(ctx, in.TxnId); err == nil && strings.TrimSpace(enc) != "" {
// // 		if dec, derr := utils.DecryptFromDB(enc); derr == nil && regexp.MustCompile(`^\d{12}$`).MatchString(dec) {
// // 			aadhaarPlain = dec
// // 		} else if derr != nil {
// // 			log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: Aadhaar decrypt failed for txn=%s: %v", in.TxnId, derr)
// // 		} else {
// // 			log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: Aadhaar format invalid after decrypt for txn=%s", in.TxnId)
// // 		}
// // 	} else if err != nil {
// // 		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: GetEncryptedAadhaarByTxn(%s) failed: %v", in.TxnId, err)
// // 	}

// // 	// helpers
// // 	trim := func(s string) string { return strings.TrimSpace(s) }
// // 	is10 := func(s string) bool { return regexp.MustCompile(`^\d{10}$`).MatchString(s) }
// // 	addIfNonEmpty := func(m map[string]any, k, v string) {
// // 		if strings.TrimSpace(v) != "" {
// // 			m[k] = v
// // 		}
// // 	}

// // 	safeDob := trim(formatDob(verifyOut.ABHAProfile.Dob))
// // 	safeGender := trim(normalizeGender(verifyOut.ABHAProfile.Gender))

// // 	firstName := trim(verifyOut.ABHAProfile.FirstName)
// // 	middleName := trim(verifyOut.ABHAProfile.MiddleName)
// // 	lastName := trim(verifyOut.ABHAProfile.LastName)
// // 	if firstName == "" {
// // 		return nil, fmt.Errorf("ABDM_VERIFY_ERROR: missing first name in ABHA profile")
// // 	}

// // 	emailStr := ""
// // 	if verifyOut.ABHAProfile.Email != nil {
// // 		emailStr = trim(*verifyOut.ABHAProfile.Email)
// // 	}
// // 	mobileStr := trim(verifyOut.ABHAProfile.Mobile)
// // 	addressLine1 := trim(verifyOut.ABHAProfile.Address)
// // 	pincode := trim(verifyOut.ABHAProfile.PinCode)

// // 	healthID := ""
// // 	if len(verifyOut.ABHAProfile.PhrAddress) > 0 {
// // 		healthID = trim(verifyOut.ABHAProfile.PhrAddress[0])
// // 	}

// // 	// identifiers
// // 	identifiers := make([]any, 0, 3)
// // 	if aadhaarPlain != "" {
// // 		identifiers = append(identifiers, map[string]any{
// // 			"identifierType": "AADHAAR",
// // 			"identifierId":   aadhaarPlain,
// // 		})
// // 	}
// // 	identifiers = append(identifiers, map[string]any{
// // 		"identifierType": "ABHA",
// // 		"identifierId":   abhaNumber,
// // 	})
// // 	// If you decide to add PHR later:
// // 	// if healthID != "" {
// // 	// 	identifiers = append(identifiers, map[string]any{
// // 	// 		"identifierType": "PHR",
// // 	// 		"identifierId":   healthID,
// // 	// 	})
// // 	// }

// // 	// name
// // 	nameObj := map[string]any{"givenName": firstName}
// // 	addIfNonEmpty(nameObj, "familyName", lastName)
// // 	addIfNonEmpty(nameObj, "otherNames", middleName)

// // 	// individual
// // 	indObj := map[string]any{
// // 		"tenantId":          in.TenantId,
// // 		"clientReferenceId": in.ClientReferenceId,
// // 		"name":              nameObj,
// // 		"rowVersion":        1,
// // 		"isSystemUser":      false,
// // 		"identifiers":       identifiers,
// // 	}

// // 	if safeDob != "" {
// // 		indObj["dateOfBirth"] = safeDob
// // 	}
// // 	if safeGender != "" {
// // 		indObj["gender"] = safeGender
// // 	}
// // 	if is10(mobileStr) {
// // 		indObj["mobileNumber"] = mobileStr
// // 	}
// // 	if emailStr != "" {
// // 		indObj["email"] = emailStr
// // 	}

// // 	// address (optional)
// // 	addresses := make([]any, 0, 1)
// // 	if addressLine1 != "" || pincode != "" || strings.TrimSpace(in.LocalityCode) != "" {
// // 		addr := map[string]any{
// // 			"clientReferenceId": in.ClientReferenceId,
// // 			"tenantId":          "default",
// // 			"type":              "OTHER",
// // 		}
// // 		addIfNonEmpty(addr, "addressLine1", addressLine1)
// // 		addIfNonEmpty(addr, "pincode", pincode)
// // 		if lc := strings.TrimSpace(in.LocalityCode); lc != "" {
// // 			addr["locality"] = map[string]any{"code": lc}
// // 		}
// // 		addresses = append(addresses, addr)
// // 	}
// // 	if len(addresses) > 0 {
// // 		indObj["address"] = addresses
// // 	}

// // 	// request wrapper
// // 	individualBody := map[string]any{
// // 		"RequestInfo": map[string]any{
// // 			"apiId":     "dev",
// // 			"msgId":     "Create Individual",
// // 			"authToken": in.HcmAuthToken,
// // 			"userInfo":  map[string]any{"id": in.UserID, "uuid": in.UserUUID},
// // 		},
// // 		"Individual": indObj,
// // 	}

// // 	// 7) Create Individual in HCM
// // 	reqBytes, err := json.Marshal(individualBody)
// // 	if err != nil {
// // 		return nil, fmt.Errorf("INDIVIDUAL_PAYLOAD_MARSHAL_FAILED: %w", err)
// // 	}

// // 	indURL := s.cfg.IndividualCreateURL
// // 	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, indURL, bytes.NewReader(reqBytes))
// // 	if err != nil {
// // 		return nil, fmt.Errorf("INDIVIDUAL_HTTP_REQ_FAILED: %w", err)
// // 	}
// // 	httpReq.Header.Set("Content-Type", "application/json")
// // 	httpReq.Header.Set("REQUEST-ID", uuid.New().String())

// // 	resp, err := s.httpClient.Do(httpReq)
// // 	if err != nil {
// // 		return nil, fmt.Errorf("INDIVIDUAL_CREATE_CALL_FAILED: %w", err)
// // 	}
// // 	defer resp.Body.Close()

// // 	respBody, err := io.ReadAll(resp.Body)
// // 	if err != nil {
// // 		return nil, fmt.Errorf("INDIVIDUAL_RESP_READ_FAILED: %w", err)
// // 	}
// // 	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
// // 		return nil, fmt.Errorf("INDIVIDUAL_CREATE_FAILED(%d): %s", resp.StatusCode, string(respBody))
// // 	}

// // 	// 8) Parse Individual response and persist ABHA profile
// // 	var indOut dtos.IndividualCreateResponse
// // 	if err := json.Unmarshal(respBody, &indOut); err != nil {
// // 		return nil, fmt.Errorf("INDIVIDUAL_PARSE_FAILED: %w", err)
// // 	}
// // 	if strings.TrimSpace(indOut.Individual.Id) == "" {
// // 		return nil, fmt.Errorf("INDIVIDUAL_ID_MISSING_IN_RESPONSE")
// // 	}

// // 	// external id
// // 	externalID := uuid.New().String()
// // 	if _, err := uuid.Parse(indOut.Individual.Id); err == nil {
// // 		externalID = indOut.Individual.Id
// // 	}

// // 	// business id
// // 	hcmBusinessID := strings.TrimSpace(indOut.Individual.IndividualId)
// // 	if hcmBusinessID == "" {
// // 		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: individualId missing; fallback to id=%s", indOut.Individual.Id)
// // 		hcmBusinessID = indOut.Individual.Id
// // 	}

// // 	now := time.Now().UTC()

// // 	abhaProfile := &domain.AbhaNumber{
// // 		ExternalID:       externalID,
// // 		ABHANumber:       abhaNumber,
// // 		HealthID:         healthID,
// // 		AccessToken:      verifyOut.Tokens.Token,
// // 		RefreshToken:     verifyOut.Tokens.RefreshToken,
// // 		FirstName:        verifyOut.ABHAProfile.FirstName,
// // 		MiddleName:       verifyOut.ABHAProfile.MiddleName,
// // 		LastName:         verifyOut.ABHAProfile.LastName,
// // 		Name:             strings.TrimSpace(verifyOut.ABHAProfile.FirstName + " " + verifyOut.ABHAProfile.LastName),
// // 		Gender:           verifyOut.ABHAProfile.Gender,
// // 		DateOfBirth:      verifyOut.ABHAProfile.Dob,
// // 		Email:            emailStr,
// // 		Mobile:           mobileStr,
// // 		Address:          verifyOut.ABHAProfile.Address,
// // 		State:            verifyOut.ABHAProfile.StateCode,
// // 		District:         verifyOut.ABHAProfile.DistrictCode,
// // 		Pincode:          verifyOut.ABHAProfile.PinCode,
// // 		ProfilePhoto:     verifyOut.ABHAProfile.Photo,
// // 		New:              true,
// // 		CreatedBy:        0,
// // 		LastModifiedBy:   0,
// // 		CreatedDate:      now,
// // 		LastModifiedDate: now,
// // 	}

// // 	ensureExternalID(abhaProfile)

// // 	if err := s.abhaRepo.SaveAbhaProfile(ctx, abhaProfile); err != nil {
// // 		return nil, fmt.Errorf("failed to save ABHA profile: %w", err)
// // 	}

// // 	log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] Success | ABHA=%s ExternalID=%s", abhaNumber, abhaProfile.ExternalID)

// // 	// Update transaction (best-effort)
// // 	if err := s.abhaRepo.UpdateTxnOnVerify(ctx, in.TxnId, hcmBusinessID, abhaNumber, "system"); err != nil {
// // 		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: UpdateTxnOnVerify failed: %v", err)
// // 	}

// // 	out := map[string]any{
// // 		"abhaNumber":   abhaNumber,
// // 		"individualId": hcmBusinessID,
// // 		"hcmResponse":  json.RawMessage(respBody),
// // 	}
// // 	final, mErr := json.Marshal(out)
// // 	if mErr != nil {
// // 		return respBody, nil
// // 	}
// // 	return final, nil
// // }

// //// ------------------------------ card download v2

// func (s *abhaService) FetchAbhaCardByTypeV2(
// 	ctx context.Context,
// 	abhaNumber, cardType, token, refreshToken string,
// ) ([]byte, string, error) {

// 	log.Printf("[FetchAbhaCardByTypeV2] Start, ABHA: %s, cardType: %s", abhaNumber, cardType)

// 	// Map endpoint
// 	var endpoint string
// 	switch cardType {
// 	case "getCard":
// 		endpoint = s.cfg.ABHACardEndpoints.GetCard
// 	case "getSvgCard":
// 		endpoint = s.cfg.ABHACardEndpoints.GetSvgCard
// 	case "getPngCard":
// 		endpoint = s.cfg.ABHACardEndpoints.GetPngCard
// 	default:
// 		return nil, "", errorsx.BadRequest("INVALID_CARD_TYPE", fmt.Sprintf("unsupported card type: %s", cardType), nil, nil)
// 	}
// 	log.Printf("[FetchAbhaCardByTypeV2] Endpoint resolved: %s", endpoint)

// 	// ABDM Authorization token
// 	log.Printf("[FetchAbhaCardByTypeV2] Fetching ABDM auth token...")
// 	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
// 	if err != nil {
// 		return nil, "", errorsx.Internal("AUTH_TOKEN_FETCH_FAILED", "failed to fetch ABDM token", nil, err)
// 	}
// 	log.Printf("[FetchAbhaCardByTypeV2] ABDM token fetched, length=%d", len(abdmToken))

// 	fetchImage := func(xToken string) ([]byte, string, error) {
// 		log.Printf("[FetchAbhaCardByTypeV2:fetchImage] GET %s (X-Token len=%d)", endpoint, len(xToken))

// 		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
// 		if err != nil {
// 			return nil, "", errorsx.Internal("REQ_BUILD_FAILED", "failed to build request", nil, err)
// 		}
// 		req.Header.Set("Accept", "*/*")
// 		req.Header.Set("Accept-Language", "en-US")
// 		req.Header.Set("X-Token", "Bearer "+xToken) // caller-provided token(s)
// 		req.Header.Set("Authorization", "Bearer "+abdmToken)
// 		req.Header.Set("TIMESTAMP", time.Now().UTC().Format(time.RFC3339Nano))
// 		req.Header.Set("REQUEST-ID", newRequestID())

// 		resp, err := s.httpClient.Do(req)
// 		if err != nil {
// 			return nil, "", errorsx.Internal("HTTP_DO_FAILED", "failed to call card endpoint", nil, err)
// 		}
// 		defer resp.Body.Close()

// 		data, readErr := io.ReadAll(resp.Body)
// 		if readErr != nil {
// 			return nil, "", errorsx.Internal("READ_BODY_FAILED", "failed to read response", nil, readErr)
// 		}

// 		if resp.StatusCode != http.StatusOK {
// 			return nil, "", errorsx.FromUpstream(resp.StatusCode, data, "ABHA_CARD_FETCH")
// 		}

// 		detected := http.DetectContentType(data)

// 		// SVG case: attempt to extract embedded image
// 		if detected == "text/xml" || detected == "image/svg+xml" {
// 			if decoded, actualType, derr := utils.ExtractImageFromSVG(data); derr == nil {
// 				return decoded, actualType, nil
// 			}
// 			// If extraction fails, continue with SVG bytes
// 			return data, "image/svg+xml", nil
// 		}

// 		return data, detected, nil
// 	}

// 	// Try with access token
// 	if token != "" {
// 		if img, ctype, err := fetchImage(token); err == nil {
// 			log.Printf("[FetchAbhaCardByTypeV2] SUCCESS via access token")
// 			return img, ctype, nil
// 		}
// 		log.Printf("[FetchAbhaCardByTypeV2] Access token attempt failed")
// 	}

// 	// Retry with refresh token
// 	if refreshToken != "" {
// 		if img, ctype, err := fetchImage(refreshToken); err == nil {
// 			log.Printf("[FetchAbhaCardByTypeV2] SUCCESS via refresh token")
// 			return img, ctype, nil
// 		}
// 		return nil, "", errorsx.UpstreamFailed("ABHA_CARD_FETCH_FAILED", "failed with provided tokens", http.StatusBadGateway, nil, err)
// 	}

// 	return nil, "", errorsx.BadRequest("MISSING_TOKEN", "no usable token provided (token/refresh_token both empty)", nil, nil)
// }

// func newRequestID() string {
// 	// prefer uuid; fallback to 16 random bytes
// 	id, err := uuid.NewRandom()
// 	if err == nil {
// 		return id.String()
// 	}
// 	var b [16]byte
// 	_, _ = rand.Read(b[:])
// 	return fmt.Sprintf("%x", b[:])
// }

package services

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/x509"
	"digit-abdm/internal/utils"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"io"
	"log"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/google/uuid"

	"digit-abdm/configs"
	"digit-abdm/internal/core/domain"
	"digit-abdm/internal/core/ports"
	errorsx "digit-abdm/internal/errorsx"
	"digit-abdm/pkg/dtos"
)

type abhaService struct {
	cfg        *configs.Config
	httpClient *http.Client
	abhaRepo   ports.AbhaRepository
}

func NewABHAService(cfg *configs.Config, repo ports.AbhaRepository) ports.ABHAService {
	return &abhaService{
		cfg:        cfg,
		httpClient: &http.Client{},
		abhaRepo:   repo,
	}
}

// ----------------------------- common upstream error wrapper -----------------------------

// wrapUpstream inspects the upstream body and, if it finds `error.code` and `error.message` (or
// top-level `code`/`message`), uses those as the final ErrorCode/ErrorMessage.
// AdditionalDetails always carries the parsed upstream payload.
// The cause is "upstream status <status>" so your envelope can surface it as description.
func (s *abhaService) wrapUpstream(status int, body []byte, codePrefix string) *errorsx.Error {
	var parsed map[string]any
	_ = json.Unmarshal(body, &parsed)

	upCode, upMsg := "", ""

	if len(parsed) > 0 {
		// Prefer nested { "error": { "code": "...", "message": "..." } }
		if em, ok := parsed["error"].(map[string]any); ok {
			if c, ok := em["code"].(string); ok && strings.TrimSpace(c) != "" {
				upCode = c
			}
			if m, ok := em["message"].(string); ok && strings.TrimSpace(m) != "" {
				upMsg = m
			}
		}
		// Fallback to top-level code / message if present
		if upCode == "" {
			if c, ok := parsed["code"].(string); ok && strings.TrimSpace(c) != "" {
				upCode = c
			}
		}
		if upMsg == "" {
			if m, ok := parsed["message"].(string); ok && strings.TrimSpace(m) != "" {
				upMsg = m
			}
		}
	}

	// Construct cause text for description in your final envelope
	cause := fmt.Errorf("upstream status %d", status)

	// If we extracted upstream code/message, surface them directly
	if upCode != "" || upMsg != "" {
		// Use upstream code / message
		return errorsx.UpstreamFailed(upCode, upMsg, status, parsed, cause)
	}

	// Otherwise, keep your existing prefix behavior, but still pass parsed map if available
	msg := http.StatusText(status)
	if len(parsed) > 0 {
		return errorsx.UpstreamFailed(codePrefix+"_UPSTREAM", msg, status, parsed, cause)
	}
	return errorsx.UpstreamFailed(codePrefix+"_UPSTREAM", msg, status, string(body), cause)
}

// ----------------------------------------------------------------------------------------

// FetchPublicKey retrieves RSA public key
func (s *abhaService) FetchPublicKey(ctx context.Context) (*rsa.PublicKey, error) {
	log.Printf("[FetchPublicKey] Starting public key fetch from URL: %s", s.cfg.PublicKeyURL)

	req, err := http.NewRequestWithContext(ctx, "GET", s.cfg.PublicKeyURL, nil)
	if err != nil {
		log.Printf("[FetchPublicKey] ERROR: Failed to create HTTP request: %v", err)
		return nil, errorsx.Internal("PUBLIC_KEY_REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
	}

	log.Printf("[FetchPublicKey] Sending HTTP GET request...")
	resp, err := s.httpClient.Do(req)
	if err != nil {
		log.Printf("[FetchPublicKey] ERROR: HTTP request failed: %v", err)
		return nil, errorsx.Internal("PUBLIC_KEY_HTTP_FAILED", "public key fetch failed", nil, err)
	}
	defer resp.Body.Close()

	log.Printf("[FetchPublicKey] Response received with status: %d", resp.StatusCode)

	certPEM, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("[FetchPublicKey] ERROR: Failed to read response body: %v", err)
		return nil, errorsx.Internal("PUBLIC_KEY_READ_FAILED", "failed to read public key response", nil, err)
	}

	log.Printf("[FetchPublicKey] Response body length: %d bytes", len(certPEM))

	block, _ := pem.Decode(certPEM)
	if block == nil {
		log.Printf("[FetchPublicKey] ERROR: Failed to parse PEM block")
		return nil, errorsx.Internal("PEM_PARSE_FAILED", "failed to parse PEM block", nil, nil)
	}

	log.Printf("[FetchPublicKey] PEM block decoded successfully, type: %s", block.Type)

	pubKey, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		log.Printf("[FetchPublicKey] ERROR: Failed to parse public key: %v", err)
		return nil, errorsx.Internal("PUBLIC_KEY_PARSE_FAILED", "failed to parse public key", nil, err)
	}

	rsaPubKey, ok := pubKey.(*rsa.PublicKey)
	if !ok {
		log.Printf("[FetchPublicKey] ERROR: Key is not RSA type")
		return nil, errorsx.Internal("PUBLIC_KEY_TYPE_INVALID", "not RSA public key", nil, nil)
	}

	log.Printf("[FetchPublicKey] SUCCESS: RSA public key fetched successfully, key size: %d bits", rsaPubKey.Size()*8)
	return rsaPubKey, nil
}

func (s *abhaService) EncryptData(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
	log.Printf("[EncryptData] Starting data encryption, data length: %d characters", len(data))

	// Use SHA-1 (per ABDM / DevGlan config)
	hash := sha1.New()
	log.Printf("[EncryptData] Using SHA-1 hash algorithm for OAEP encryption")

	encryptedBytes, err := rsa.EncryptOAEP(hash, rand.Reader, publicKey, []byte(data), nil)
	if err != nil {
		log.Printf("[EncryptData] ERROR: RSA OAEP encryption failed: %v", err)
		return "", errorsx.Internal("OAEP_ENCRYPT_FAILED", "RSA OAEP encryption failed", nil, err)
	}

	log.Printf("[EncryptData] Encryption successful, encrypted bytes length: %d", len(encryptedBytes))

	// Encode to base64
	encoded := base64.StdEncoding.EncodeToString(encryptedBytes)
	log.Printf("[EncryptData] Base64 encoding completed, encoded string length: %d", len(encoded))

	return encoded, nil
}

////////////// --------------------------------------------------------------------------------------------

func (s *abhaService) SendOTPRequest(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error) {
	log.Printf("[SendOTPRequest] Initiating OTP request to URL: %s", s.cfg.OTPRequestURL)
	log.Printf("[SendOTPRequest] Request payload: %+v", req)
	log.Printf("[SendOTPRequest] Using token length: %d characters", len(token))

	response, err := s.sendRequest(ctx, s.cfg.OTPRequestURL, req, token)
	if err != nil {
		log.Printf("[SendOTPRequest] ERROR: OTP request failed: %v", err)
		return nil, err
	}

	log.Printf("[SendOTPRequest] SUCCESS: OTP request completed, response length: %d bytes", len(response))
	return response, nil
}

func (s *abhaService) RecordAadhaarTxnOnOtp(ctx context.Context, tenantID, aadhaarPlain, txnID string) error {
	// Basic sanity — 12 digits Aadhaar
	if ok := regexp.MustCompile(`^\d{12}$`).MatchString(aadhaarPlain); !ok {
		return errorsx.BadRequest("INVALID_AADHAAR_FORMAT", "aadhaar must be 12 digits", nil, nil)
	}
	enc, err := utils.EncryptForDB(aadhaarPlain)
	if err != nil {
		return errorsx.Internal("AADHAAR_ENCRYPT_FAILED", "failed to encrypt aadhaar for db", nil, err)
	}
	hash := utils.HashForLookup(aadhaarPlain)
	actor := "system" // or pull from context
	_, err = s.abhaRepo.UpsertAadhaarTxnOnOtp(ctx, tenantID, txnID, enc, hash, actor)
	if err != nil {
		return errorsx.Internal("TXN_UPSERT_FAILED", "failed to upsert aadhaar txn", nil, err)
	}
	log.Printf("[RecordAadhaarTxnOnOtp] Upsert done | tenant=%s txn=%s", tenantID, txnID)
	return nil
}

func (s *abhaService) SendEnrolRequestWithOTP(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error) {
	log.Printf("[SendEnrolRequestWithOTP] Starting enrolment request to URL: %s", s.cfg.EnrolByAadhaarURL)
	log.Printf("[SendEnrolRequestWithOTP] Request payload: %+v", req)
	log.Printf("[SendEnrolRequestWithOTP] Using token length: %d characters", len(token))

	respBody, err := s.sendRequest(ctx, s.cfg.EnrolByAadhaarURL, req, token)
	if err != nil {
		log.Printf("[SendEnrolRequestWithOTP] ERROR: Enrolment request failed: %v", err)
		return nil, err
	}

	log.Printf("[SendEnrolRequestWithOTP] Response received, length: %d bytes", len(respBody))

	// Parse response
	var enrolResp dtos.EnrolWithOTPResponse
	if err := json.Unmarshal(respBody, &enrolResp); err != nil {
		log.Printf("[SendEnrolRequestWithOTP] ERROR: Failed to parse enrolment response: %v", err)
		return nil, errorsx.Internal("ENROL_PARSE_FAILED", "failed to parse enrolment response", nil, err)
	}

	log.Printf("[SendEnrolRequestWithOTP] Response parsed successfully")
	log.Printf("[SendEnrolRequestWithOTP] Response details: %+v", enrolResp)

	// Map response to model
	profile := enrolResp.ABHAProfile
	tokens := enrolResp.Tokens
	now := time.Now().UTC()

	log.Printf("[SendEnrolRequestWithOTP] ABHA profile extracted: %+v", profile)
	log.Printf("[SendEnrolRequestWithOTP] Tokens extracted: %+v", tokens)
	abha := &domain.AbhaNumber{
		ExternalID:       uuid.New().String(),
		ABHANumber:       profile.ABHANumber,
		HealthID:         "",
		Email:            profile.Email,
		FirstName:        profile.FirstName,
		MiddleName:       profile.MiddleName,
		LastName:         profile.LastName,
		ProfilePhoto:     profile.Photo,
		AccessToken:      tokens.Token,
		RefreshToken:     tokens.RefreshToken,
		Address:          profile.Address,
		DateOfBirth:      profile.Dob,
		District:         profile.DistrictCode,
		Gender:           profile.Gender,
		Name:             profile.FirstName + " " + profile.LastName,
		Pincode:          profile.PinCode,
		State:            profile.StateCode,
		Mobile:           profile.Mobile,
		New:              true,
		CreatedBy:        0, // or actual user ID if available
		LastModifiedBy:   0, // or actual user ID if available
		CreatedDate:      now,
		LastModifiedDate: now,
	}

	if len(profile.PhrAddress) > 0 {
		abha.HealthID = profile.PhrAddress[0]
		log.Printf("[SendEnrolRequestWithOTP] Health ID extracted from PhrAddress: %s", abha.HealthID)
	} else {
		log.Printf("[SendEnrolRequestWithOTP] WARNING: No PhrAddress found in profile")
	}

	log.Printf("[SendEnrolRequestWithOTP] Attempting to save ABHA profile to database...")
	// Save to DB
	if err := s.abhaRepo.SaveAbhaProfile(ctx, abha); err != nil {
		log.Printf("[SendEnrolRequestWithOTP] ERROR: Failed to save ABHA profile to database: %v", err)
		return nil, errorsx.Internal("ABHA_SAVE_FAILED", "failed to save ABHA profile", nil, err)
	}

	log.Printf("[SendEnrolRequestWithOTP] SUCCESS: ABHA profile saved successfully with ID: %s", abha.ExternalID)
	return respBody, nil
}

func (s *abhaService) FetchAbhaCard(ctx context.Context, abhaNumber string) ([]byte, error) {
	log.Printf("[FetchAbhaCard] Starting card fetch for ABHA number: %s", abhaNumber)

	// Get access token from DB
	abha, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
	if err != nil {
		log.Printf("[FetchAbhaCard] ERROR: Failed to get ABHA profile from DB: %v", err)
		return nil, errorsx.NotFound("ABHA_PROFILE_NOT_FOUND", "failed to get ABHA profile", nil, err)
	}
	if abha.AccessToken == "" {
		log.Printf("[FetchAbhaCard] ERROR: Access token not found for ABHA number: %s", abhaNumber)
		return nil, errorsx.BadRequest("ACCESS_TOKEN_MISSING", "access token not found for ABHA number", nil, nil)
	}

	log.Printf("[FetchAbhaCard] ABHA profile found with access token length: %d chars", len(abha.AccessToken))

	// Get auth token
	log.Printf("[FetchAbhaCard] Fetching ABDM auth token...")
	authToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		log.Printf("[FetchAbhaCard] ERROR: Failed to fetch ABDM auth token: %v", err)
		return nil, errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM auth token", nil, err)
	}

	log.Printf("[FetchAbhaCard] ABDM auth token fetched successfully, length: %d chars", len(authToken))

	// Make HTTP request to getCard
	cardURL := "https://healthidsbx.abdm.gov.in/api/v1/account/getCard"
	log.Printf("[FetchAbhaCard] Creating HTTP POST request to: %s", cardURL)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, cardURL, nil)
	if err != nil {
		log.Printf("[FetchAbhaCard] ERROR: Failed to create HTTP request: %v", err)
		return nil, errorsx.Internal("GETCARD_REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
	}
	req.Header.Set("accept", "*/*")
	req.Header.Set("Accept-Language", "en-US")
	req.Header.Set("X-Token", "Bearer "+abha.AccessToken)
	req.Header.Set("Authorization", "Bearer "+authToken)

	log.Printf("[FetchAbhaCard] Request headers set, sending request...")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		log.Printf("[FetchAbhaCard] ERROR: HTTP request failed: %v", err)
		return nil, errorsx.Internal("GETCARD_HTTP_FAILED", "failed to call getCard", nil, err)
	}
	defer resp.Body.Close()

	log.Printf("[FetchAbhaCard] Response received with status code: %d", resp.StatusCode)

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		log.Printf("[FetchAbhaCard] ERROR: getCard failed with status %d, response: %s", resp.StatusCode, string(bodyBytes))
		return nil, s.wrapUpstream(resp.StatusCode, bodyBytes, "GETCARD")
	}

	responseBody, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("[FetchAbhaCard] ERROR: Failed to read response body: %v", err)
		return nil, errorsx.Internal("GETCARD_READ_FAILED", "failed to read getCard response", nil, err)
	}

	log.Printf("[FetchAbhaCard] SUCCESS: Card data fetched, response length: %d bytes", len(responseBody))
	return responseBody, nil
}

func (s *abhaService) FetchAbhaCardByType(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
	log.Printf("[FetchAbhaCardByType] Starting card fetch for ABHA: %s, card type: %s", abhaNumber, cardType)

	abhaData, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
	if err != nil || abhaData.AccessToken == "" {
		log.Printf("[FetchAbhaCardByType] ERROR: ABHA not found or access token missing for number: %s", abhaNumber)
		return nil, "", errorsx.NotFound("ABHA_NOT_FOUND_OR_TOKEN_MISSING", "abha not found or access token missing", nil, err)
	}
	log.Printf("[FetchAbhaCardByType] ABHA profile found: %s, access token length: %d chars",
		abhaData.ABHANumber, len(abhaData.AccessToken))

	log.Printf("[FetchAbhaCardByType] Fetching ABDM auth token...")
	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		log.Printf("[FetchAbhaCardByType] ERROR: Failed to fetch ABDM token: %v", err)
		return nil, "", errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM token", nil, err)
	}
	log.Printf("[FetchAbhaCardByType] ABDM auth token fetched successfully, length: %d chars", len(abdmToken))

	var endpoint string
	switch cardType {
	case "getCard":
		endpoint = s.cfg.ABHACardEndpoints.GetCard
	case "getSvgCard":
		endpoint = s.cfg.ABHACardEndpoints.GetSvgCard
	case "getPngCard":
		endpoint = s.cfg.ABHACardEndpoints.GetPngCard
	default:
		log.Printf("[FetchAbhaCardByType] ERROR: Unsupported card type: %s", cardType)
		return nil, "", errorsx.BadRequest("INVALID_CARD_TYPE", fmt.Sprintf("unsupported card type: %s", cardType), nil, nil)
	}
	log.Printf("[FetchAbhaCardByType] Card type '%s' mapped to endpoint: %s", cardType, endpoint)

	fetchImage := func(token string) ([]byte, string, error) {
		log.Printf("[FetchAbhaCardByType:fetchImage] Creating HTTP GET request to endpoint with token length: %d chars", len(token))

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
		if err != nil {
			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: Failed to create HTTP request: %v", err)
			return nil, "", errorsx.Internal("GETCARD_REQ_BUILD_FAILED", "failed to build card request", nil, err)
		}
		req.Header.Set("Accept", "*/*")
		req.Header.Set("Accept-Language", "en-US")
		req.Header.Set("X-Token", "Bearer "+token)
		req.Header.Set("Authorization", "Bearer "+abdmToken)

		log.Printf("[FetchAbhaCardByType:fetchImage] Sending HTTP GET request...")
		resp, err := s.httpClient.Do(req)
		if err != nil {
			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: HTTP request failed: %v", err)
			return nil, "", errorsx.Internal("GETCARD_HTTP_FAILED", "failed to call card endpoint", nil, err)
		}
		defer resp.Body.Close()

		log.Printf("[FetchAbhaCardByType:fetchImage] Response received with status: %d", resp.StatusCode)

		data, err := io.ReadAll(resp.Body)
		if err != nil {
			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: Failed to read response body: %v", err)
			return nil, "", errorsx.Internal("GETCARD_READ_FAILED", "failed to read response", nil, err)
		}

		log.Printf("[FetchAbhaCardByType:fetchImage] Response body read, length: %d bytes", len(data))

		if resp.StatusCode != http.StatusOK {
			log.Printf("[FetchAbhaCardByType:fetchImage] ERROR: Request failed with status %d: %s", resp.StatusCode, string(data))
			return nil, "", s.wrapUpstream(resp.StatusCode, data, "ABHA_CARD_FETCH")
		}

		// Auto-detect content type
		detectedType := http.DetectContentType(data)
		log.Printf("[FetchAbhaCardByType:fetchImage] Detected content type: %s", detectedType)

		// SVG case: attempt to extract embedded image
		if detectedType == "text/xml" || detectedType == "image/svg+xml" {
			log.Printf("[FetchAbhaCardByType:fetchImage] Attempting to extract base64 image from SVG...")
			decoded, actualType, decodeErr := utils.ExtractImageFromSVG(data)
			if decodeErr != nil {
				log.Printf("[FetchAbhaCardByType:fetchImage] SVG base64 extraction failed: %v", decodeErr)
			} else {
				log.Printf("[FetchAbhaCardByType:fetchImage] Successfully extracted base64 image from SVG, type: %s", actualType)
				return decoded, actualType, nil
			}
		}

		log.Printf("[FetchAbhaCardByType:fetchImage] Returning raw data with detected type: %s", detectedType)
		return data, detectedType, nil
	}

	// Try with access token
	log.Printf("[FetchAbhaCardByType] Attempting to fetch card using access token...")
	imageData, detectedType, err := fetchImage(abhaData.AccessToken)
	if err == nil {
		log.Printf("[FetchAbhaCardByType] SUCCESS: Card fetched successfully using access token")
		return imageData, detectedType, nil
	}

	log.Printf("[FetchAbhaCardByType] Access token failed: %v", err)

	// Retry with refresh token
	if abhaData.RefreshToken != "" {
		log.Printf("[FetchAbhaCardByType] Retrying with refresh token...")
		imageData, detectedType, err2 := fetchImage(abhaData.RefreshToken)
		if err2 == nil {
			log.Printf("[FetchAbhaCardByType] SUCCESS: Card fetched successfully using refresh token")
			return imageData, detectedType, nil
		}
		log.Printf("[FetchAbhaCardByType] ERROR: Refresh token also failed: %v", err2)
		return nil, "", errorsx.UpstreamFailed("ABHA_CARD_FETCH_FAILED", "failed with both access and refresh tokens", http.StatusBadGateway, nil, err2)
	}

	log.Printf("[FetchAbhaCardByType] ERROR: No refresh token available for retry")
	return nil, "", errorsx.Internal("ABHA_CARD_FETCH_FAILED", "failed with access token and no refresh token available", nil, err)
}

// helper to safely slice long token logs
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func (s *abhaService) FetchQRCodeByABHANumber(ctx context.Context, abhaNumber string) ([]byte, error) {
	log.Printf("[FetchQRCodeByABHANumber] Starting QR code fetch for ABHA number: %s", abhaNumber)

	abhaData, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
	if err != nil || abhaData.AccessToken == "" {
		log.Printf("[FetchQRCodeByABHANumber] ERROR: ABHA not found or access token missing for number: %s", abhaNumber)
		return nil, errorsx.NotFound("ABHA_NOT_FOUND_OR_TOKEN_MISSING", "abha not found or access token missing", nil, err)
	}
	log.Printf("[FetchQRCodeByABHANumber] ABHA profile found: %s, access token length: %d chars",
		abhaData.ABHANumber, len(abhaData.AccessToken))

	log.Printf("[FetchQRCodeByABHANumber] Fetching ABDM auth token...")
	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		log.Printf("[FetchQRCodeByABHANumber] ERROR: Failed to fetch ABDM token: %v", err)
		return nil, errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM token", nil, err)
	}
	log.Printf("[FetchQRCodeByABHANumber] ABDM auth token fetched successfully, length: %d chars", len(abdmToken))

	endpoint := s.cfg.QRCode
	log.Printf("[FetchQRCodeByABHANumber] QR endpoint configured: %s", endpoint)

	// Reusable fetch function
	fetchQR := func(token string) ([]byte, error) {
		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Creating HTTP GET request with token length: %d chars", len(token))

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
		if err != nil {
			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: Failed to create HTTP request: %v", err)
			return nil, errorsx.Internal("QR_REQ_BUILD_FAILED", "failed to build QR request", nil, err)
		}

		req.Header.Set("Accept", "*/*")
		req.Header.Set("Accept-Language", "en-US")
		req.Header.Set("X-Token", "Bearer "+token)
		req.Header.Set("Authorization", "Bearer "+abdmToken)

		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Sending HTTP GET request...")
		resp, err := s.httpClient.Do(req)
		if err != nil {
			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: HTTP request failed: %v", err)
			return nil, errorsx.Internal("QR_HTTP_FAILED", "failed to call QR endpoint", nil, err)
		}
		defer resp.Body.Close()

		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Response received with status: %d", resp.StatusCode)

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: Failed to read response body: %v", err)
			return nil, errorsx.Internal("QR_READ_FAILED", "failed to read QR response", nil, err)
		}

		log.Printf("[FetchQRCodeByABHANumber:fetchQR] Response body read, length: %d bytes", len(body))

		if resp.StatusCode != http.StatusOK {
			log.Printf("[FetchQRCodeByABHANumber:fetchQR] ERROR: QR fetch failed with status %d: %s",
				resp.StatusCode, string(body))
			return nil, s.wrapUpstream(resp.StatusCode, body, "QR_FETCH")
		}

		log.Printf("[FetchQRCodeByABHANumber:fetchQR] QR data fetched successfully")
		return body, nil
	}

	log.Printf("[FetchQRCodeByABHANumber] Attempting to fetch QR code using access token...")
	imageData, err := fetchQR(abhaData.AccessToken)
	if err == nil {
		log.Printf("[FetchQRCodeByABHANumber] SUCCESS: QR code fetched successfully using access token")
		return imageData, nil
	}

	log.Printf("[FetchQRCodeByABHANumber] Access token failed, retrying with refresh token: %v", err)

	if abhaData.RefreshToken != "" {
		log.Printf("[FetchQRCodeByABHANumber] Attempting to fetch QR code using refresh token...")
		imageData, err2 := fetchQR(abhaData.RefreshToken)
		if err2 == nil {
			log.Printf("[FetchQRCodeByABHANumber] SUCCESS: QR code fetched successfully using refresh token")
			return imageData, nil
		}
		log.Printf("[FetchQRCodeByABHANumber] ERROR: Refresh token also failed: %v", err2)
		return nil, errorsx.UpstreamFailed("QR_FETCH_FAILED", "both access and refresh token failed", http.StatusBadGateway, nil, err2)
	}

	log.Printf("[FetchQRCodeByABHANumber] ERROR: No refresh token available for retry")
	return nil, errorsx.Internal("QR_FETCH_FAILED", "access token failed and no refresh token available", nil, err)
}

func (s *abhaService) sendRequest(ctx context.Context, url string, payload interface{}, token string) ([]byte, error) {
	log.Printf("[sendRequest] Starting HTTP POST request to URL: %s", url)

	body, err := json.Marshal(payload)
	if err != nil {
		log.Printf("[sendRequest] ERROR: Failed to marshal payload: %v", err)
		return nil, errorsx.Internal("REQ_MARSHAL_FAILED", "failed to marshal request payload", nil, err)
	}

	log.Printf("[sendRequest] Payload marshalled successfully, body length: %d bytes", len(body))

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(body))
	if err != nil {
		log.Printf("[sendRequest] ERROR: Failed to create HTTP request: %v", err)
		return nil, errorsx.Internal("REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
	}

	requestID := uuid.New().String()
	timestamp := time.Now().UTC().Format("2006-01-02T15:04:05.000Z")

	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("REQUEST-ID", requestID)
	req.Header.Set("TIMESTAMP", timestamp)

	log.Printf("[sendRequest] Headers set - REQUEST-ID: %s, TIMESTAMP: %s, Token length: %d chars",
		requestID, timestamp, len(token))

	log.Printf("[sendRequest] Sending HTTP POST request...")
	resp, err := s.httpClient.Do(req)
	if err != nil {
		log.Printf("[sendRequest] ERROR: HTTP request failed: %v", err)
		return nil, errorsx.Internal("HTTP_DO_FAILED", "failed to call upstream API", nil, err)
	}
	defer resp.Body.Close()

	log.Printf("[sendRequest] Response received with status code: %d", resp.StatusCode)

	respBody, _ := io.ReadAll(resp.Body)

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		log.Printf("[sendRequest] ERROR: API returned non-success status %d, response: %s",
			resp.StatusCode, string(respBody))
		return nil, s.wrapUpstream(resp.StatusCode, respBody, "API")
	}

	log.Printf("[sendRequest] SUCCESS: Request completed, response body length: %d bytes", len(respBody))
	return respBody, nil
}

// ---------------------- CREATE FLOW ----------------------

// create/link_mobile_number
func (s *abhaService) LinkMobileNumber(ctx context.Context, req dtos.LinkMobileRequest, token string) ([]byte, error) {
	log.Printf("[LinkMobileNumber] Starting mobile link for mobile: %s, txnId: %s", req.Mobile, req.TransactionID)

	payload := map[string]interface{}{
		"scope":     []string{"abha-enrol", "mobile-verify"},
		"loginHint": "mobile",
		"loginId":   req.Mobile,
		"txnId":     req.TransactionID,
		"otpSystem": "abdm",
	}

	log.Printf("[LinkMobileNumber] Payload created: %+v", payload)
	log.Printf("[LinkMobileNumber] Sending request to URL: %s", s.cfg.LinkMobileURL)

	response, err := s.sendRequest(ctx, s.cfg.LinkMobileURL, payload, token)
	if err != nil {
		log.Printf("[LinkMobileNumber] ERROR: Mobile link request failed: %v", err)
		return nil, err
	}

	log.Printf("[LinkMobileNumber] SUCCESS: Mobile link completed, response length: %d bytes", len(response))
	return response, nil
}

// create/verify_mobile_otp
func (s *abhaService) VerifyMobileOTP(ctx context.Context, req dtos.VerifyMobileOTPRequest, token string) ([]byte, error) {
	log.Printf("[VerifyMobileOTP] Starting mobile OTP verification for txnId: %s", req.TransactionID)

	// Build payload exactly as required by ABDM (authData.otp.{timeStamp, txnId, otpValue})
	timestamp := time.Now().Format("2006-01-02 15:04:05")
	payload := map[string]interface{}{
		"scope": []string{"abha-enrol", "mobile-verify"},
		"authData": map[string]interface{}{
			"authMethods": []string{"otp"},
			"otp": map[string]interface{}{
				"timeStamp": timestamp,
				"txnId":     req.TransactionID,
				"otpValue":  req.Otp,
			},
		},
	}

	log.Printf("[VerifyMobileOTP] Payload created with timestamp: %s, OTP length: %d", timestamp, len(req.Otp))
	log.Printf("[VerifyMobileOTP] Sending request to URL: %s", s.cfg.VerifyMobileURL)

	response, err := s.sendRequest(ctx, s.cfg.VerifyMobileURL, payload, token)
	if err != nil {
		log.Printf("[VerifyMobileOTP] ERROR: Mobile OTP verification failed: %v", err)
		return nil, err
	}

	log.Printf("[VerifyMobileOTP] SUCCESS: Mobile OTP verification completed, response length: %d bytes", len(response))
	return response, nil
}

// create/address_suggestion
func (s *abhaService) AddressSuggestion(ctx context.Context, req dtos.AddressSuggestionRequest, token string) ([]byte, error) {
	url := s.cfg.AddressSuggestionURL

	log.Printf("[AddressSuggestion] Hitting URL: %s", url)
	log.Printf("[AddressSuggestion] txnId=%s token=%s", req.TransactionID, token)

	httpReq, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		log.Printf("[AddressSuggestion] ERROR building request: %v", err)
		return nil, errorsx.Internal("ADDR_SUGGEST_REQ_BUILD_FAILED", "failed to build request", nil, err)
	}
	httpReq.Header.Set("TRANSACTION_ID", req.TransactionID)
	httpReq.Header.Set("Authorization", "Bearer "+token)
	httpReq.Header.Set("REQUEST-ID", uuid.New().String())

	const abdmTimestampLayout = "2006-01-02T15:04:05"
	timestamp := time.Now().Format(abdmTimestampLayout)
	httpReq.Header.Set("TIMESTAMP", timestamp)

	log.Printf("[AddressSuggestion] Headers => TRANSACTION_ID=%s TIMESTAMP=%s", req.TransactionID, timestamp)

	resp, err := s.httpClient.Do(httpReq)
	if err != nil {
		log.Printf("[AddressSuggestion] ERROR making request: %v", err)
		return nil, errorsx.Internal("ADDR_SUGGEST_HTTP_FAILED", "address suggestion call failed", nil, err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("[AddressSuggestion] ERROR reading body: %v", err)
		return nil, errorsx.Internal("ADDR_SUGGEST_READ_FAILED", "failed to read address suggestion response", nil, err)
	}

	log.Printf("[AddressSuggestion] Status=%d Response=%s", resp.StatusCode, string(body))

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, s.wrapUpstream(resp.StatusCode, body, "ADDRESS_SUGGEST")
	}

	return body, nil
}

// create/enrol_abha_address
func (s *abhaService) EnrolAddress(ctx context.Context, req dtos.EnrolAddressRequest, token string) ([]byte, error) {
	payload := map[string]interface{}{
		"transaction_id": req.TransactionID,
		"abha_address":   req.AbhaAddress,
		"preferred":      1,
	}
	respBytes, err := s.sendRequest(ctx, s.cfg.EnrolAddressURL, payload, token)
	if err != nil {
		return nil, err
	}

	// update abha_number table if possible
	var resp struct {
		TxnId          string `json:"txnId"`
		HealthIdNumber string `json:"healthIdNumber"`
		PreferredAbha  string `json:"preferredAbhaAddress"`
	}
	if json.Unmarshal(respBytes, &resp) == nil {
		abha := &domain.AbhaNumber{
			ABHANumber: resp.HealthIdNumber,
			HealthID:   resp.PreferredAbha,
		}
		_ = s.abhaRepo.SaveAbhaProfile(ctx, abha)
	}
	return respBytes, nil
}

// ---------------------- LOGIN FLOW ----------------------

func (s *abhaService) LoginSendOTP(ctx context.Context, req dtos.SendOtpRequest, token string) ([]byte, error) {
	log.Printf("[LoginSendOTP] Starting login OTP request for type: %s, value: %s, otp_system: %s",
		req.Type, req.Value, req.OtpSystem)

	scope := []string{}
	if req.OtpSystem == "aadhaar" {
		scope = append(scope, "aadhaar-verify")
	} else {
		scope = append(scope, "mobile-verify")
	}
	if req.Type == "abha-address" {
		scope = append([]string{"abha-address-login"}, scope...)
	} else {
		scope = append([]string{"abha-login"}, scope...)
	}

	payload := map[string]interface{}{
		"scope":      scope,
		"type":       req.Type,
		"value":      req.Value,
		"otp_system": req.OtpSystem,
	}

	log.Printf("[LoginSendOTP] Payload created with scope: %v", scope)
	log.Printf("[LoginSendOTP] Sending request to URL: %s", s.cfg.LoginSendOtpURL)

	response, err := s.sendRequest(ctx, s.cfg.LoginSendOtpURL, payload, token)
	if err != nil {
		log.Printf("[LoginSendOTP] ERROR: Login OTP send request failed: %v", err)
		return nil, err
	}

	log.Printf("[LoginSendOTP] SUCCESS: Login OTP sent, response length: %d bytes", len(response))
	return response, nil
}

func (s *abhaService) LoginVerifyOTP(ctx context.Context, req dtos.VerifyOtpRequest, token string) ([]byte, error) {
	log.Printf("[LoginVerifyOTP] Starting login OTP verification for txnId: %s, type: %s, otp_system: %s",
		req.TransactionID, req.Type, req.OtpSystem)

	scope := []string{}
	if req.OtpSystem == "aadhaar" {
		scope = append(scope, "aadhaar-verify")
	} else {
		scope = append(scope, "mobile-verify")
	}
	if req.Type == "abha-address" {
		scope = append([]string{"abha-address-login"}, scope...)
	} else {
		scope = append([]string{"abha-login"}, scope...)
	}

	payload := map[string]interface{}{
		"scope":          scope,
		"transaction_id": req.TransactionID,
		"otp":            req.Otp,
	}

	log.Printf("[LoginVerifyOTP] Payload created with scope: %v, OTP length: %d", scope, len(req.Otp))
	log.Printf("[LoginVerifyOTP] Sending request to URL: %s", s.cfg.LoginVerifyOtpURL)

	respBytes, err := s.sendRequest(ctx, s.cfg.LoginVerifyOtpURL, payload, token)
	if err != nil {
		log.Printf("[LoginVerifyOTP] ERROR: Login OTP verification failed: %v", err)
		return nil, err
	}

	log.Printf("[LoginVerifyOTP] Response received, length: %d bytes", len(respBytes))

	// update tokens in DB
	var out struct {
		TxnId         string `json:"txnId"`
		Token         string `json:"token"`
		RefreshToken  string `json:"refreshToken"`
		ABHANumber    string `json:"ABHANumber"`
		PreferredAbha string `json:"preferredAbhaAddress"`
	}
	if json.Unmarshal(respBytes, &out) == nil {
		log.Printf("[LoginVerifyOTP] Response parsed successfully, ABHA: %s, PreferredAbha: %s",
			out.ABHANumber, out.PreferredAbha)

		abha := &domain.AbhaNumber{
			ABHANumber:   out.ABHANumber,
			HealthID:     out.PreferredAbha,
			AccessToken:  out.Token,
			RefreshToken: out.RefreshToken,
		}

		log.Printf("[LoginVerifyOTP] Attempting to save ABHA profile to database...")
		if saveErr := s.abhaRepo.SaveAbhaProfile(ctx, abha); saveErr != nil {
			log.Printf("[LoginVerifyOTP] WARNING: Failed to save ABHA profile: %v", saveErr)
		} else {
			log.Printf("[LoginVerifyOTP] ABHA profile saved successfully")
		}
	} else {
		log.Printf("[LoginVerifyOTP] WARNING: Failed to parse response for token extraction")
	}

	log.Printf("[LoginVerifyOTP] SUCCESS: Login OTP verification completed")
	return respBytes, nil
}

func (s *abhaService) LoginCheckAuthMethods(ctx context.Context, req dtos.CheckAuthMethodsRequest, token string) ([]byte, error) {
	log.Printf("[LoginCheckAuthMethods] Starting auth methods check for ABHA address: %s", req.AbhaAddress)

	payload := map[string]interface{}{
		"abha_address": req.AbhaAddress,
	}

	log.Printf("[LoginCheckAuthMethods] Payload created: %+v", payload)
	log.Printf("[LoginCheckAuthMethods] Sending request to URL: %s", s.cfg.LoginCheckAuthURL)

	response, err := s.sendRequest(ctx, s.cfg.LoginCheckAuthURL, payload, token)
	if err != nil {
		log.Printf("[LoginCheckAuthMethods] ERROR: Auth methods check failed: %v", err)
		return nil, err
	}

	log.Printf("[LoginCheckAuthMethods] SUCCESS: Auth methods check completed, response length: %d bytes", len(response))
	return response, nil
}

func (s *abhaService) logLoginTxn(ctx context.Context, txn *domain.TransactionLog) {
	go func() {
		if err := s.abhaRepo.InsertLoginTransaction(ctx, txn); err != nil {
			log.Printf("[LoginTxnLog] Failed to insert: %v", err)
		}
	}()
}

/////////////////// -------------------------- profile login logic

func (s *abhaService) ProfileLoginRequestOTP(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error) {
	return s.sendRequestWithABDMHeaders(ctx, s.cfg.ProfileLoginRequestOTPURL, req, token)
}

func (s *abhaService) ProfileLoginVerifyOTP(ctx context.Context, req dtos.ProfileLoginVerifyOTP, token string) ([]byte, error) {
	respBytes, err := s.sendRequestWithABDMHeaders(ctx, s.cfg.ProfileLoginVerifyOTPURL, req, token)
	if err != nil {
		return nil, err
	}

	// Try to persist tokens if present
	var out dtos.ProfileLoginVerifyResponse
	if json.Unmarshal(respBytes, &out) == nil && (out.Token != "" || out.RefreshToken != "") {
		abha := &domain.AbhaNumber{
			ABHANumber:       out.ABHANumber,
			HealthID:         out.PreferredAbhaAddress,
			AccessToken:      out.Token,
			RefreshToken:     out.RefreshToken,
			LastModifiedDate: time.Now().UTC(),
		}

		mask := func(s string) string {
			if len(s) <= 6 {
				return "****"
			}
			return s[:3] + "****" + s[len(s)-3:]
		}

		log.Printf("ABHA created: number=%s healthID=%s accessToken=%s refreshToken=%s lastModified=%s",
			abha.ABHANumber,
			abha.HealthID,
			mask(abha.AccessToken),
			mask(abha.RefreshToken),
			abha.LastModifiedDate.Format(time.RFC3339),
		)
		// _ = s.abhaRepo.SaveAbhaProfile(ctx, abha)
	}
	return respBytes, nil
}

// Same as sendRequest, but TIMESTAMP must be RFC3339 with millis + 'Z' (what your cURLs show)
func (s *abhaService) sendRequestWithABDMHeaders(ctx context.Context, url string, payload interface{}, token string) ([]byte, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, errorsx.Internal("REQ_MARSHAL_FAILED", "failed to marshal request payload", nil, err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBuffer(body))
	if err != nil {
		return nil, errorsx.Internal("REQ_BUILD_FAILED", "failed to create HTTP request", nil, err)
	}

	// NOTE: token is *single* Bearer
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("REQUEST-ID", uuid.New().String())
	req.Header.Set("TIMESTAMP", time.Now().UTC().Format(time.RFC3339Nano)) // e.g. 2025-09-01T06:57:40.811Z

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, errorsx.Internal("HTTP_DO_FAILED", "failed to call upstream API", nil, err)
	}
	defer resp.Body.Close()

	b, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, s.wrapUpstream(resp.StatusCode, b, "API")
	}
	return b, nil
}

// Verify Aadhaar OTP with ABDM, create Individual in HCM, then persist ABHA profile.
func (s *abhaService) VerifyAadhaarOtpAndCreateIndividualV2(ctx context.Context, in dtos.VerifyAndCreateV2Input) ([]byte, error) {
	log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] Start | txnId=%s tenant=%s clientRef=%s", in.TxnId, in.TenantId, in.ClientReferenceId)

	// ---- 1) Validate inputs
	if ok := regexp.MustCompile(`^\d{6}$`).MatchString(in.Otp); !ok {
		return nil, errorsx.BadRequest("INVALID_OTP_FORMAT", "otp must be 6 digits", nil, nil)
	}
	if in.Mobile != "" && !regexp.MustCompile(`^\d{10}$`).MatchString(in.Mobile) {
		return nil, errorsx.BadRequest("INVALID_MOBILE_FORMAT", "mobile must be 10 digits", nil, nil)
	}
	if strings.TrimSpace(in.TxnId) == "" {
		return nil, errorsx.BadRequest("MISSING_TXN_ID", "transaction id is required", nil, nil)
	}
	if strings.TrimSpace(in.TenantId) == "" || strings.TrimSpace(in.ClientReferenceId) == "" ||
		strings.TrimSpace(in.HcmAuthToken) == "" || strings.TrimSpace(in.UserUUID) == "" || in.UserID == 0 {
		return nil, errorsx.BadRequest("MISSING_HCM_INPUTS", "required HCM inputs missing", nil, nil)
	}

	// ---- 2) Encrypt OTP (ABDM requires RSA-OAEP SHA-1)
	pub, err := s.FetchPublicKey(ctx)
	if err != nil {
		return nil, errorsx.Internal("PUBLIC_KEY_FETCH_FAILED", "failed to fetch public key", nil, err)
	}
	encOTP, err := s.EncryptData(ctx, pub, in.Otp)
	if err != nil {
		return nil, errorsx.Internal("OTP_ENCRYPT_FAILED", "failed to encrypt otp", nil, err)
	}

	// ---- 3) Build ABDM verify payload
	var verifyReq dtos.EnrolByAadhaarWithOTPRequestV2
	verifyReq.Consent.Code = "abha-enrollment"
	verifyReq.Consent.Version = "1.4"
	verifyReq.AuthData.AuthMethods = []string{"otp"}
	verifyReq.AuthData.OTP.TimeStamp = time.Now().Format("2006-01-02 15:04:05") // ABDM expects this format
	verifyReq.AuthData.OTP.TxnId = in.TxnId
	verifyReq.AuthData.OTP.OtpValue = encOTP
	if in.Mobile != "" {
		verifyReq.AuthData.OTP.Mobile = in.Mobile // sandbox sometimes needs plaintext mobile
	}

	// ---- 4) ABDM verify call
	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		return nil, errorsx.Internal("ABDM_TOKEN_FAILED", "failed to fetch ABDM token", nil, err)
	}
	verifyRespBytes, err := s.sendRequest(ctx, s.cfg.EnrolByAadhaarURL, verifyReq, abdmToken)
	if err != nil {
		if strings.Contains(err.Error(), "INVALID_OTP") {
			return nil, errorsx.BadRequest("ABDM_INVALID_OTP", "invalid otp", nil, err)
		}
		return nil, errorsx.UpstreamFailed("ABDM_VERIFY_ERROR", "ABDM verify call failed", http.StatusBadGateway, nil, err)
	}

	// ---- 5) Parse ABDM response
	var verifyOut dtos.EnrolWithOTPResponseV2
	if uErr := json.Unmarshal(verifyRespBytes, &verifyOut); uErr != nil {
		return nil, errorsx.Internal("ABDM_VERIFY_PARSE_ERROR", "failed to parse ABDM verify response", nil, uErr)
	}

	abhaNumber := strings.TrimSpace(verifyOut.ABHAProfile.ABHANumber)
	if abhaNumber == "" {
		abhaNumber = strings.TrimSpace(verifyOut.ABHAProfile.HealthIdNumber)
	}
	if abhaNumber == "" {
		return nil, errorsx.UpstreamFailed("ABDM_VERIFY_ERROR", "missing ABHA number in response", http.StatusBadGateway, nil, nil)
	}

	// --- fetch Aadhaar linked to this txn and decrypt it for identifiers
	aadhaarPlain := ""
	if enc, err := s.abhaRepo.GetEncryptedAadhaarByTxn(ctx, in.TxnId); err == nil && strings.TrimSpace(enc) != "" {
		if dec, derr := utils.DecryptFromDB(enc); derr == nil && regexp.MustCompile(`^\d{12}$`).MatchString(dec) {
			aadhaarPlain = dec
		} else if derr != nil {
			log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: Aadhaar decrypt failed for txn=%s: %v", in.TxnId, derr)
		} else {
			log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: Aadhaar format invalid after decrypt for txn=%s", in.TxnId)
		}
	} else if err != nil {
		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: GetEncryptedAadhaarByTxn(%s) failed: %v", in.TxnId, err)
	}

	// Convenience getters & normalizers
	trim := func(s string) string { return strings.TrimSpace(s) }
	is10 := func(s string) bool { return regexp.MustCompile(`^\d{10}$`).MatchString(s) }
	addIfNonEmpty := func(m map[string]any, k, v string) {
		if strings.TrimSpace(v) != "" {
			m[k] = v
		}
	}
	// formatDob: ABHA "dd-MM-yyyy" -> HCM "dd/MM/yyyy"
	safeDob := trim(formatDob(verifyOut.ABHAProfile.Dob))
	safeGender := trim(normalizeGender(verifyOut.ABHAProfile.Gender))

	// Names (no placeholders)
	firstName := trim(verifyOut.ABHAProfile.FirstName)
	middleName := trim(verifyOut.ABHAProfile.MiddleName)
	lastName := trim(verifyOut.ABHAProfile.LastName)
	if firstName == "" {
		return nil, errorsx.UpstreamFailed("ABDM_VERIFY_ERROR", "missing first name in ABHA profile", http.StatusBadGateway, nil, nil)
	}

	// Optional fields
	emailStr := ""
	if verifyOut.ABHAProfile.Email != nil {
		emailStr = trim(*verifyOut.ABHAProfile.Email)
	}
	mobileStr := trim(verifyOut.ABHAProfile.Mobile)
	addressLine1 := trim(verifyOut.ABHAProfile.Address)
	pincode := trim(verifyOut.ABHAProfile.PinCode)

	healthID := ""
	if len(verifyOut.ABHAProfile.PhrAddress) > 0 {
		healthID = trim(verifyOut.ABHAProfile.PhrAddress[0])
	}

	// --- build identifiers slice dynamically (no placeholders)
	identifiers := make([]any, 0, 3)
	if aadhaarPlain != "" {
		identifiers = append(identifiers, map[string]any{
			"identifierType": "AADHAAR",
			"identifierId":   aadhaarPlain,
		})
	}
	identifiers = append(identifiers, map[string]any{
		"identifierType": "ABHA",
		"identifierId":   abhaNumber, // keep hyphenation as-is for display
	})

	// ---- Build HCM Individual payload: include only non-empty fields
	nameObj := map[string]any{"givenName": firstName}
	addIfNonEmpty(nameObj, "familyName", lastName)
	addIfNonEmpty(nameObj, "otherNames", middleName)

	indObj := map[string]any{
		"tenantId":          in.TenantId,
		"clientReferenceId": in.ClientReferenceId,
		"name":              nameObj,
		"rowVersion":        1,
		"isSystemUser":      false,
		"identifiers":       identifiers,
	}

	// Optional Individual fields (added only if non-empty/valid)
	if safeDob != "" {
		indObj["dateOfBirth"] = safeDob
	}
	if safeGender != "" {
		indObj["gender"] = safeGender
	}
	if is10(mobileStr) {
		indObj["mobileNumber"] = mobileStr
	}
	if emailStr != "" {
		indObj["email"] = emailStr
	}

	// Address: include only if we have at least one meaningful field
	addresses := make([]any, 0, 1)
	if addressLine1 != "" || pincode != "" || strings.TrimSpace(in.LocalityCode) != "" {
		addr := map[string]any{
			"clientReferenceId": in.ClientReferenceId,
			"tenantId":          "default",
			"type":              "OTHER",
		}
		addIfNonEmpty(addr, "addressLine1", addressLine1)
		addIfNonEmpty(addr, "pincode", pincode)
		if lc := strings.TrimSpace(in.LocalityCode); lc != "" {
			addr["locality"] = map[string]any{"code": lc}
		}
		addresses = append(addresses, addr)
	}
	if len(addresses) > 0 {
		indObj["address"] = addresses
	}

	// ---- Request wrapper
	individualBody := map[string]any{
		"RequestInfo": map[string]any{
			"apiId":     "dev",
			"msgId":     "Create Individual",
			"authToken": in.HcmAuthToken,
			"userInfo":  map[string]any{"id": in.UserID, "uuid": in.UserUUID},
		},
		"Individual": indObj,
	}

	// ---- 7) Create Individual in HCM
	reqBytes, err := json.Marshal(individualBody)
	if err != nil {
		return nil, errorsx.Internal("INDIVIDUAL_PAYLOAD_MARSHAL_FAILED", "failed to marshal individual payload", nil, err)
	}

	indURL := s.cfg.IndividualCreateURL
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, indURL, bytes.NewReader(reqBytes))
	if err != nil {
		return nil, errorsx.Internal("INDIVIDUAL_HTTP_REQ_FAILED", "failed to build individual request", nil, err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("REQUEST-ID", uuid.New().String())

	resp, err := s.httpClient.Do(httpReq)
	if err != nil {
		return nil, errorsx.Internal("INDIVIDUAL_CREATE_CALL_FAILED", "failed to call individual create", nil, err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, errorsx.Internal("INDIVIDUAL_RESP_READ_FAILED", "failed to read individual response", nil, err)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, s.wrapUpstream(resp.StatusCode, respBody, "INDIVIDUAL_CREATE_FAILED")
	}

	// ---- 8) Parse Individual response and persist ABHA profile
	var indOut dtos.IndividualCreateResponse
	if err := json.Unmarshal(respBody, &indOut); err != nil {
		return nil, errorsx.Internal("INDIVIDUAL_PARSE_FAILED", "failed to parse individual response", nil, err)
	}
	if strings.TrimSpace(indOut.Individual.Id) == "" {
		return nil, errorsx.Internal("INDIVIDUAL_ID_MISSING_IN_RESPONSE", "missing id in individual response", nil, nil)
	}

	// Use Individual.Id as external_id if it's a valid UUID; else generate one.
	externalID := uuid.New().String()
	if _, err := uuid.Parse(indOut.Individual.Id); err == nil {
		externalID = indOut.Individual.Id
	}

	// Business id for the transaction table
	hcmBusinessID := strings.TrimSpace(indOut.Individual.IndividualId)
	if hcmBusinessID == "" {
		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: individualId missing in response; falling back to id=%s", indOut.Individual.Id)
		hcmBusinessID = indOut.Individual.Id
	}

	now := time.Now().UTC()
	emailForProfile := emailStr
	mobileForProfile := mobileStr

	abhaProfile := &domain.AbhaNumber{
		ExternalID:       externalID,
		ABHANumber:       abhaNumber,
		HealthID:         healthID,
		AccessToken:      verifyOut.Tokens.Token,
		RefreshToken:     verifyOut.Tokens.RefreshToken,
		FirstName:        verifyOut.ABHAProfile.FirstName,
		MiddleName:       verifyOut.ABHAProfile.MiddleName,
		LastName:         verifyOut.ABHAProfile.LastName,
		Name:             strings.TrimSpace(verifyOut.ABHAProfile.FirstName + " " + verifyOut.ABHAProfile.LastName),
		Gender:           verifyOut.ABHAProfile.Gender,
		DateOfBirth:      verifyOut.ABHAProfile.Dob,
		Email:            emailForProfile,
		Mobile:           mobileForProfile,
		Address:          verifyOut.ABHAProfile.Address,
		State:            verifyOut.ABHAProfile.StateCode,
		District:         verifyOut.ABHAProfile.DistrictCode,
		Pincode:          verifyOut.ABHAProfile.PinCode,
		ProfilePhoto:     verifyOut.ABHAProfile.Photo,
		New:              true,
		CreatedBy:        0,
		LastModifiedBy:   0,
		CreatedDate:      now,
		LastModifiedDate: now,
	}

	ensureExternalID(abhaProfile)

	if err := s.abhaRepo.SaveAbhaProfile(ctx, abhaProfile); err != nil {
		return nil, errorsx.Internal("ABHA_SAVE_FAILED", "failed to save ABHA profile", nil, err)
	}

	log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] Success | ABHA=%s ExternalID=%s", abhaNumber, abhaProfile.ExternalID)

	// Update transaction record (best-effort)
	if err := s.abhaRepo.UpdateTxnOnVerify(ctx, in.TxnId, hcmBusinessID, abhaNumber, "system"); err != nil {
		log.Printf("[VerifyAadhaarOtpAndCreateIndividualV2] WARN: UpdateTxnOnVerify failed: %v", err)
	}

	out := map[string]any{
		"abhaNumber":   abhaNumber,
		"individualId": hcmBusinessID,
		"hcmResponse":  json.RawMessage(respBody),
	}
	final, mErr := json.Marshal(out)
	if mErr != nil {
		return respBody, nil
	}
	return final, nil
}

// -------------- helpers --------------

func fallback(v, def string) string {
	if strings.TrimSpace(v) == "" {
		return def
	}
	return v
}

// ABDM sometimes gives YYYY-MM-DD; HCM expects DD/MM/YYYY in your sample.
func formatDob(s string) string {
	t, err := time.Parse("2006-01-02", s)
	if err == nil {
		return t.Format("02/01/2006")
	}
	t, err = time.Parse("02-01-2006", s)
	if err == nil {
		return t.Format("02/01/2006")
	}
	return s
}

func normalizeGender(g string) string {
	g = strings.ToUpper(strings.TrimSpace(g))
	switch g {
	case "M", "MALE":
		return "MALE"
	case "F", "FEMALE":
		return "FEMALE"
	case "O", "OTHER", "OTHERS":
		return "OTHER"
	default:
		return "MALE"
	}
}

// ---- helpers (keep in your file) ----
func ensureExternalID(a *domain.AbhaNumber) {
	if strings.TrimSpace(a.ExternalID) == "" {
		a.ExternalID = uuid.New().String()
	}
}

func (s *abhaService) ResolveAadhaarFromABHA(ctx context.Context, abha string) (string, error) {
	abhaCanon, ok := utils.CanonicalizeABHA(abha)
	if !ok {
		return "", fmt.Errorf("INVALID_ABHA_FORMAT")
	}
	enc, err := s.abhaRepo.GetEncryptedAadhaarByABHA(ctx, abhaCanon)
	if err != nil {
		return "", fmt.Errorf("ABHA_NOT_FOUND_OR_NO_LINKED_AADHAAR: %w", err)
	}
	if strings.TrimSpace(enc) == "" {
		return "", fmt.Errorf("LINKED_AADHAAR_MISSING")
	}
	plain, derr := utils.DecryptFromDB(enc)
	if derr != nil {
		return "", fmt.Errorf("AADHAAR_DECRYPT_FAILED: %w", derr)
	}
	if !regexp.MustCompile(`^\d{12}$`).MatchString(plain) {
		return "", fmt.Errorf("AADHAAR_FORMAT_INVALID")
	}
	return plain, nil
}

//// ------------------------------ card download v2

func (s *abhaService) FetchAbhaCardByTypeV2(
	ctx context.Context,
	abhaNumber, cardType, token, refreshToken string,
) ([]byte, string, error) {

	log.Printf("[FetchAbhaCardByTypeV2] Start, ABHA: %s, cardType: %s", abhaNumber, cardType)

	// Map endpoint
	var endpoint string
	switch cardType {
	case "getCard":
		endpoint = s.cfg.ABHACardEndpoints.GetCard
	case "getSvgCard":
		endpoint = s.cfg.ABHACardEndpoints.GetSvgCard
	case "getPngCard":
		endpoint = s.cfg.ABHACardEndpoints.GetPngCard
	default:
		return nil, "", errorsx.BadRequest("INVALID_CARD_TYPE", fmt.Sprintf("unsupported card type: %s", cardType), nil, nil)
	}
	log.Printf("[FetchAbhaCardByTypeV2] Endpoint resolved: %s", endpoint)

	// ABDM Authorization token
	log.Printf("[FetchAbhaCardByTypeV2] Fetching ABDM auth token...")
	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		return nil, "", errorsx.Internal("AUTH_TOKEN_FETCH_FAILED", "failed to fetch ABDM token", nil, err)
	}
	log.Printf("[FetchAbhaCardByTypeV2] ABDM token fetched, length=%d", len(abdmToken))

	fetchImage := func(xToken string) ([]byte, string, error) {
		log.Printf("[FetchAbhaCardByTypeV2:fetchImage] GET %s (X-Token len=%d)", endpoint, len(xToken))

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
		if err != nil {
			return nil, "", errorsx.Internal("REQ_BUILD_FAILED", "failed to build request", nil, err)
		}
		req.Header.Set("Accept", "*/*")
		req.Header.Set("Accept-Language", "en-US")
		req.Header.Set("X-Token", "Bearer "+xToken) // caller-provided token(s)
		req.Header.Set("Authorization", "Bearer "+abdmToken)
		req.Header.Set("TIMESTAMP", time.Now().UTC().Format(time.RFC3339Nano))
		req.Header.Set("REQUEST-ID", newRequestID())

		resp, err := s.httpClient.Do(req)
		if err != nil {
			return nil, "", errorsx.Internal("HTTP_DO_FAILED", "failed to call card endpoint", nil, err)
		}
		defer resp.Body.Close()

		data, readErr := io.ReadAll(resp.Body)
		if readErr != nil {
			return nil, "", errorsx.Internal("READ_BODY_FAILED", "failed to read response", nil, readErr)
		}

		if resp.StatusCode != http.StatusOK {
			return nil, "", s.wrapUpstream(resp.StatusCode, data, "ABHA_CARD_FETCH")
		}

		detected := http.DetectContentType(data)

		// SVG case: attempt to extract embedded image
		if detected == "text/xml" || detected == "image/svg+xml" {
			if decoded, actualType, derr := utils.ExtractImageFromSVG(data); derr == nil {
				return decoded, actualType, nil
			}
			// If extraction fails, continue with SVG bytes
			return data, "image/svg+xml", nil
		}

		return data, detected, nil
	}

	// Try with access token
	if token != "" {
		if img, ctype, err := fetchImage(token); err == nil {
			log.Printf("[FetchAbhaCardByTypeV2] SUCCESS via access token")
			return img, ctype, nil
		}
		log.Printf("[FetchAbhaCardByTypeV2] Access token attempt failed")
	}

	// Retry with refresh token
	if refreshToken != "" {
		if img, ctype, err := fetchImage(refreshToken); err == nil {
			log.Printf("[FetchAbhaCardByTypeV2] SUCCESS via refresh token")
			return img, ctype, nil
		}
		return nil, "", errorsx.UpstreamFailed("ABHA_CARD_FETCH_FAILED", "failed with provided tokens", http.StatusBadGateway, nil, err)
	}

	return nil, "", errorsx.BadRequest("MISSING_TOKEN", "no usable token provided (token/refresh_token both empty)", nil, nil)
}

func newRequestID() string {
	// prefer uuid; fallback to 16 random bytes
	id, err := uuid.NewRandom()
	if err == nil {
		return id.String()
	}
	var b [16]byte
	_, _ = rand.Read(b[:])
	return fmt.Sprintf("%x", b[:])
}
