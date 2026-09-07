FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .

# Always pin ISM. GitHub/Cloud Build checkouts often omit the submodule, and
# the old tag fallback silently served English for Tenglish "ela undi" asks.
ARG ISM_GIT_COMMIT=c5bc671e1a29c4221445eb4943fe0a354c0d5118
RUN rm -rf /app/vendor/indian_stock_market \
    && git init /app/vendor/indian_stock_market \
    && git -C /app/vendor/indian_stock_market remote add origin \
         https://github.com/sriharshaduppalli/Indian_stock_market.git \
    && git -C /app/vendor/indian_stock_market fetch --depth 1 origin "$ISM_GIT_COMMIT" \
    && git -C /app/vendor/indian_stock_market checkout FETCH_HEAD

ENV PYTHONPATH=/app/vendor/indian_stock_market/src

# Remove the bundled SQLite DB so Cloud Run creates a fresh one with correct permissions
RUN rm -f bysel.db

EXPOSE 8080

# Shell form so $PORT is expanded by the shell (Cloud Run sets PORT=8080)
CMD uvicorn app:app --host 0.0.0.0 --port ${PORT:-8080} --timeout-keep-alive 75
