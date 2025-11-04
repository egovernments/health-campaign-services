# Punjab Analysis System - Dockerfile
# Multi-stage build for optimized production image

# Stage 1: Build stage
FROM python:3.11-slim as builder

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Create virtual environment
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Set working directory
WORKDIR /build

# Copy and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip
RUN pip install --no-cache-dir -r requirements.txt

# Stage 2: Production stage
FROM python:3.11-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Copy virtual environment from builder
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Working directory
WORKDIR /app

# Copy application code (all Python files in context)
COPY main.py .
COPY extract_data.py .
COPY analyze_data.py .
COPY cleanup_data.py .
COPY config_loader.py .
COPY process_all_tenants.py .
COPY filestore_uploader.py .
COPY upload_to_filestore.py .

# Create data/output dirs
RUN mkdir -p /data /output

# Create non-root user
RUN useradd --create-home --shell /bin/bash punjab-user
RUN chown -R punjab-user:punjab-user /app /data /output

USER punjab-user

# Environment vars
ENV MODE=extract
ENV TENANT_ID=pb.adampur
ENV OUTPUT_DIR=/data
ENV DATA_DIR=/data
ENV CHUNK_SIZE=50000
ENV CREATEDTIME_LIMIT=1757269799000
ENV CONFIG_PATH=/app/secrets/db_config.yaml
ENV ENVIRONMENT=development

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD python -c "import psycopg2; print('Health check passed')" || exit 1

ENTRYPOINT ["python", "main.py"]
