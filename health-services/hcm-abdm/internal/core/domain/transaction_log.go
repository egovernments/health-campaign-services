package domain

import (
	"time"

	"github.com/google/uuid"
)

type TransactionLog struct {
	ID              int64     `json:"id"`
	ABHANumber      string    `json:"abha_number,omitempty"`
	RequestType     string    `json:"request_type"`            // e.g., send-aadhaar-otp, getCard, qrCode
	Endpoint        string    `json:"endpoint,omitempty"`      // URL or path
	RequestPayload  string    `json:"request_payload"`         // Store JSON as string
	ResponsePayload string    `json:"response_payload"`        // Store JSON as string
	ResponseStatus  int       `json:"response_status_code"`    // HTTP response code
	ErrorMessage    string    `json:"error_message,omitempty"` // Detailed failure if any
	SourceIP        string    `json:"source_ip,omitempty"`     // Optional: c.ClientIP()
	UserAgent       string    `json:"user_agent,omitempty"`    // Optional: c.Request.UserAgent()
	TraceID         uuid.UUID `json:"trace_id"`                // For distributed tracing
	LatencyMs       int64     `json:"latency_ms"`              // Duration in milliseconds
	CreatedAt       time.Time `json:"created_at"`
}
