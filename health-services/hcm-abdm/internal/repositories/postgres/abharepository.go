package postgres

import (
	"context"
	"database/sql"
	"digit-abdm/internal/core/domain"
	"digit-abdm/internal/core/ports"
	"errors"
	"time"

	"github.com/google/uuid"
)

type AbhaRepositoryImpl struct {
	db *sql.DB
}

func NewAbhaRepository(db *sql.DB) ports.AbhaRepository {
	return &AbhaRepositoryImpl{db: db}
}

func (r *AbhaRepositoryImpl) SaveAbhaProfile(ctx context.Context, abha *domain.AbhaNumber) error {
	// COALESCE(NULLIF($1,'')::uuid, gen_random_uuid()) turns "" -> NULL -> generated UUID.
	// Text columns use COALESCE(NULLIF(EXCLUDED.col,''), existing.col) to avoid overwriting with empty strings.
	const stmt = `
	  INSERT INTO abha_number (
		external_id, deleted, abha_number, health_id, email, first_name, middle_name, last_name, profile_photo,
		access_token, refresh_token, address, date_of_birth, district, gender, name, pincode, state, mobile,
		created_by, last_modified_by, created_date, modified_date, new
	  )
	  VALUES (
		COALESCE(NULLIF($1,'')::uuid, gen_random_uuid()),
		$2, $3, $4, $5, $6, $7, $8, $9,
		$10, $11, $12, $13, $14, $15, $16, $17, $18, $19,
		$20, $21, $22, $23, $24
	  )
	  ON CONFLICT (abha_number) DO UPDATE SET
		external_id     = COALESCE(EXCLUDED.external_id, abha_number.external_id),
		health_id       = COALESCE(NULLIF(EXCLUDED.health_id,''),       abha_number.health_id),
		email           = COALESCE(NULLIF(EXCLUDED.email,''),           abha_number.email),
		first_name      = COALESCE(NULLIF(EXCLUDED.first_name,''),      abha_number.first_name),
		middle_name     = COALESCE(NULLIF(EXCLUDED.middle_name,''),     abha_number.middle_name),
		last_name       = COALESCE(NULLIF(EXCLUDED.last_name,''),       abha_number.last_name),
		profile_photo   = COALESCE(NULLIF(EXCLUDED.profile_photo,''),   abha_number.profile_photo),
		access_token    = COALESCE(NULLIF(EXCLUDED.access_token,''),    abha_number.access_token),
		refresh_token   = COALESCE(NULLIF(EXCLUDED.refresh_token,''),   abha_number.refresh_token),
		address         = COALESCE(NULLIF(EXCLUDED.address,''),         abha_number.address),
		date_of_birth   = COALESCE(NULLIF(EXCLUDED.date_of_birth,''),   abha_number.date_of_birth),
		district        = COALESCE(NULLIF(EXCLUDED.district,''),        abha_number.district),
		gender          = COALESCE(NULLIF(EXCLUDED.gender,''),          abha_number.gender),
		name            = COALESCE(NULLIF(EXCLUDED.name,''),            abha_number.name),
		pincode         = COALESCE(NULLIF(EXCLUDED.pincode,''),         abha_number.pincode),
		state           = COALESCE(NULLIF(EXCLUDED.state,''),           abha_number.state),
		mobile          = COALESCE(NULLIF(EXCLUDED.mobile,''),          abha_number.mobile),
		modified_date   = EXCLUDED.modified_date,
		last_modified_by= EXCLUDED.last_modified_by
	`

	_, err := r.db.ExecContext(ctx, stmt,
		abha.ExternalID, abha.Deleted, abha.ABHANumber, abha.HealthID, abha.Email, abha.FirstName, abha.MiddleName, abha.LastName, abha.ProfilePhoto,
		abha.AccessToken, abha.RefreshToken, abha.Address, abha.DateOfBirth, abha.District, abha.Gender, abha.Name, abha.Pincode, abha.State, abha.Mobile,
		abha.CreatedBy, abha.LastModifiedBy, abha.CreatedDate, abha.LastModifiedDate, abha.New,
	)
	return err
}

// GetAbhaByNumber retrieves a profile by abha_number
func (r *AbhaRepositoryImpl) GetAbhaByNumber(ctx context.Context, abhaNumber string) (*domain.AbhaNumber, error) {
	query := `SELECT * FROM abha_number WHERE abha_number = $1`
	row := r.db.QueryRowContext(ctx, query, abhaNumber)

	var abha domain.AbhaNumber
	err := row.Scan(
		&abha.ID, &abha.ExternalID, &abha.Deleted, &abha.ABHANumber, &abha.HealthID, &abha.Email, &abha.FirstName, &abha.MiddleName,
		&abha.LastName, &abha.ProfilePhoto, &abha.AccessToken, &abha.RefreshToken, &abha.Address, &abha.DateOfBirth,
		&abha.District, &abha.Gender, &abha.Name, &abha.Pincode, &abha.State, &abha.Mobile,
		&abha.CreatedBy, &abha.LastModifiedBy, &abha.CreatedDate, &abha.LastModifiedDate, &abha.New,
	)

	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}

	return &abha, nil
}

func (r *AbhaRepositoryImpl) GetTokenByABHANumber(ctx context.Context, abhaNumber string) (*domain.AbhaNumber, error) {
	query := `SELECT access_token, refresh_token FROM abha_number WHERE abha_number = $1 AND deleted = false`
	row := r.db.QueryRowContext(ctx, query, abhaNumber)

	var accessToken, refreshToken string
	if err := row.Scan(&accessToken, &refreshToken); err != nil {
		return nil, err
	}

	return &domain.AbhaNumber{
		ABHANumber:   abhaNumber,
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
	}, nil
}

