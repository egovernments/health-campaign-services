package handlers

import (
	"bytes"
	"context"
	"crypto/rsa"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"digit-abdm/configs"
	"digit-abdm/pkg/dtos"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// MockABHAService implements ports.ABHAService for testing
type MockABHAService struct {
	FetchPublicKeyFunc                           func(ctx context.Context) (*rsa.PublicKey, error)
	EncryptDataFunc                              func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error)
	SendOTPRequestFunc                           func(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error)
	SendEnrolRequestWithOTPFunc                  func(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error)
	FetchAbhaCardByTypeFunc                      func(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error)
	ProfileLoginRequestOTPFunc                   func(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error)
	ProfileLoginVerifyOTPFunc                    func(ctx context.Context, req dtos.ProfileLoginVerifyOTP, token string) ([]byte, error)
	RecordAadhaarTxnOnOtpFunc                    func(ctx context.Context, tenantID, aadhaarPlain, txnID string) error
	ResolveAadhaarFromABHAFunc                   func(ctx context.Context, abha string) (string, error)
	VerifyAadhaarOtpAndCreateIndividualV2Func    func(ctx context.Context, in dtos.VerifyAndCreateV2Input) ([]byte, error)
	FetchAbhaCardFunc                            func(ctx context.Context, abhaNumber string) ([]byte, error)
	FetchQRCodeByABHANumberFunc                  func(ctx context.Context, abhaNumber string) ([]byte, error)
	LinkMobileNumberFunc                         func(ctx context.Context, req dtos.LinkMobileRequest, token string) ([]byte, error)
	VerifyMobileOTPFunc                          func(ctx context.Context, req dtos.VerifyMobileOTPRequest, token string) ([]byte, error)
	AddressSuggestionFunc                        func(ctx context.Context, req dtos.AddressSuggestionRequest, token string) ([]byte, error)
	EnrolAddressFunc                             func(ctx context.Context, req dtos.EnrolAddressRequest, token string) ([]byte, error)
	LoginSendOTPFunc                             func(ctx context.Context, req dtos.SendOtpRequest, token string) ([]byte, error)
	LoginVerifyOTPFunc                           func(ctx context.Context, req dtos.VerifyOtpRequest, token string) ([]byte, error)
	LoginCheckAuthMethodsFunc                    func(ctx context.Context, req dtos.CheckAuthMethodsRequest, token string) ([]byte, error)
}

func (m *MockABHAService) FetchPublicKey(ctx context.Context) (*rsa.PublicKey, error) {
	if m.FetchPublicKeyFunc != nil {
		return m.FetchPublicKeyFunc(ctx)
	}
	// Return a dummy public key for testing
	return &rsa.PublicKey{}, nil
}

func (m *MockABHAService) EncryptData(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
	if m.EncryptDataFunc != nil {
		return m.EncryptDataFunc(ctx, publicKey, data)
	}
	return "encrypted_" + data, nil
}

func (m *MockABHAService) SendOTPRequest(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error) {
	if m.SendOTPRequestFunc != nil {
		return m.SendOTPRequestFunc(ctx, req, token)
	}
	return []byte(`{"txnId":"test-txn-123","message":"OTP sent successfully"}`), nil
}

func (m *MockABHAService) SendEnrolRequestWithOTP(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error) {
	if m.SendEnrolRequestWithOTPFunc != nil {
		return m.SendEnrolRequestWithOTPFunc(ctx, req, token)
	}
	return []byte(`{"txnId":"test-txn-123","ABHANumber":"91-1234-5678-9012","message":"Enrollment successful"}`), nil
}

func (m *MockABHAService) FetchAbhaCardByType(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
	if m.FetchAbhaCardByTypeFunc != nil {
		return m.FetchAbhaCardByTypeFunc(ctx, abhaNumber, cardType)
	}
	return []byte("fake-card-data"), "image/jpeg", nil
}

func (m *MockABHAService) ProfileLoginRequestOTP(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error) {
	if m.ProfileLoginRequestOTPFunc != nil {
		return m.ProfileLoginRequestOTPFunc(ctx, req, token)
	}
	return []byte(`{"txnId":"profile-txn-456","message":"Profile OTP sent"}`), nil
}

