FROM python:3.11-slim@sha256:9c900dea9e8fb7e16277c179b555cc72d29a352dbc33cff48ad5a0412fd5bfc7
ARG WORK_DIR
WORKDIR /app
COPY ${WORK_DIR}/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ${WORK_DIR}/main.py .
COPY ${WORK_DIR}/migrate.py .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
