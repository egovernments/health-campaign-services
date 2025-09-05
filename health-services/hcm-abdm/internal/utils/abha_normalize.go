package utils

import (
	"regexp"
	"strings"
)

// digits only (strip hyphens/spaces)
func NormalizeABHANumberDigits(s string) string {
	s = strings.ReplaceAll(s, "-", "")
	s = strings.ReplaceAll(s, " ", "")
	return s
}

// ABHA is 14 digits; UI often shows 2-4-4-4 groupings.
func ValidateABHANumber(s string) bool {
	d := NormalizeABHANumberDigits(s)
	return regexp.MustCompile(`^\d{14}$`).MatchString(d)
}

// Canonicalize to "XX-XXXX-XXXX-XXXX"
func CanonicalizeABHA(s string) (string, bool) {
	d := NormalizeABHANumberDigits(s)
	if len(d) != 14 {
		return "", false
	}
	// 2-4-4-4
	return d[0:2] + "-" + d[2:6] + "-" + d[6:10] + "-" + d[10:14], true
}
