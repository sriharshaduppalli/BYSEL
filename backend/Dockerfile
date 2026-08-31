FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ARG ISM_GIT_TAG=bysel-ism-v2026.08.31
RUN if [ ! -f /app/vendor/indian_stock_market/src/indian_stock_llm/__init__.py ]; then \
      git clone --depth 1 --branch "$ISM_GIT_TAG" \
        https://github.com/sriharshaduppalli/Indian_stock_market.git \
        /app/vendor/indian_stock_market; \
    fi

ENV PYTHONPATH=/app/vendor/indian_stock_market/src

EXPOSE 8000

# Cloud Run sets PORT env var; default to 8000 for local/Render
CMD uvicorn app:app --host 0.0.0.0 --port ${PORT:-8000}
