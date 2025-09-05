package configs

import (
	"os"
	"strconv"
	"strings"
	"time"
)

// Config represents the application configuration
type Config struct {
	// Server configuration
	RESTPort int
	GRPCPort int

	ContextPath string

	// Database configuration
	DBHost     string
	DBPort     string
	DBUser     string
	DBPassword string
	DBName     string
	DBSSLMode  string

	// Migration configuratoin
	EnableMigrations bool

	// Redis configuration
	RedisHost     string
	RedisPort     string
	RedisPassword string
	RedisDB       int

	// Cache configuration
	CacheExpiration time.Duration

	// ABDM configurations
	PublicKeyURL               string
	OTPRequestURL              string
	EnrolByAadhaarURL          string
	AbdmClientID               string
	AbdmClientSecret           string
	ABDM_URL                   string
	INDIVIDUAL_SERVICE_API_URL string
	AbdmAuthURL                string
	ABHACardEndpoints          ABHACardEndpointsConfig
	QRCode                     string

	// New Create-flow URLs
	LinkMobileURL        string
	VerifyMobileURL      string
	AddressSuggestionURL string
	EnrolAddressURL      string

	// New Login URLs
	LoginSendOtpURL   string
	LoginVerifyOtpURL string
	LoginCheckAuthURL string

	// add to Config struct
	ProfileLoginRequestOTPURL string
	ProfileLoginVerifyOTPURL  string

	IndividualCreateURL string
	HTTPClientTimeout   time.Duration
}

type ABHACardEndpointsConfig struct {
	GetCard    string
	GetSvgCard string
	GetPngCard string
}

