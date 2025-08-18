package configs

import (
	"os"
	"strconv"
	"time"
)

// Config represents the application configuration
type Config struct {
	// Server configuration
	RESTPort int
	GRPCPort int

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
	PublicKeyURL           string
	OTPRequestURL          string
	EnrolByAadhaarURL      string
	AbdmClientID           string
	AbdmClientSecret       string
	ABDM_URL               string
	HEALTH_SERVICE_API_URL string
	AbdmAuthURL            string
	ABHACardEndpoints      ABHACardEndpointsConfig
	QRCode                 string
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
		AbdmClientID:      getEnv("ABDM_CLIENT_ID", "******"),
		AbdmClientSecret:  getEnv("ABDM_CLIENT_SECRET", "******************"),
		AbdmAuthURL:       getEnv("ABDM_AUTH_URL", "https://dev.abdm.gov.in/gateway/v0.5/sessions"),

		// New ABHA Card Endpoints
		ABHACardEndpoints: ABHACardEndpointsConfig{
			GetCard:    getEnv("ABHA_GET_CARD_URL", "https://healthidsbx.abdm.gov.in/api/v1/account/getCard"),
			GetSvgCard: getEnv("ABHA_GET_SVG_CARD_URL", "https://healthidsbx.abdm.gov.in/api/v1/account/getSvgCard"),
			GetPngCard: getEnv("ABHA_GET_PNG_CARD_URL", "https://healthidsbx.abdm.gov.in/api/v1/account/getPngCard"),
		},
		QRCode: getEnv("ABHA_QR_ENDPOINT", "https://healthidsbx.abdm.gov.in/api/v1/account/qrCode"),
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
