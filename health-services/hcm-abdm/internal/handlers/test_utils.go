package handlers

import (
	"context"
	
	"digit-abdm/configs"
)

// MockFetchABDMToken is a test helper that can be used to mock utils.FetchABDMToken
var MockFetchABDMToken func(ctx context.Context, cfg *configs.Config) (string, error)

// TestTokenProvider returns a test token for mocking purposes
func TestTokenProvider(ctx context.Context, cfg *configs.Config) (string, error) {
	if MockFetchABDMToken != nil {
		return MockFetchABDMToken(ctx, cfg)
	}
	return "test-auth-token", nil
}