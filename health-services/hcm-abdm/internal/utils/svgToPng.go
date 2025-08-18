package utils

import (
	"encoding/base64"
	"fmt"
	"regexp"
)

// helper to extract and decode embedded base64 image from SVG
func ExtractImageFromSVG(svgData []byte) ([]byte, string, error) {
	re := regexp.MustCompile(`(?i)xlink:href="data:image\/(png|jpeg);base64,([^"]+)"`)
	matches := re.FindSubmatch(svgData)

	if len(matches) < 3 {
		return nil, "", fmt.Errorf("base64 image not found in SVG")
	}

	imageFormat := string(matches[1]) // "png" or "jpeg"
	base64Data := matches[2]

	decodedImage, err := base64.StdEncoding.DecodeString(string(base64Data))
	if err != nil {
		return nil, "", fmt.Errorf("failed to decode base64 image: %w", err)
	}

	contentType := "image/" + imageFormat
	return decodedImage, contentType, nil
}