func (r *AbhaRepositoryImpl) InsertTransactionLog(ctx context.Context, log *domain.TransactionLog) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO abha_transaction_log 
		(abha_number, request_type, endpoint, request_payload, response_payload, 
		response_status_code, error_message, source_ip, user_agent, trace_id, latency_ms, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)`,
		log.ABHANumber, log.RequestType, log.Endpoint, log.RequestPayload, log.ResponsePayload,
		log.ResponseStatus, log.ErrorMessage, log.SourceIP, log.UserAgent, log.TraceID, log.LatencyMs, log.CreatedAt)
	return err
}

func (r *AbhaRepositoryImpl) InsertLoginTransaction(ctx context.Context, log *domain.TransactionLog) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO abha_login_transaction_log
		    (abha_number,request_type,endpoint,request_payload,response_payload,response_status_code,
		     error_message,source_ip,user_agent,trace_id,latency_ms,created_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)`,
		log.ABHANumber, log.RequestType, log.Endpoint, log.RequestPayload, log.ResponsePayload,
		log.ResponseStatus, log.ErrorMessage, log.SourceIP, log.UserAgent, log.TraceID, log.LatencyMs, log.CreatedAt)
	return err
}

// UpsertAadhaarTxnOnOtp creates/updates a transaction keyed by Aadhaar hash.
// Idempotent for resend: if the same Aadhaar is used again, we overwrite the txnId and ciphertext.
func (r *AbhaRepositoryImpl) UpsertAadhaarTxnOnOtp(
	ctx context.Context,
	tenantId, txnId, aadharEnc, aadhaarHash, actor string,
) (string, error) {
	now := time.Now().UnixMilli()
	id := uuid.New().String()

	const stmt = `
INSERT INTO abha_individual_transaction
  (id, individualId, transactionId, tenantId, additionalDetails, createdBy, lastModifiedBy,
   createdTime, lastModifiedTime, rowVersion, isDeleted, abhaNumber, aadharNumber, aadhaarHash)
VALUES
  ($1, NULL, $2, $3, '{}'::jsonb, $4, $4, $5, $5, 1, false, NULL, $6, $7)
ON CONFLICT (aadhaarHash) DO UPDATE SET
  transactionId     = EXCLUDED.transactionId,
  aadharNumber      = EXCLUDED.aadharNumber,
  lastModifiedBy    = EXCLUDED.lastModifiedBy,
  lastModifiedTime  = EXCLUDED.lastModifiedTime,
  isDeleted         = false
RETURNING id;
`
	var retID string
	err := r.db.QueryRowContext(ctx, stmt,
		id, txnId, tenantId, actor, now, aadharEnc, aadhaarHash,
	).Scan(&retID)
	return retID, err
}

/* ---------------------- NEW: finalize on successful verify ---------------------- */

func (r *AbhaRepositoryImpl) UpdateTxnOnVerify(
	ctx context.Context,
	txnId, individualId, abhaNumber, actor string,
) error {
	const stmt = `
UPDATE abha_individual_transaction
   SET individualId = $2,
       abhaNumber   = $3,
       lastModifiedBy = $4,
       lastModifiedTime = $5
 WHERE transactionId = $1;
`
	res, err := r.db.ExecContext(ctx, stmt, txnId, individualId, abhaNumber, actor, time.Now().UnixMilli())
	if err != nil {
		return err
	}
	aff, _ := res.RowsAffected()
	if aff == 0 {
		return sql.ErrNoRows
	}
	return nil
}

// GetEncryptedAadhaarByTxn returns the encrypted Aadhaar (base64 AES-GCM) for a given txnId.
func (r *AbhaRepositoryImpl) GetEncryptedAadhaarByTxn(ctx context.Context, txnId string) (string, error) {
	const q = `
        SELECT aadharNumber
        FROM abha_individual_transaction
        WHERE transactionId = $1
        LIMIT 1;
    `
	var enc string
	err := r.db.QueryRowContext(ctx, q, txnId).Scan(&enc)
	if err != nil {
		return "", err // may be sql.ErrNoRows
	}
	return enc, nil
}

// GetEncryptedAadhaarByABHA returns encrypted Aadhaar for a given ABHA number.
// Try exact match on canonical hyphenated form first (uses index), then fallback to REPLACE.
func (r *AbhaRepositoryImpl) GetEncryptedAadhaarByABHA(ctx context.Context, abhaNumber string) (string, error) {
	// First try exact (fast, index-friendly)
	const qExact = `
SELECT aadharNumber
FROM abha_individual_transaction
WHERE abhaNumber = $1
ORDER BY lastModifiedTime DESC
LIMIT 1;`
	var enc string
	err := r.db.QueryRowContext(ctx, qExact, abhaNumber).Scan(&enc)
	if err == nil {
		return enc, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return "", err
	}

	// Fallback (handles legacy data without hyphens / mixed formats)
	const qFallback = `
SELECT aadharNumber
FROM abha_individual_transaction
WHERE REPLACE(abhaNumber, '-', '') = REPLACE($1, '-', '')
ORDER BY lastModifiedTime DESC
LIMIT 1;`
	if err2 := r.db.QueryRowContext(ctx, qFallback, abhaNumber).Scan(&enc); err2 != nil {
		return "", err2
	}
	return enc, nil
}
