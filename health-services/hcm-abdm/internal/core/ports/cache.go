package ports

import (
	"errors"
)

// ErrCacheMiss is returned when an item is not found in the cache
var ErrCacheMiss = errors.New("item not found in cache")

// MessageCache defines the caching operations for messages
//type MessageCache interface {
//	// SetMessages adds messages to the cache
//	SetMessages(ctx context.Context, tenantID, module, locale string, messages []domain.Message) error
//
//	// GetMessages retrieves messages from the cache
//	GetMessages(ctx context.Context, tenantID, module, locale string) ([]domain.Message, error)
//
//	// Invalidate removes a specific message set from the cache
//	Invalidate(ctx context.Context, tenantID, module, locale string) error
//
//	// BustCache clears the cache for a given tenant, and optionally module and locale
//	BustCache(ctx context.Context, tenantID, module, locale string) error
//}
