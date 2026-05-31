FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .

# Remove the bundled SQLite DB so Cloud Run creates a fresh one with correct permissions
RUN rm -f bysel.db

EXPOSE 8080

# Shell form so $PORT is expanded by the shell (Cloud Run sets PORT=8080)
CMD uvicorn app:app --host 0.0.0.0 --port ${PORT:-8080} --timeout-keep-alive 75
