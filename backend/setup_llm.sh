#!/usr/bin/env bash
# Clones Indian_stock_market LLM branch at Render build time
set -e

LLM_DIR="$(dirname "$0")/llm"

if [ ! -d "$LLM_DIR/.git" ]; then
    echo "Cloning Indian_stock_market LLM..."
    git clone --branch copilot/create-llm-model-architecture --single-branch \
        https://github.com/sriharshaduppalli/Indian_stock_market "$LLM_DIR"
else
    echo "LLM present, pulling latest..."
    git -C "$LLM_DIR" pull
fi

echo "LLM setup complete."