func (m *MockABHAService) ProfileLoginVerifyOTP(ctx context.Context, req dtos.ProfileLoginVerifyOTP, token string) ([]byte, error) {
	if m.ProfileLoginVerifyOTPFunc != nil {
		return m.ProfileLoginVerifyOTPFunc(ctx, req, token)
	}
	return []byte(`{"txnId":"profile-txn-456","token":"auth-token-123","ABHANumber":"91-1234-5678-9012"}`), nil
}

func (m *MockABHAService) RecordAadhaarTxnOnOtp(ctx context.Context, tenantID, aadhaarPlain, txnID string) error {
	if m.RecordAadhaarTxnOnOtpFunc != nil {
		return m.RecordAadhaarTxnOnOtpFunc(ctx, tenantID, aadhaarPlain, txnID)
	}
	return nil
}

func (m *MockABHAService) ResolveAadhaarFromABHA(ctx context.Context, abha string) (string, error) {
	if m.ResolveAadhaarFromABHAFunc != nil {
		return m.ResolveAadhaarFromABHAFunc(ctx, abha)
	}
	return "123456789012", nil
}

// Implement other interface methods with reasonable defaults
func (m *MockABHAService) VerifyAadhaarOtpAndCreateIndividualV2(ctx context.Context, in dtos.VerifyAndCreateV2Input) ([]byte, error) {
	if m.VerifyAadhaarOtpAndCreateIndividualV2Func != nil {
		return m.VerifyAadhaarOtpAndCreateIndividualV2Func(ctx, in)
	}
	return []byte(`{"Individual":{"id":"test-id","individualId":"IND-123"}}`), nil
}

func (m *MockABHAService) FetchAbhaCard(ctx context.Context, abhaNumber string) ([]byte, error) {
	if m.FetchAbhaCardFunc != nil {
		return m.FetchAbhaCardFunc(ctx, abhaNumber)
	}
	return []byte("fake-card-data"), nil
}

func (m *MockABHAService) FetchQRCodeByABHANumber(ctx context.Context, abhaNumber string) ([]byte, error) {
	if m.FetchQRCodeByABHANumberFunc != nil {
		return m.FetchQRCodeByABHANumberFunc(ctx, abhaNumber)
	}
	return []byte("fake-qr-data"), nil
}

func (m *MockABHAService) LinkMobileNumber(ctx context.Context, req dtos.LinkMobileRequest, token string) ([]byte, error) {
	if m.LinkMobileNumberFunc != nil {
		return m.LinkMobileNumberFunc(ctx, req, token)
	}
	return []byte(`{"success": true}`), nil
}

func (m *MockABHAService) VerifyMobileOTP(ctx context.Context, req dtos.VerifyMobileOTPRequest, token string) ([]byte, error) {
	if m.VerifyMobileOTPFunc != nil {
		return m.VerifyMobileOTPFunc(ctx, req, token)
	}
	return []byte(`{"success": true}`), nil
}

func (m *MockABHAService) AddressSuggestion(ctx context.Context, req dtos.AddressSuggestionRequest, token string) ([]byte, error) {
	if m.AddressSuggestionFunc != nil {
		return m.AddressSuggestionFunc(ctx, req, token)
	}
	return []byte(`{"suggestions": []}`), nil
}

func (m *MockABHAService) EnrolAddress(ctx context.Context, req dtos.EnrolAddressRequest, token string) ([]byte, error) {
	if m.EnrolAddressFunc != nil {
		return m.EnrolAddressFunc(ctx, req, token)
	}
	return []byte(`{"success": true}`), nil
}

func (m *MockABHAService) LoginSendOTP(ctx context.Context, req dtos.SendOtpRequest, token string) ([]byte, error) {
	if m.LoginSendOTPFunc != nil {
		return m.LoginSendOTPFunc(ctx, req, token)
	}
	return []byte(`{"txnId": "login-txn"}`), nil
}

