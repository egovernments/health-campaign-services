package domain

import "encoding/json"

type AbhaIndividualTransaction struct {
	ID                string
	IndividualID      *string
	TransactionID     string
	TenantID          string
	AdditionalDetails json.RawMessage
	CreatedBy         string
	LastModifiedBy    string
	CreatedTime       int64
	LastModifiedTime  int64
	RowVersion        int64
	IsDeleted         bool
	AbhaNumber        *string
	AadharNumberEnc   string // encrypted (base64 AES-GCM)
	AadhaarHash       string // hex(SHA-256), unique
}
