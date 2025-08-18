package utils

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"digit-abdm/configs"
)

type ABDMTokenResponse struct {
	AccessToken string `json:"accessToken"`
	ExpiresIn   int    `json:"expiresIn"`
	TokenType   string `json:"tokenType"`
}

// FetchABDMToken makes an API request to fetch the ABDM access token
func FetchABDMToken(ctx context.Context, cfg *configs.Config) (string, error) {
	reqBody := map[string]string{
		"clientId":     cfg.AbdmClientID,
		"clientSecret": cfg.AbdmClientSecret,
		"grantType":    "client_credentials",
	}

	bodyBytes, _ := json.Marshal(reqBody)

	req, err := http.NewRequestWithContext(ctx, "POST", cfg.AbdmAuthURL, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("accept", "application/json")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", errors.New("failed to fetch token, status: " + resp.Status)
	}

	var tokenResp ABDMTokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&tokenResp); err != nil {
		return "", err
	}

	return tokenResp.AccessToken, nil
}
