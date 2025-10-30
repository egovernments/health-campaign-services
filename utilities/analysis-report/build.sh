#!/bin/bash

# Punjab Analysis System - Build Script
# Builds Docker image and loads it into minikube

set -e  # Exit on any error

echo "🏗️  Punjab Analysis System - Build Script"
echo "========================================"

# Configuration
IMAGE_NAME="punjab-analysis"
IMAGE_TAG="v3.0.0"
FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"

echo "📦 Building Docker image: ${FULL_IMAGE}"

# Build the Docker image
docker build -t ${FULL_IMAGE} .

if [ $? -eq 0 ]; then
    echo "✅ Docker image built successfully: ${FULL_IMAGE}"
else
    echo "❌ Docker build failed"
    exit 1
fi

# Docker image is ready for use
echo "✅ Docker image ready for local use"

echo ""
echo "🎉 Build completed!"
echo "   Image: ${FULL_IMAGE}"
echo "   Size: $(docker images ${FULL_IMAGE} --format 'table {{.Size}}' | tail -n +2)"
echo ""
echo "📝 Next steps:"
echo "   1. Test locally: docker run --rm -e MODE=extract ${FULL_IMAGE}"
echo "   2. Start Airflow: docker-compose up -d"
echo "   3. Access Airflow UI: http://localhost:8082 (admin/admin123)"