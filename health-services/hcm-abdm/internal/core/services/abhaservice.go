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
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"

	"github.com/google/uuid"

	"digit-abdm/configs"
	"digit-abdm/internal/core/domain"
	"digit-abdm/internal/core/ports"
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

// FetchPublicKey retrieves RSA public key
func (s *abhaService) FetchPublicKey(ctx context.Context) (*rsa.PublicKey, error) {
	req, _ := http.NewRequestWithContext(ctx, "GET", s.cfg.PublicKeyURL, nil)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}

	defer resp.Body.Close()

	certPEM, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	block, _ := pem.Decode(certPEM)
	if block == nil {
		return nil, errors.New("failed to parse PEM block")
	}

	pubKey, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return nil, err
	}

	rsaPubKey, ok := pubKey.(*rsa.PublicKey)
	if !ok {
		return nil, errors.New("not RSA public key")
	}

	return rsaPubKey, nil
}

func (s *abhaService) EncryptData(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
	// Use SHA-1 (per ABDM / DevGlan config)
	hash := sha1.New()

	encryptedBytes, err := rsa.EncryptOAEP(hash, rand.Reader, publicKey, []byte(data), nil)
	if err != nil {
		return "", err
	}

	// Encode to base64
	encoded := base64.StdEncoding.EncodeToString(encryptedBytes)
	return encoded, nil
}

func (s *abhaService) SendOTPRequest(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error) {
	return s.sendRequest(ctx, s.cfg.OTPRequestURL, req, token)
}

func (s *abhaService) SendEnrolRequestWithOTP(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error) {
	// return s.sendRequest(ctx, s.cfg.EnrolByAadhaarURL, req, token)
	respBody, err := s.sendRequest(ctx, s.cfg.EnrolByAadhaarURL, req, token)
	if err != nil {
		return nil, err
	}

	// Parse response
	var enrolResp dtos.EnrolWithOTPResponse
	if err := json.Unmarshal(respBody, &enrolResp); err != nil {
		return nil, fmt.Errorf("failed to parse enrolment response: %w", err)
	}

	fmt.Printf("res: %+v\n", enrolResp)
	// Map response to model
	profile := enrolResp.ABHAProfile
	tokens := enrolResp.Tokens
	//txnId := enrolResp.TxnId
	now := time.Now().UTC()

	fmt.Printf("ABHA profile: %+v\n", profile)
	fmt.Printf("Tokens: %+v\n", tokens)
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
	}

	// Save to DB
	if err := s.abhaRepo.SaveAbhaProfile(ctx, abha); err != nil {
		return nil, fmt.Errorf("failed to save ABHA profile: %w", err)
	}

	return respBody, nil
}

// func (s *abhaService) sendRequest(ctx context.Context, url string, payload interface{}, token string) ([]byte, error) {
// 	body, _ := json.Marshal(payload)

// 	httpReq, _ := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(body))
// 	httpReq.Header.Set("Authorization", "Bearer "+token)
// 	httpReq.Header.Set("Content-Type", "application/json")
// 	httpReq.Header.Set("REQUEST-ID", uuid.New().String())
// 	httpReq.Header.Set("TIMESTAMP", "2025-07-25T06:05:03.113Z") // ideally generate dynamically

// 	resp, err := s.httpClient.Do(httpReq)
// 	if err != nil {
// 		return nil, err
// 	}
// 	defer resp.Body.Close()

// 	return io.ReadAll(resp.Body)
// }

func (s *abhaService) FetchAbhaCard(ctx context.Context, abhaNumber string) ([]byte, error) {
	// Get access token from DB
	abha, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
	if err != nil {
		return nil, fmt.Errorf("failed to get ABHA profile: %w", err)
	}
	if abha.AccessToken == "" {
		return nil, fmt.Errorf("access token not found for ABHA number")
	}

	// Get auth token
	authToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch ABDM auth token: %w", err)
	}

	// Make HTTP request to getCard
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://healthidsbx.abdm.gov.in/api/v1/account/getCard", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("accept", "*/*")
	req.Header.Set("Accept-Language", "en-US")
	req.Header.Set("X-Token", "Bearer "+abha.AccessToken)
	req.Header.Set("Authorization", "Bearer "+authToken)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to call getCard: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("getCard failed: %s", string(bodyBytes))
	}

	return io.ReadAll(resp.Body)
}

