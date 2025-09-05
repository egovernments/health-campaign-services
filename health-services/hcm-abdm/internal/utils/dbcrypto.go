package utils

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"io"
)

// NOTE: Per your request, we keep a constant key in the repo for now.
// In real deployments, read from env/secret manager and rotate periodically.
const dbCryptoKeyHex = "8d1e28f4c2a34b40b5a0c69d1f3e2b7490a8e7b2e6c3d9f4b1a2c3d4e5f6a7b8" // 32 bytes hex

func dbKey() ([]byte, error) {
	k, err := hex.DecodeString(dbCryptoKeyHex)
	if err != nil {
		return nil, err
	}
	if len(k) != 32 {
		return nil, errors.New("dbCryptoKeyHex must decode to 32 bytes")
	}
	return k, nil
}

// EncryptForDB encrypts plaintext using AES-256-GCM and returns base64(nonce||ciphertext).
func EncryptForDB(plaintext string) (string, error) {
	key, err := dbKey()
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}
	// Seal appends tag automatically; we prefix nonce for easy storage
	ciphertext := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
	return base64.StdEncoding.EncodeToString(ciphertext), nil
}

// DecryptFromDB reverses EncryptForDB.
func DecryptFromDB(cipherB64 string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(cipherB64)
	if err != nil {
		return "", err
	}
	key, err := dbKey()
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	if len(raw) < gcm.NonceSize() {
		return "", errors.New("ciphertext too short")
	}
	nonce, ct := raw[:gcm.NonceSize()], raw[gcm.NonceSize():]
	plain, err := gcm.Open(nil, nonce, ct, nil)
	if err != nil {
		return "", err
	}
	return string(plain), nil
}

// HashForLookup returns hex(SHA-256) for idempotent lookups (no decryption needed).
func HashForLookup(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}