// LoadConfig loads the configuration from environment variables
func LoadConfig() *Config {
	return &Config{
		// Server configuration
		RESTPort: getEnvAsInt("REST_PORT", 8088),
		GRPCPort: getEnvAsInt("GRPC_PORT", 8089),
		// Context path (default: /hcm-abha)
		ContextPath: normalizeContextPath(getEnv("CONTEXT_PATH", "/hcm-abha")),

		// Database configuration
		DBHost:     getEnv("DB_HOST", "localhost"),
		DBPort:     getEnv("DB_PORT", "5432"),
		DBUser:     getEnv("DB_USER", "postgres"),
		DBPassword: getEnv("DB_PASSWORD", "postgres"),
		DBName:     getEnv("DB_NAME", "postgres"),
		DBSSLMode:  getEnv("DB_SSL_MODE", "disable"),

		EnableMigrations: getEnv("ENABLE_MIGRATIONS", "true") == "true",

		// Redis configuration
		RedisHost:     getEnv("REDIS_HOST", "localhost"),
		RedisPort:     getEnv("REDIS_PORT", "6379"),
		RedisPassword: getEnv("REDIS_PASSWORD", ""),
		RedisDB:       getEnvAsInt("REDIS_DB", 0),

		// Cache configuration
		CacheExpiration: getEnvAsDuration("CACHE_EXPIRATION", 24*time.Hour),

		// ABDM configrurations
		PublicKeyURL:      getEnv("PUBLIC_KEY_URL", "https://healthidsbx.abdm.gov.in/api/v1/auth/cert"),
		OTPRequestURL:     getEnv("OTP_REQUEST_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/enrollment/request/otp"),
		EnrolByAadhaarURL: getEnv("ENROL_BY_AADHAAR_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/enrollment/enrol/byAadhaar"),
		AbdmClientID:      getEnv("ABDM_CLIENT_ID", "SBX_******"),
		AbdmClientSecret:  getEnv("ABDM_CLIENT_SECRET", "************************"),
		AbdmAuthURL:       getEnv("ABDM_AUTH_URL", "https://dev.abdm.gov.in/gateway/v0.5/sessions"),

		// New ABHA Card Endpoints
		ABHACardEndpoints: ABHACardEndpointsConfig{
			GetCard:    getEnv("ABHA_GET_CARD_URL", "https://healthidsbx.abdm.gov.in/api/v1/account/getCard"),
			GetSvgCard: getEnv("ABHA_GET_SVG_CARD_URL", "https://healthidsbx.abdm.gov.in/api/v1/account/getSvgCard"),
			GetPngCard: getEnv("ABHA_GET_PNG_CARD_URL", "https://healthidsbx.abdm.gov.in/api/v1/account/getPngCard"),
		},
		QRCode: getEnv("ABHA_QR_ENDPOINT", "https://healthidsbx.abdm.gov.in/api/v1/account/qrCode"),

		// Create Flow URLs
		LinkMobileURL:        getEnv("LINK_MOBILE_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/enrollment/request/otp"),
		VerifyMobileURL:      getEnv("VERIFY_MOBILE_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/enrollment/auth/byAbdm"),
		AddressSuggestionURL: getEnv("ADDRESS_SUGGESTION_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/enrollment/enrol/suggestion"),
		EnrolAddressURL:      getEnv("ENROL_ADDRESS_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/enrollment/enrol/abha-address"),

		// Login Flow URLs
		LoginSendOtpURL:   getEnv("LOGIN_SEND_OTP_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/profile/login/request/otp"),
		LoginVerifyOtpURL: getEnv("LOGIN_VERIFY_OTP_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/profile/login/verify"),
		LoginCheckAuthURL: getEnv("LOGIN_CHECK_AUTH_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/phr/web/login/abha/search"),

		// profile login URLs
		// in LoadConfig()
		ProfileLoginRequestOTPURL: getEnv("PROFILE_LOGIN_REQUEST_OTP_URL", getEnv("LOGIN_SEND_OTP_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/profile/login/request/otp")),
		ProfileLoginVerifyOTPURL:  getEnv("PROFILE_LOGIN_VERIFY_OTP_URL", getEnv("LOGIN_VERIFY_OTP_URL", "https://abhasbx.abdm.gov.in/abha/api/v3/profile/login/verify")),

		// NEW: outbound HTTP timeout (default 15s)
		HTTPClientTimeout: getEnvAsDuration("HTTP_CLIENT_TIMEOUT", 15*time.Second),

		// NEW: Individual create URL (defaults to HEALTH_SERVICE_API_URL + /health-individual/v1/_create)
		IndividualCreateURL: getEnv(
			"INDIVIDUAL_CREATE_URL",
			trimRightSlash(getEnv("INDIVIDUAL_SERVICE_API_URL", "http://localhost:8080"))+"/health-individual/v1/_create",
		),
	}
}

// getEnv gets an environment variable or returns a default value
func getEnv(key, defaultValue string) string {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}
	return value
}

// getEnvAsInt gets an environment variable as an integer or returns a default value
func getEnvAsInt(key string, defaultValue int) int {
	valueStr := getEnv(key, "")
	if valueStr == "" {
		return defaultValue
	}

	value, err := strconv.Atoi(valueStr)
	if err != nil {
		return defaultValue
	}

	return value
}

// getEnvAsDuration gets an environment variable as a duration or returns a default value
func getEnvAsDuration(key string, defaultValue time.Duration) time.Duration {
	valueStr := getEnv(key, "")
	if valueStr == "" {
		return defaultValue
	}

	value, err := time.ParseDuration(valueStr)
	if err != nil {
		return defaultValue
	}

	return value
}

func trimRightSlash(s string) string {
	if len(s) > 0 && s[len(s)-1] == '/' {
		return s[:len(s)-1]
	}
	return s
}

func normalizeContextPath(s string) string {
	s = strings.TrimSpace(s)
	if s == "" {
		s = "/hcm-abha"
	}
	// ensure leading slash
	if !strings.HasPrefix(s, "/") {
		s = "/" + s
	}
	// remove trailing slash except root
	if len(s) > 1 && s[len(s)-1] == '/' {
		s = s[:len(s)-1]
	}
	return s
}
