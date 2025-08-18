package ports

import (
	"context"
	"crypto/rsa"

	"digit-abdm/pkg/dtos"
)

type ABHAService interface {
	FetchPublicKey(ctx context.Context) (*rsa.PublicKey, error)
	EncryptData(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error)
	SendOTPRequest(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error)
	SendEnrolRequestWithOTP(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error)
	FetchAbhaCard(ctx context.Context, abhaNumber string) ([]byte, error)
	FetchAbhaCardByType(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error)
	FetchQRCodeByABHANumber(ctx context.Context, abhaNumber string) ([]byte, error)
	// ----------------------

	// SendAadhaarOTP(ctx context.Context, req dtos.SendAadhaarOTPRequest, token string) ([]byte, error)
	// VerifyAadhaarOTP(ctx context.Context, req dtos.VerifyAadhaarOTPRequest, token string) ([]byte, error)
	// LinkMobile(ctx context.Context, req dtos.LinkMobileRequest, token string) ([]byte, error)
	// VerifyMobileOTP(ctx context.Context, req dtos.VerifyMobileOTPRequest, token string) ([]byte, error)
	// AddressSuggestion(ctx context.Context, req dtos.AddressSuggestionRequest, token string) ([]byte, error)
	// EnrolAddress(ctx context.Context, req dtos.EnrolAddressRequest, token string) ([]byte, error)
	// SendLoginOTP(ctx context.Context, req dtos.SendLoginOTPRequest, token string) ([]byte, error)
	// VerifyLoginOTP(ctx context.Context, req dtos.VerifyLoginOTPRequest, token string) ([]byte, error)
}