func (m *MockABHAService) LoginVerifyOTP(ctx context.Context, req dtos.VerifyOtpRequest, token string) ([]byte, error) {
	if m.LoginVerifyOTPFunc != nil {
		return m.LoginVerifyOTPFunc(ctx, req, token)
	}
	return []byte(`{"token": "login-token"}`), nil
}

func (m *MockABHAService) LoginCheckAuthMethods(ctx context.Context, req dtos.CheckAuthMethodsRequest, token string) ([]byte, error) {
	if m.LoginCheckAuthMethodsFunc != nil {
		return m.LoginCheckAuthMethodsFunc(ctx, req, token)
	}
	return []byte(`{"authMethods": ["otp"]}`), nil
}

// setupMockABDMServer creates a mock ABDM auth server
func setupMockABDMServer() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/gateway/v0.5/sessions" && r.Method == "POST" {
			response := map[string]interface{}{
				"accessToken": "test-auth-token-123",
				"expiresIn":   3600,
				"tokenType":   "Bearer",
			}
			w.Header().Set("Content-Type", "application/json")
			json.NewEncoder(w).Encode(response)
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
}

func setupTestRouter(mockService *MockABHAService) (*gin.Engine, func()) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	
	// Setup mock ABDM server
	mockServer := setupMockABDMServer()
	
	cfg := &configs.Config{
		AbdmClientID:     "test-client-id",
		AbdmClientSecret: "test-client-secret",
		AbdmAuthURL:      mockServer.URL + "/gateway/v0.5/sessions",
	}
	
	handler := NewABHAHandler(mockService, cfg)
	
	// Register only the 5 routes we're testing
	v1 := router.Group("/api/v1")
	{
		createGroup := v1.Group("/abha/create")
		{
			createGroup.POST("/send-aadhaar-otp", handler.SendAadhaarOTP)
			createGroup.POST("/verify-and-enroll-with-aadhaar-otp", handler.VerifyAndEnrolByAadhaarWithOTP)
		}
		cardGroup := v1.Group("/abha/card")
		{
			cardGroup.POST("/fetch", handler.GetABHACardUnified)
		}
		loginGroup := v1.Group("/abha/login")
		{
			loginGroup.POST("/profile/request-otp", handler.ProfileLoginRequestOTP)
			loginGroup.POST("/profile/verify-otp", handler.ProfileLoginVerifyOTP)
		}
	}
	
	return router, mockServer.Close
}

