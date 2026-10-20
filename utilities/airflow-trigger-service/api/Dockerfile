FROM python:3.11-slim
ARG WORK_DIR
WORKDIR /app
COPY ${WORK_DIR}/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ${WORK_DIR}/main.py .
COPY ${WORK_DIR}/migrate.py .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]