package postgres

import (
	"context"
	"database/sql"
	"digit-abdm/internal/core/domain"
	"digit-abdm/internal/core/ports"
	"errors"
)

type AbhaRepositoryImpl struct {
	db *sql.DB
}

func NewAbhaRepository(db *sql.DB) ports.AbhaRepository {
	return &AbhaRepositoryImpl{db: db}
}

func (r *AbhaRepositoryImpl) SaveAbhaProfile(ctx context.Context, abha *domain.AbhaNumber) error {
	stmt := `
	  INSERT INTO abha_number (
		external_id, deleted, abha_number, health_id, email, first_name, middle_name, last_name, profile_photo,
		access_token, refresh_token, address, date_of_birth, district, gender, name, pincode, state, mobile,
		created_by, last_modified_by, created_date, modified_date, new
	  )
	  VALUES (
		$1, $2, $3, $4, $5, $6, $7, $8, $9,
		$10, $11, $12, $13, $14, $15, $16, $17, $18, $19,
		$20, $21, $22, $23, $24
	  )
	  ON CONFLICT (external_id) DO NOTHING
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
