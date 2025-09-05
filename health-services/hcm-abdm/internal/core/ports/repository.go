package ports

import (
	"context"

	"digit-abdm/internal/core/domain"
)

type AbhaRepository interface {
	SaveAbhaProfile(ctx context.Context, abha *domain.AbhaNumber) error
	GetAbhaByNumber(ctx context.Context, abhaNumber string) (*domain.AbhaNumber, error)
	GetTokenByABHANumber(ctx context.Context, abhaNumber string) (*domain.AbhaNumber, error)
	InsertTransactionLog(ctx context.Context, log *domain.TransactionLog) error
	InsertLoginTransaction(ctx context.Context, log *domain.TransactionLog) error

	UpsertAadhaarTxnOnOtp(ctx context.Context, tenantId, txnId, aadharEnc, aadhaarHash, actor string) (string, error)
	UpdateTxnOnVerify(ctx context.Context, txnId, individualId, abhaNumber, actor string) error

	GetEncryptedAadhaarByTxn(ctx context.Context, txnId string) (string, error)
	GetEncryptedAadhaarByABHA(ctx context.Context, abhaNumber string) (string, error)
}
