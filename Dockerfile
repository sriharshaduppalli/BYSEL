FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .

# Pin is backend/ISM_PIN (tag bysel-ism-v2026.08.23). Submodule files are
# copied when the build context ran git submodule update --init.
# Clone the public tag if the checkout was missing.
ARG ISM_GIT_TAG=bysel-ism-v2026.08.23
RUN if [ ! -f /app/vendor/indian_stock_market/src/indian_stock_llm/__init__.py ]; then \
      git clone --depth 1 --branch "$ISM_GIT_TAG" \
        https://github.com/sriharshaduppalli/Indian_stock_market.git \
        /app/vendor/indian_stock_market; \
    fi

ENV PYTHONPATH=/app/vendor/indian_stock_market/src

# Remove the bundled SQLite DB so Cloud Run creates a fresh one with correct permissions
RUN rm -f bysel.db

EXPOSE 8080

# Shell form so $PORT is expanded by the shell (Cloud Run sets PORT=8080)
CMD uvicorn app:app --host 0.0.0.0 --port ${PORT:-8080} --timeout-keep-alive 75