func (s *abhaService) FetchAbhaCardByType(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
	abhaData, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
	if err != nil || abhaData.AccessToken == "" {
		return nil, "", fmt.Errorf("abha not found or access token missing")
	}
	log.Printf("[Service] Using ABHA: %s", abhaData.ABHANumber)

	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		return nil, "", fmt.Errorf("failed to fetch ABDM token: %w", err)
	}
	log.Printf("[Service] Got ABDM token")

	var endpoint string
	switch cardType {
	case "getCard":
		endpoint = s.cfg.ABHACardEndpoints.GetCard
	case "getSvgCard":
		endpoint = s.cfg.ABHACardEndpoints.GetSvgCard
	case "getPngCard":
		endpoint = s.cfg.ABHACardEndpoints.GetPngCard
	default:
		return nil, "", fmt.Errorf("unsupported card type: %s", cardType)
	}
	log.Printf("[Service] Fetching card from endpoint: %s", endpoint)

	fetchImage := func(token string) ([]byte, string, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
		if err != nil {
			return nil, "", err
		}
		req.Header.Set("Accept", "*/*")
		req.Header.Set("Accept-Language", "en-US")
		req.Header.Set("X-Token", "Bearer "+token)
		req.Header.Set("Authorization", "Bearer "+abdmToken)

		resp, err := s.httpClient.Do(req)
		if err != nil {
			return nil, "", err
		}
		defer resp.Body.Close()

		data, err := io.ReadAll(resp.Body)
		if err != nil {
			return nil, "", err
		}

		if resp.StatusCode != http.StatusOK {
			return nil, "", fmt.Errorf("failed with status %d: %s", resp.StatusCode, string(data))
		}

		// Auto-detect content type
		detectedType := http.DetectContentType(data)
		log.Printf("[Service] Detected content type: %s", detectedType)

		// SVG case: attempt to extract embedded image
		if detectedType == "text/xml" || detectedType == "image/svg+xml" {
			decoded, actualType, decodeErr := utils.ExtractImageFromSVG(data)
			if decodeErr != nil {
				log.Printf("[Service] SVG base64 extraction failed: %v", decodeErr)
			} else {
				log.Printf("[Service] Extracted base64 image from SVG: %s", actualType)
				return decoded, actualType, nil
			}
		}

		return data, detectedType, nil
	}

	// Try with access token
	imageData, detectedType, err := fetchImage(abhaData.AccessToken)
	if err == nil {
		return imageData, detectedType, nil
	}

	log.Printf("[Service] Access token failed: %v", err)

	// Retry with refresh token
	if abhaData.RefreshToken != "" {
		log.Printf("[Service] Retrying with refresh token...")
		imageData, detectedType, err2 := fetchImage(abhaData.RefreshToken)
		if err2 == nil {
			return imageData, detectedType, nil
		}
		log.Printf("[Service] Refresh token also failed: %v", err2)
		return nil, "", fmt.Errorf("failed with both access and refresh tokens: %v | %v", err, err2)
	}

	return nil, "", fmt.Errorf("failed with access token: %v and no refresh token available", err)
}

// helper to safely slice long token logs
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func (s *abhaService) FetchQRCodeByABHANumber(ctx context.Context, abhaNumber string) ([]byte, error) {

	abhaData, err := s.abhaRepo.GetTokenByABHANumber(ctx, abhaNumber)
	if err != nil || abhaData.AccessToken == "" {
		return nil, fmt.Errorf("abha not found or access token missing")
	}

	abdmToken, err := utils.FetchABDMToken(ctx, s.cfg)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch ABDM token: %w", err)
	}

	endpoint := s.cfg.QRCode
	log.Printf("[Service] QR endpoint: %s", endpoint)

	// Reusable fetch function
	fetchQR := func(token string) ([]byte, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
		if err != nil {
			return nil, err
		}

		req.Header.Set("Accept", "*/*")
		req.Header.Set("Accept-Language", "en-US")
		req.Header.Set("X-Token", "Bearer "+token)
		req.Header.Set("Authorization", "Bearer "+abdmToken)

		resp, err := s.httpClient.Do(req)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return nil, err
		}

		if resp.StatusCode != http.StatusOK {
			return nil, fmt.Errorf("QR fetch failed: %s", string(body))
		}

		return body, nil
	}

	imageData, err := fetchQR(abhaData.AccessToken)
	if err == nil {
		return imageData, nil
	}

	log.Printf("[Service] Access token failed, retrying with refresh token: %v", err)

	if abhaData.RefreshToken != "" {
		imageData, err2 := fetchQR(abhaData.RefreshToken)
		if err2 == nil {
			return imageData, nil
		}
		log.Printf("[Service] Refresh token failed too: %v", err2)
		return nil, fmt.Errorf("both access and refresh token failed: %v | %v", err, err2)
	}

	return nil, fmt.Errorf("access token failed: %v and no refresh token available", err)
}

func (s *abhaService) sendRequest(ctx context.Context, url string, payload interface{}, token string) ([]byte, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(body))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("REQUEST-ID", uuid.New().String())
	req.Header.Set("TIMESTAMP", time.Now().UTC().Format("2006-01-02T15:04:05.000Z"))

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API returned status %d: %s", resp.StatusCode, string(respBody))
	}

	return io.ReadAll(resp.Body)
}

//
//func (s *abhaService) logTransaction(ctx context.Context, log *domain.TransactionLog) {
//	go func() {
//		if err := s.abhaRepo.InsertTransactionLog(ctx, log); err != nil {
//			log.Printf("[TransactionLog] Failed to insert: %v", err)
//		}
//	}()
//}
//
//func toJSONB(v interface{}) json.RawMessage {
//	b, _ := json.Marshal(v)
//	return b
//}
//
//func getErrString(err error) string {
//	if err != nil {
//		return err.Error()
//	}
//	return ""
//}
//
//func getStatusCode(err error) int {
//	if err != nil {
//		return 500
//	}
//	return 200
//}
