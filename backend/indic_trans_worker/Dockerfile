FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/models \
    TRANSFORMERS_CACHE=/models \
    HF_HUB_DISABLE_TELEMETRY=1 \
    INDIC_TRANS_MODEL=naklitechie/indictrans2-en-indic-dist-200M \
    INDIC_TRANS_DEVICE=cpu

RUN apt-get update && apt-get install -y --no-install-recommends \
        git \
        build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt

COPY app.py __init__.py __main__.py ./

# Prefetch weights only. Official AI4Bharat HF repo is gated; this MIT copy is public.
RUN python -c "import os; from huggingface_hub import snapshot_download; snapshot_download(os.environ['INDIC_TRANS_MODEL'])"

EXPOSE 8080
CMD uvicorn app:app --host 0.0.0.0 --port ${PORT:-8080}