func TestSendAadhaarOTP(t *testing.T) {
	tests := []struct {
		name           string
		requestBody    interface{}
		mockSetup      func(*MockABHAService)
		expectedStatus int
		expectedBody   string
		tenantID       string
	}{
		{
			name: "successful OTP request",
			requestBody: dtos.AadhaarRequest{
				AadhaarNumber: "123456789012",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "encrypted_" + data, nil
				}
				m.SendOTPRequestFunc = func(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error) {
					assert.Equal(t, []string{"abha-enrol"}, req.Scope)
					assert.Equal(t, "aadhaar", req.LoginHint)
					assert.Equal(t, "encrypted_123456789012", req.LoginId)
					assert.Equal(t, "aadhaar", req.OTPSystem)
					return []byte(`{"txnId":"test-txn-123","message":"OTP sent successfully"}`), nil
				}
				m.RecordAadhaarTxnOnOtpFunc = func(ctx context.Context, tenantID, aadhaarPlain, txnID string) error {
					assert.Equal(t, "test-tenant", tenantID)
					assert.Equal(t, "123456789012", aadhaarPlain)
					assert.Equal(t, "test-txn-123", txnID)
					return nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedBody:   `{"txnId":"test-txn-123","message":"OTP sent successfully"}`,
			tenantID:       "test-tenant",
		},
		{
			name:           "invalid request body",
			requestBody:    "invalid-json",
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "missing aadhaar number",
			requestBody: map[string]string{
				"invalidField": "value",
			},
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "fetch public key error",
			requestBody: dtos.AadhaarRequest{
				AadhaarNumber: "123456789012",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "encrypt data error",
			requestBody: dtos.AadhaarRequest{
				AadhaarNumber: "123456789012",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "", assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "send OTP request error",
			requestBody: dtos.AadhaarRequest{
				AadhaarNumber: "123456789012",
			},
			mockSetup: func(m *MockABHAService) {
				m.SendOTPRequestFunc = func(ctx context.Context, req dtos.OTPRequest, token string) ([]byte, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "default tenant ID when header missing",
			requestBody: dtos.AadhaarRequest{
				AadhaarNumber: "123456789012",
			},
			mockSetup: func(m *MockABHAService) {
				m.RecordAadhaarTxnOnOtpFunc = func(ctx context.Context, tenantID, aadhaarPlain, txnID string) error {
					assert.Equal(t, "default", tenantID)
					return nil
				}
			},
			expectedStatus: http.StatusOK,
			tenantID:       "",
		},
		{
			name: "record transaction persistence error - should not fail request",
			requestBody: dtos.AadhaarRequest{
				AadhaarNumber: "123456789012",
			},
			mockSetup: func(m *MockABHAService) {
				m.RecordAadhaarTxnOnOtpFunc = func(ctx context.Context, tenantID, aadhaarPlain, txnID string) error {
					return assert.AnError // This should be logged but not fail the request
				}
			},
			expectedStatus: http.StatusOK,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			
			mockService := &MockABHAService{}
			tt.mockSetup(mockService)
			router, cleanup := setupTestRouter(mockService)
			defer cleanup()

			body, _ := json.Marshal(tt.requestBody)
			req := httptest.NewRequest(http.MethodPost, "/api/v1/abha/create/send-aadhaar-otp", bytes.NewBuffer(body))
			req.Header.Set("Content-Type", "application/json")
			if tt.tenantID != "" {
				req.Header.Set("X-Tenant-Id", tt.tenantID)
			}

			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			assert.Equal(t, tt.expectedStatus, w.Code)
			if tt.expectedBody != "" {
				assert.JSONEq(t, tt.expectedBody, w.Body.String())
			}
		})
	}
}

func TestVerifyAndEnrolByAadhaarWithOTP(t *testing.T) {
	tests := []struct {
		name           string
		requestBody    interface{}
		mockSetup      func(*MockABHAService)
		expectedStatus int
		expectedBody   string
	}{
		{
			name: "successful enrollment",
			requestBody: dtos.VerifyOTPInput{
				TxnId:  "test-txn-123",
				Otp:    "123456",
				Mobile: "9876543210",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "encrypted_" + data, nil
				}
				m.SendEnrolRequestWithOTPFunc = func(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error) {
					assert.Equal(t, []string{"otp"}, req.AuthData.AuthMethods)
					assert.Equal(t, "test-txn-123", req.AuthData.OTP.TxnId)
					assert.Equal(t, "encrypted_123456", req.AuthData.OTP.OtpValue)
					assert.Equal(t, "9876543210", req.AuthData.OTP.Mobile)
					assert.Equal(t, "abha-enrollment", req.Consent.Code)
					assert.Equal(t, "1.4", req.Consent.Version)
					return []byte(`{"txnId":"test-txn-123","ABHANumber":"91-1234-5678-9012","message":"Enrollment successful"}`), nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedBody:   `{"txnId":"test-txn-123","ABHANumber":"91-1234-5678-9012","message":"Enrollment successful"}`,
		},
		{
			name:           "invalid request body",
			requestBody:    "invalid-json",
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "missing required fields",
			requestBody: map[string]string{
				"invalidField": "value",
			},
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "fetch public key error",
			requestBody: dtos.VerifyOTPInput{
				TxnId:  "test-txn-123",
				Otp:    "123456",
				Mobile: "9876543210",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "encrypt OTP error",
			requestBody: dtos.VerifyOTPInput{
				TxnId:  "test-txn-123",
				Otp:    "123456",
				Mobile: "9876543210",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "", assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "enrollment service error",
			requestBody: dtos.VerifyOTPInput{
				TxnId:  "test-txn-123",
				Otp:    "123456",
				Mobile: "9876543210",
			},
			mockSetup: func(m *MockABHAService) {
				m.SendEnrolRequestWithOTPFunc = func(ctx context.Context, req dtos.EnrolByAadhaarWithOTPRequest, token string) ([]byte, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			
			mockService := &MockABHAService{}
			tt.mockSetup(mockService)
			router, cleanup := setupTestRouter(mockService)
			defer cleanup()

			body, _ := json.Marshal(tt.requestBody)
			req := httptest.NewRequest(http.MethodPost, "/api/v1/abha/create/verify-and-enroll-with-aadhaar-otp", bytes.NewBuffer(body))
			req.Header.Set("Content-Type", "application/json")

			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			assert.Equal(t, tt.expectedStatus, w.Code)
			if tt.expectedBody != "" {
				assert.JSONEq(t, tt.expectedBody, w.Body.String())
			}
		})
	}
}

func TestGetABHACardUnified(t *testing.T) {
	tests := []struct {
		name           string
		requestBody    interface{}
		mockSetup      func(*MockABHAService)
		expectedStatus int
		expectedHeader map[string]string
		expectedBody   []byte
	}{
		{
			name: "successful JPEG card fetch",
			requestBody: dtos.AbhaCardRequest{
				AbhaNumber: "91-1234-5678-9012",
				CardType:   "getCard",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchAbhaCardByTypeFunc = func(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
					assert.Equal(t, "91-1234-5678-9012", abhaNumber)
					assert.Equal(t, "getCard", cardType)
					return []byte("fake-jpeg-card-data"), "image/jpeg", nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedHeader: map[string]string{
				"Content-Type":        "image/jpeg",
				"Content-Disposition": "inline; filename=abha.jpg",
			},
			expectedBody: []byte("fake-jpeg-card-data"),
		},
		{
			name: "successful SVG card fetch",
			requestBody: dtos.AbhaCardRequest{
				AbhaNumber: "91-1234-5678-9012",
				CardType:   "getSvgCard",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchAbhaCardByTypeFunc = func(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
					return []byte("<svg>fake-svg-data</svg>"), "image/svg+xml", nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedHeader: map[string]string{
				"Content-Type":        "image/svg+xml",
				"Content-Disposition": "inline; filename=abha.svg",
			},
			expectedBody: []byte("<svg>fake-svg-data</svg>"),
		},
		{
			name: "successful PNG card fetch",
			requestBody: dtos.AbhaCardRequest{
				AbhaNumber: "91-1234-5678-9012",
				CardType:   "getPngCard",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchAbhaCardByTypeFunc = func(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
					return []byte("fake-png-card-data"), "image/png", nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedHeader: map[string]string{
				"Content-Type":        "image/png",
				"Content-Disposition": "inline; filename=abha.png",
			},
			expectedBody: []byte("fake-png-card-data"),
		},
		{
			name:           "invalid request body",
			requestBody:    "invalid-json",
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "missing required fields",
			requestBody: map[string]string{
				"invalidField": "value",
			},
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "service error",
			requestBody: dtos.AbhaCardRequest{
				AbhaNumber: "91-1234-5678-9012",
				CardType:   "getCard",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchAbhaCardByTypeFunc = func(ctx context.Context, abhaNumber, cardType string) ([]byte, string, error) {
					return nil, "", assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			
			mockService := &MockABHAService{}
			tt.mockSetup(mockService)
			router, cleanup := setupTestRouter(mockService)
			defer cleanup()

			body, _ := json.Marshal(tt.requestBody)
			req := httptest.NewRequest(http.MethodPost, "/api/v1/abha/card/fetch", bytes.NewBuffer(body))
			req.Header.Set("Content-Type", "application/json")

			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			assert.Equal(t, tt.expectedStatus, w.Code)
			
			for key, expectedValue := range tt.expectedHeader {
				assert.Equal(t, expectedValue, w.Header().Get(key))
			}
			
			if tt.expectedBody != nil {
				assert.Equal(t, tt.expectedBody, w.Body.Bytes())
			}
		})
	}
}

func TestProfileLoginRequestOTP(t *testing.T) {
	tests := []struct {
		name           string
		requestBody    interface{}
		mockSetup      func(*MockABHAService)
		expectedStatus int
		expectedBody   string
	}{
		{
			name: "successful aadhaar OTP request",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "aadhaar",
				Value:     "123456789012",
				OtpSystem: "aadhaar",
				Scope:     []string{"abha-login"},
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "encrypted_" + data, nil
				}
				m.ProfileLoginRequestOTPFunc = func(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error) {
					assert.Equal(t, []string{"abha-login"}, req.Scope)
					assert.Equal(t, "aadhaar", req.LoginHint)
					assert.Equal(t, "encrypted_123456789012", req.LoginId)
					assert.Equal(t, "aadhaar", req.OTPSystem)
					return []byte(`{"txnId":"profile-txn-456","message":"Profile OTP sent"}`), nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedBody:   `{"txnId":"profile-txn-456","message":"Profile OTP sent"}`,
		},
		{
			name: "successful mobile OTP request",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "mobile",
				Value:     "9876543210",
				OtpSystem: "abdm",
			},
			mockSetup: func(m *MockABHAService) {
				m.ProfileLoginRequestOTPFunc = func(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error) {
					assert.Contains(t, req.Scope, "abha-login")
					assert.Contains(t, req.Scope, "mobile-verify")
					assert.Equal(t, "mobile", req.LoginHint)
					assert.Equal(t, "9876543210", req.LoginId) // Mobile not encrypted
					assert.Equal(t, "abdm", req.OTPSystem)
					return []byte(`{"txnId":"profile-txn-456","message":"Profile OTP sent"}`), nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedBody:   `{"txnId":"profile-txn-456","message":"Profile OTP sent"}`,
		},
		{
			name: "successful ABHA number resolution",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "abha-number",
				Value:     "91-1234-5678-9012",
				OtpSystem: "aadhaar",
			},
			mockSetup: func(m *MockABHAService) {
				m.ResolveAadhaarFromABHAFunc = func(ctx context.Context, abha string) (string, error) {
					assert.Equal(t, "91-1234-5678-9012", abha)
					return "123456789012", nil
				}
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					assert.Equal(t, "123456789012", data)
					return "encrypted_" + data, nil
				}
				m.ProfileLoginRequestOTPFunc = func(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error) {
					assert.Contains(t, req.Scope, "aadhaar-verify")
					assert.Equal(t, "aadhaar", req.LoginHint)
					assert.Equal(t, "encrypted_123456789012", req.LoginId)
					assert.Equal(t, "aadhaar", req.OTPSystem)
					return []byte(`{"txnId":"profile-txn-456","message":"Profile OTP sent"}`), nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedBody:   `{"txnId":"profile-txn-456","message":"Profile OTP sent"}`,
		},
		{
			name:           "invalid request body",
			requestBody:    "invalid-json",
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "missing required fields",
			requestBody: map[string]string{
				"invalidField": "value",
			},
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "invalid ABHA number format",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "abha-number",
				Value:     "invalid-abha",
				OtpSystem: "aadhaar",
			},
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "ABHA resolution failed",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "abha-number",
				Value:     "91-1234-5678-9012",
				OtpSystem: "aadhaar",
			},
			mockSetup: func(m *MockABHAService) {
				m.ResolveAadhaarFromABHAFunc = func(ctx context.Context, abha string) (string, error) {
					return "", assert.AnError
				}
			},
			expectedStatus: http.StatusNotFound,
		},
		{
			name: "fetch public key error",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "aadhaar",
				Value:     "123456789012",
				OtpSystem: "aadhaar",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "encrypt data error",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "aadhaar",
				Value:     "123456789012",
				OtpSystem: "aadhaar",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "", assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "profile login service error",
			requestBody: dtos.ProfileLoginSendInput{
				LoginHint: "mobile",
				Value:     "9876543210",
				OtpSystem: "abdm",
			},
			mockSetup: func(m *MockABHAService) {
				m.ProfileLoginRequestOTPFunc = func(ctx context.Context, req dtos.ProfileLoginRequestOTP, token string) ([]byte, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusBadGateway,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			
			mockService := &MockABHAService{}
			tt.mockSetup(mockService)
			router, cleanup := setupTestRouter(mockService)
			defer cleanup()

			body, _ := json.Marshal(tt.requestBody)
			req := httptest.NewRequest(http.MethodPost, "/api/v1/abha/login/profile/request-otp", bytes.NewBuffer(body))
			req.Header.Set("Content-Type", "application/json")

			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			assert.Equal(t, tt.expectedStatus, w.Code)
			if tt.expectedBody != "" {
				assert.JSONEq(t, tt.expectedBody, w.Body.String())
			}
		})
	}
}

func TestProfileLoginVerifyOTP(t *testing.T) {
	tests := []struct {
		name           string
		requestBody    interface{}
		mockSetup      func(*MockABHAService)
		expectedStatus int
		expectedBody   string
	}{
		{
			name: "successful OTP verification",
			requestBody: dtos.ProfileLoginVerifyInput{
				TxnId:     "profile-txn-456",
				OtpValue:  "123456",
				OtpSystem: "aadhaar",
				LoginHint: "aadhaar",
				Scope:     []string{"abha-login"},
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "encrypted_" + data, nil
				}
				m.ProfileLoginVerifyOTPFunc = func(ctx context.Context, req dtos.ProfileLoginVerifyOTP, token string) ([]byte, error) {
					assert.Equal(t, []string{"abha-login"}, req.Scope)
					assert.Equal(t, []string{"otp"}, req.AuthData.AuthMethods)
					assert.Equal(t, "profile-txn-456", req.AuthData.OTP.TxnId)
					assert.Equal(t, "encrypted_123456", req.AuthData.OTP.OtpValue)
					return []byte(`{"txnId":"profile-txn-456","token":"auth-token-123","ABHANumber":"91-1234-5678-9012"}`), nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedBody:   `{"txnId":"profile-txn-456","token":"auth-token-123","ABHANumber":"91-1234-5678-9012"}`,
		},
		{
			name: "default scope when empty",
			requestBody: dtos.ProfileLoginVerifyInput{
				TxnId:     "profile-txn-456",
				OtpValue:  "123456",
				OtpSystem: "abdm",
				LoginHint: "mobile",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "encrypted_" + data, nil
				}
				m.ProfileLoginVerifyOTPFunc = func(ctx context.Context, req dtos.ProfileLoginVerifyOTP, token string) ([]byte, error) {
					assert.Contains(t, req.Scope, "abha-login")
					assert.Contains(t, req.Scope, "mobile-verify")
					return []byte(`{"txnId":"profile-txn-456","token":"auth-token-123"}`), nil
				}
			},
			expectedStatus: http.StatusOK,
			expectedBody:   `{"txnId":"profile-txn-456","token":"auth-token-123"}`,
		},
		{
			name:           "invalid request body",
			requestBody:    "invalid-json",
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "missing required fields",
			requestBody: map[string]string{
				"invalidField": "value",
			},
			mockSetup:      func(m *MockABHAService) {},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name: "fetch public key error",
			requestBody: dtos.ProfileLoginVerifyInput{
				TxnId:     "profile-txn-456",
				OtpValue:  "123456",
				OtpSystem: "aadhaar",
				LoginHint: "aadhaar",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "encrypt OTP error",
			requestBody: dtos.ProfileLoginVerifyInput{
				TxnId:     "profile-txn-456",
				OtpValue:  "123456",
				OtpSystem: "aadhaar",
				LoginHint: "aadhaar",
			},
			mockSetup: func(m *MockABHAService) {
				m.FetchPublicKeyFunc = func(ctx context.Context) (*rsa.PublicKey, error) {
					return &rsa.PublicKey{}, nil
				}
				m.EncryptDataFunc = func(ctx context.Context, publicKey *rsa.PublicKey, data string) (string, error) {
					return "", assert.AnError
				}
			},
			expectedStatus: http.StatusInternalServerError,
		},
		{
			name: "profile verify service error",
			requestBody: dtos.ProfileLoginVerifyInput{
				TxnId:     "profile-txn-456",
				OtpValue:  "123456",
				OtpSystem: "aadhaar",
				LoginHint: "aadhaar",
			},
			mockSetup: func(m *MockABHAService) {
				m.ProfileLoginVerifyOTPFunc = func(ctx context.Context, req dtos.ProfileLoginVerifyOTP, token string) ([]byte, error) {
					return nil, assert.AnError
				}
			},
			expectedStatus: http.StatusBadGateway,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			
			mockService := &MockABHAService{}
			tt.mockSetup(mockService)
			router, cleanup := setupTestRouter(mockService)
			defer cleanup()

			body, _ := json.Marshal(tt.requestBody)
			req := httptest.NewRequest(http.MethodPost, "/api/v1/abha/login/profile/verify-otp", bytes.NewBuffer(body))
			req.Header.Set("Content-Type", "application/json")

			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			assert.Equal(t, tt.expectedStatus, w.Code)
			if tt.expectedBody != "" {
				assert.JSONEq(t, tt.expectedBody, w.Body.String())
			}
		})
	}
}

// Integration test to verify all handlers work together
func TestHandlerIntegration(t *testing.T) {
	t.Run("complete ABHA enrollment flow", func(t *testing.T) {
		mockService := &MockABHAService{}
		router, cleanup := setupTestRouter(mockService)
		defer cleanup()

		// Step 1: Send Aadhaar OTP
		sendOTPBody, _ := json.Marshal(dtos.AadhaarRequest{
			AadhaarNumber: "123456789012",
		})
		
		req1 := httptest.NewRequest(http.MethodPost, "/api/v1/abha/create/send-aadhaar-otp", bytes.NewBuffer(sendOTPBody))
		req1.Header.Set("Content-Type", "application/json")
		req1.Header.Set("X-Tenant-Id", "test-tenant")
		
		w1 := httptest.NewRecorder()
		router.ServeHTTP(w1, req1)
		
		require.Equal(t, http.StatusOK, w1.Code)
		
		var otpResponse struct {
			TxnId string `json:"txnId"`
		}
		require.NoError(t, json.Unmarshal(w1.Body.Bytes(), &otpResponse))
		require.Equal(t, "test-txn-123", otpResponse.TxnId)

		// Step 2: Verify OTP and Enroll
		verifyOTPBody, _ := json.Marshal(dtos.VerifyOTPInput{
			TxnId:  otpResponse.TxnId,
			Otp:    "123456",
			Mobile: "9876543210",
		})
		
		req2 := httptest.NewRequest(http.MethodPost, "/api/v1/abha/create/verify-and-enroll-with-aadhaar-otp", bytes.NewBuffer(verifyOTPBody))
		req2.Header.Set("Content-Type", "application/json")
		
		w2 := httptest.NewRecorder()
		router.ServeHTTP(w2, req2)
		
		require.Equal(t, http.StatusOK, w2.Code)
		
		var enrollResponse struct {
			ABHANumber string `json:"ABHANumber"`
		}
		require.NoError(t, json.Unmarshal(w2.Body.Bytes(), &enrollResponse))
		require.Equal(t, "91-1234-5678-9012", enrollResponse.ABHANumber)

		// Step 3: Get ABHA Card
		getCardBody, _ := json.Marshal(dtos.AbhaCardRequest{
			AbhaNumber: enrollResponse.ABHANumber,
			CardType:   "getCard",
		})
		
		req3 := httptest.NewRequest(http.MethodPost, "/api/v1/abha/card/fetch", bytes.NewBuffer(getCardBody))
		req3.Header.Set("Content-Type", "application/json")
		
		w3 := httptest.NewRecorder()
		router.ServeHTTP(w3, req3)
		
		require.Equal(t, http.StatusOK, w3.Code)
		require.Equal(t, "image/jpeg", w3.Header().Get("Content-Type"))
	})
}