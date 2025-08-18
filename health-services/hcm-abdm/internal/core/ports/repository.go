package ports

import (
	"context"

	"digit-abdm/internal/core/domain"
)

type AbhaRepository interface {
	SaveAbhaProfile(ctx context.Context, abha *domain.AbhaNumber) error
	GetTokenByABHANumber(ctx context.Context, abhaNumber string) (*domain.AbhaNumber, error)
	InsertTransactionLog(ctx context.Context, log *domain.TransactionLog) error
}
